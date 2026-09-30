package dev.dewy.tasks

import dev.dewy.net.Box
import dev.dewy.net.DewyClient
import dev.dewy.net.Entry
import dev.dewy.net.ServerEvent
import dev.dewy.net.Task
import dev.dewy.settings.Connection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * holds it, the visible UI or the watch service.
 */
class TaskStore(connection: Flow<Connection?>, private val scope: CoroutineScope) {
    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    private val threads = MutableStateFlow<Map<String, List<Entry>>>(emptyMap())
    private val holders = MutableStateFlow(0)
    private val client = MutableStateFlow<DewyClient?>(null)

    init {
        scope.launch {
            combine(connection, holders.map { it > 0 }.distinctUntilChanged()) { c, held -> c to held }
                .collectLatest { (c, held) ->
                    val next = c?.let { DewyClient(it.serverUrl, it.token) }
                    client.value = next
                    when {
                        next == null -> _board.value = Board(link = Link.NotSetUp)
                        held -> follow(next)
                    }
                }
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

    suspend fun loadThread(id: String) {
        val loaded = requireClient().thread(id).entries
        threads.update { it + (id to loaded) }
    }

    suspend fun startTask(box: String, project: String, agent: String, text: String, yolo: Boolean): Task {
        val task = requireClient().startTask(box, project, agent, text, yolo)
        upsert(task)
        return task
    }

    suspend fun attachSession(box: String, session: String, yolo: Boolean): Task {
        val task = requireClient().attachSession(box, session, yolo)
        upsert(task)
        return task
    }

    suspend fun steer(id: String, text: String) = requireClient().steer(id, text)

    suspend fun stop(id: String) = requireClient().stop(id)

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
                threads.value.keys.forEach { id -> scope.launch { runCatching { loadThread(id) } } }
                pause = 1_000L
                // The stream ends on a reset, when the server's feed no longer
                // reaches back to our position; then the state loads again.
                c.events(state.seq).takeWhile { it != ServerEvent.Reset }.collect { apply(it) }
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

    private fun apply(event: ServerEvent) {
        when (event) {
            is ServerEvent.TaskChanged -> upsert(event.task)
            is ServerEvent.BoxChanged -> _board.update { b ->
                val others = b.boxes.filter { it.name != event.box.name }
                b.copy(boxes = (others + event.box).sortedBy { it.name })
            }
            is ServerEvent.EntryAdded -> threads.update { all ->
                val list = all[event.taskId] ?: return@update all
                all + (event.taskId to list.withEntry(event.entry))
            }
            ServerEvent.Reset -> Unit
        }
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
