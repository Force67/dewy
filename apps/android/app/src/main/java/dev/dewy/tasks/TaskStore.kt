package dev.dewy.tasks

import dev.dewy.net.Box
import dev.dewy.net.DewyClient
import dev.dewy.net.Entry
import dev.dewy.net.ServerEvent
import dev.dewy.net.StreamEvent
import dev.dewy.net.Task
import dev.dewy.settings.Connection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class Link { NotSetUp, Connecting, Live, Retrying }

data class Board(
    val boxes: List<Box> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val link: Link = Link.Connecting,
    val problem: String? = null,
)

/**
 * The app's copy of the orchestrator's board: loads it, then follows the
 * event stream and reconnects when it drops. It syncs only while something
 * holds it, the visible UI or the watch service. [cache] keeps the board,
 * the loaded threads, and the stream position across app starts.
 */
@OptIn(FlowPreview::class)
class TaskStore(connection: Flow<Connection?>, private val cache: DewyCache, private val scope: CoroutineScope) {
    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    private val threads = MutableStateFlow<Map<String, List<Entry>>>(emptyMap())
    private val holders = MutableStateFlow(0)
    private val client = MutableStateFlow<DewyClient?>(null)

    // The cache writes one change at a time, in the order they happened.
    private val disk = Dispatchers.IO.limitedParallelism(1)

    // Threads in memory that missed changes, to download when opened.
    private val stale = mutableSetOf<String>()

    // Entries streamed while their thread downloads, with their feed
    // position, to lay over the downloaded snapshot.
    private val arriving = mutableMapOf<String, MutableList<Pair<Long, Entry>>>()

    init {
        scope.launch {
            combine(connection, holders.map { it > 0 }.distinctUntilChanged()) { c, held -> c to held }
                .collectLatest { (c, held) ->
                    val next = c?.let { DewyClient(it.serverUrl, it.token) }
                    client.value = next
                    when {
                        next == null -> {
                            _board.value = Board(link = Link.NotSetUp)
                            threads.value = emptyMap()
                            withContext(disk) { cache.clear() }
                        }
                        held -> follow(next)
                    }
                }
        }
        // The last known board shows until the orchestrator answers, with
        // its boxes offline, since only the orchestrator knows.
        scope.launch {
            val boxes = withContext(disk) { cache.boxes() }?.map { it.copy(online = false) }
            val tasks = withContext(disk) { cache.tasks() }
            if (boxes != null && tasks != null) {
                _board.update { b ->
                    val waiting = b.link == Link.Connecting || b.link == Link.Retrying
                    if (waiting && b.tasks.isEmpty()) b.copy(boxes = boxes, tasks = tasks) else b
                }
            }
        }
        scope.launch {
            board.filter { it.link == Link.Live }
                .map { it.boxes to it.tasks }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { (boxes, tasks) -> withContext(disk) { cache.saveBoard(boxes, tasks) } }
        }
    }

    /** Keeps the board live until the returned handle closes. */
    fun hold(): AutoCloseable {
        holders.update { it + 1 }
        var open = true
        return AutoCloseable {
            if (open) {
                open = false
                holders.update { it - 1 }
            }
        }
    }

    fun thread(id: String): Flow<List<Entry>?> = threads.map { it[id] }

    /**
     * Makes a thread current: the stream keeps one in memory up to date, the
     * cache shows one loaded before, and only a missing or stale one downloads.
     */
    suspend fun loadThread(id: String) {
        if (id in threads.value && id !in stale) return
        if (id !in threads.value) {
            val cached = withContext(disk) { cache.thread(id) }
            if (cached != null) {
                threads.update { if (id in it) it else it + (id to cached.entries) }
                if (!cached.stale) return
                stale += id
            }
        }
        download(id)
    }

    private suspend fun download(id: String) {
        if (id in arriving) return
        arriving[id] = mutableListOf()
        try {
            val c = requireClient()
            val loaded = c.thread(id)
            // A download for an orchestrator the app has since left is void.
            if (client.value !== c) return
            var entries = loaded.entries
            for ((seq, entry) in arriving[id].orEmpty()) {
                if (seq > loaded.seq) entries = entries.withEntry(entry)
            }
            threads.update { it + (id to entries) }
            stale -= id
            withContext(disk) { cache.saveThread(id, entries) }
        } finally {
            arriving -= id
        }
    }

