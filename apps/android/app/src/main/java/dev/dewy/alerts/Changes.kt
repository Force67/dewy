package dev.dewy.alerts

import dev.dewy.net.Task
import dev.dewy.net.TaskState

enum class ChangeKind { Finished, Failed, Stalled }

/** A task turn that ended or got stuck: what the phone should tell the user. */
data class Change(val task: Task, val kind: ChangeKind)

fun changesBetween(before: List<Task>, after: List<Task>): List<Change> {
    val old = before.associateBy { it.id }
    return after.mapNotNull { task ->
        val was = old[task.id]?.state ?: return@mapNotNull null
        if (was == task.state) return@mapNotNull null
        when {
            was.isLive && task.state == TaskState.Idle -> Change(task, ChangeKind.Finished)
            was.isLive && task.state == TaskState.Failed -> Change(task, ChangeKind.Failed)
            was == TaskState.Working && task.state == TaskState.Stalled -> Change(task, ChangeKind.Stalled)
            else -> null
        }
    }
}
