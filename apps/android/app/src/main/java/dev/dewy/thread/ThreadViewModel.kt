package dev.dewy.thread

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import dev.dewy.images.MaxImages
import dev.dewy.images.discardStaged
import dev.dewy.images.uploadImages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import dev.dewy.net.Entry
import dev.dewy.net.Task
import dev.dewy.tasks.ReadMarks
import dev.dewy.tasks.TaskStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThreadViewModel(
    private val taskId: String,
    private val store: TaskStore,
    readMarks: ReadMarks,
    private val context: Context,
) : ViewModel() {
    val task: StateFlow<Task?> = store.board.map { b -> b.tasks.find { it.id == taskId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.board.value.tasks.find { it.id == taskId })

    /** The models the task's agent offers on its box; empty when it lists none. */
    val models: StateFlow<List<String>> = store.board.map { b ->
        b.tasks.find { it.id == taskId }?.let { t -> b.boxes.find { it.name == t.box }?.models?.get(t.agent) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entries: StateFlow<List<Entry>?> = store.thread(taskId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Images picked for the next message. */
    val attachments = MutableStateFlow<List<Uri>>(emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _problems = Channel<String>(Channel.BUFFERED)
    val problems = _problems.receiveAsFlow()

    init {
        load()
        // Looking at a task reads everything it said so far.
        viewModelScope.launch {
            task.filterNotNull().map { it.updated }.distinctUntilChanged().collect { readMarks.markSeen(taskId, it) }
        }
    }

    fun load() = attempt { store.loadThread(taskId) }

    fun send(text: String) = attempt { store.steer(taskId, text) }

    fun attach(uris: List<Uri>) = attachments.update { (it + uris).distinct().take(MaxImages) }

    fun detach(uri: Uri) {
        attachments.update { it - uri }
        viewModelScope.launch { discardStaged(listOf(uri)) }
    }

    private val _sent = Channel<Unit>(Channel.BUFFERED)

    /** A draft went; the screen clears it, also when it was rebuilt meanwhile. */
    val sent = _sent.receiveAsFlow()

    /** Uploads the attachments, then sends; a failure keeps the draft and the images. */
    fun sendDraft(text: String) = attempt {
        _sending.value = true
        try {
            val images = attachments.value
            val ids = uploadImages(context, store, images)
            store.steer(taskId, text, ids)
            // Images picked while this was sending wait for the next message.
            attachments.update { it - images.toSet() }
            discardStaged(images)
            _sent.trySend(Unit)
        } finally {
            _sending.value = false
        }
    }

    fun stop() = attempt { store.stop(taskId) }

    fun archive(archived: Boolean) = attempt { store.archive(taskId, archived) }

    fun setModel(model: String) = attempt { store.setModel(taskId, model) }

    private fun attempt(action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (e: Exception) {
            _problems.trySend(e.message ?: "Something went wrong")
        }
    }
}