    // The stream missed changes: cached threads download again when opened,
    // and those in memory now.
    private suspend fun staleAll() {
        withContext(disk) { cache.markAllStale() }
        stale += threads.value.keys
        threads.value.keys.forEach { id -> scope.launch { runCatching { download(id) } } }
    }

    suspend fun startTask(
        box: String,
        project: String,
        agent: String,
        model: String,
        text: String,
        images: List<String>,
        yolo: Boolean,
    ): Task {
        val task = requireClient().startTask(box, project, agent, model, text, images, yolo)
        upsert(task)
        return task
    }

    suspend fun attachSession(box: String, session: String, yolo: Boolean): Task {
        val task = requireClient().attachSession(box, session, yolo)
        upsert(task)
        return task
    }

    suspend fun steer(id: String, text: String, images: List<String> = emptyList()) = requireClient().steer(id, text, images)

    suspend fun uploadImage(bytes: ByteArray, type: String): String = requireClient().uploadImage(bytes, type)

    suspend fun imageBytes(id: String): ByteArray = requireClient().image(id)

    suspend fun stop(id: String) = requireClient().stop(id)

    suspend fun setModel(id: String, model: String) {
        requireClient().setModel(id, model)
        _board.update { b -> b.copy(tasks = b.tasks.map { if (it.id == id) it.copy(model = model) else it }) }
    }

    suspend fun archive(id: String, archived: Boolean) {
        _board.update { b -> b.copy(tasks = b.tasks.map { if (it.id == id) it.copy(archived = archived) else it }) }
        requireClient().archive(id, archived)
    }

    // A notification reply can start the process; the settings load first.
    private suspend fun requireClient() = withTimeout(5_000) { client.filterNotNull().first() }

    private suspend fun follow(c: DewyClient) {
        var pause = 1_000L
        while (true) {
            try {
                val state = c.state()
                _board.value = Board(state.boxes, state.tasks, Link.Live)
                pause = 1_000L
                // From where the app left off, the feed replays what the
                // cached threads missed; a position from another start of
                // the orchestrator means nothing now.
                val saved = withContext(disk) { cache.position() }
                val since = if (saved != null && saved.epoch == state.epoch && saved.seq <= state.seq) {
                    saved.seq
                } else {
                    staleAll()
                    withContext(disk) { cache.savePosition(StreamPosition(state.seq, state.epoch)) }
                    state.seq
                }
                // The stream ends on a reset, when the server's feed no longer
                // reaches back to our position; then the state loads again,
                // and the stream starts from it.
                c.events(since, state.epoch)
                    .takeWhile { it.event != ServerEvent.Reset }
                    .collect { apply(it, state.seq, state.epoch) }
                withContext(disk) { cache.forgetPosition() }
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _board.update { it.copy(link = Link.Retrying, problem = e.message) }
            }
            delay(pause)
            pause = (pause * 2).coerceAtMost(30_000L)
        }
    }

    // Tasks and boxes up to the state's position are in the state already;
    // replaying them would roll them back for a moment.
    private suspend fun apply(e: StreamEvent, stateSeq: Long, epoch: Long) {
        val replayed = e.seq != null && e.seq <= stateSeq
        when (val event = e.event) {
            is ServerEvent.TaskChanged -> if (!replayed) upsert(event.task)
            is ServerEvent.BoxChanged -> if (!replayed) _board.update { b ->
                val others = b.boxes.filter { it.name != event.box.name }
                b.copy(boxes = (others + event.box).sortedBy { it.name })
            }
            is ServerEvent.EntryAdded -> {
                arriving[event.taskId]?.add((e.seq ?: Long.MAX_VALUE) to event.entry)
                threads.update { all ->
                    val list = all[event.taskId] ?: return@update all
                    all + (event.taskId to list.withEntry(event.entry))
                }
                withContext(disk) { cache.putEntry(event.taskId, event.entry) }
            }
            ServerEvent.Reset -> Unit
        }
        e.seq?.let { seq -> withContext(disk) { cache.savePosition(StreamPosition(seq, epoch)) } }
    }

    private fun upsert(task: Task) = _board.update { b ->
        b.copy(tasks = b.tasks.filter { it.id != task.id } + task, problem = null)
    }
}

/** Adds an entry to a thread, or merges it into the one with the same id. */
fun List<Entry>.withEntry(entry: Entry): List<Entry> {
    val at = indexOfFirst { it.id == entry.id }
    if (at < 0) return this + entry
    return toMutableList().also { it[at] = it[at].mergedWith(entry) }
}
