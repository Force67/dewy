package dev.dewy.tasks

import dev.dewy.net.Task
import dev.dewy.net.TaskState
import kotlinx.serialization.Serializable

/** A view onto the tasks, as a mail app has mailboxes. */
@Serializable
sealed interface Folder {
    @Serializable data object All : Folder
    @Serializable data object NeedsYou : Folder
    @Serializable data object Running : Folder
    @Serializable data object Archive : Folder
    @Serializable data class InProject(val path: String, val name: String) : Folder
    @Serializable data class OnBox(val name: String) : Folder

    val title: String
        get() = when (this) {
            All -> "All Tasks"
            NeedsYou -> "Needs You"
            Running -> "Running"
            Archive -> "Archive"
            is InProject -> name
            is OnBox -> name
        }

    fun holds(task: Task): Boolean = when (this) {
        All -> !task.archived
        NeedsYou -> !task.archived && task.state.needsYou
        Running -> !task.archived && task.state.isLive
        Archive -> task.archived
        is InProject -> !task.archived && task.project == path
        is OnBox -> !task.archived && task.box == name
    }
}

fun List<Task>.inFolder(folder: Folder): List<Task> = filter(folder::holds).sortedByDescending { it.updated }

data class ProjectSummary(val path: String, val name: String, val open: Int, val live: Int)

/** The projects that have tasks, busiest first. */
fun List<Task>.projects(): List<ProjectSummary> = filter { !it.archived }
    .groupBy { it.project }
    .map { (path, tasks) ->
        ProjectSummary(path, tasks.first().projectName, tasks.size, tasks.count { it.state.isLive })
    }
    .sortedWith(compareByDescending<ProjectSummary> { it.live }.thenByDescending { it.open }.thenBy { it.name })

fun Task.isUnread(seen: Map<String, Long>): Boolean =
    state != TaskState.Working && updated > (seen[id] ?: created)
