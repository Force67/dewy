package dev.dewy.thread

import androidx.lifecycle.ViewModel
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
) : ViewModel() {
    val task: StateFlow<Task?> = store.board.map { b -> b.tasks.find { it.id == taskId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.board.value.tasks.find { it.id == taskId })

    val entries: StateFlow<List<Entry>?> = store.thread(taskId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun stop() = attempt { store.stop(taskId) }

    fun archive(archived: Boolean) = attempt { store.archive(taskId, archived) }

    private fun attempt(action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (e: Exception) {
            _problems.trySend(e.message ?: "Something went wrong")
        }
    }
}
