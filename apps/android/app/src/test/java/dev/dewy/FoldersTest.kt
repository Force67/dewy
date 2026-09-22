package dev.dewy

import dev.dewy.alerts.ChangeKind
import dev.dewy.alerts.changesBetween
import dev.dewy.net.Task
import dev.dewy.net.TaskState
import dev.dewy.tasks.Folder
import dev.dewy.tasks.inFolder
import dev.dewy.tasks.isUnread
import dev.dewy.tasks.projects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {
    private fun task(id: String, state: TaskState, project: String = "/src/dewy", updated: Long = 10, archived: Boolean = false) =
        Task(id, "forge", project, "claude", "task $id", state = state, archived = archived, created = 1, updated = updated)

    private val tasks = listOf(
        task("1", TaskState.Working, updated = 30),
        task("2", TaskState.Idle, updated = 50),
        task("3", TaskState.Stalled, project = "/src/aki", updated = 40),
        task("4", TaskState.Idle, archived = true),
    )

    @Test
    fun foldersFilterAndSortNewestFirst() {
        assertEquals(listOf("2", "3", "1"), tasks.inFolder(Folder.All).map { it.id })
        assertEquals(listOf("2", "3"), tasks.inFolder(Folder.NeedsYou).map { it.id })
        assertEquals(listOf("3", "1"), tasks.inFolder(Folder.Running).map { it.id })
        assertEquals(listOf("4"), tasks.inFolder(Folder.Archive).map { it.id })
        assertEquals(listOf("3"), tasks.inFolder(Folder.InProject("/src/aki", "aki")).map { it.id })
    }

    @Test
    fun projectsPutLiveOnesFirst() {
        val p = tasks.projects()
        assertEquals(listOf("dewy", "aki"), p.map { it.name })
        assertEquals(2, p[0].open)
    }

    @Test
    fun unreadMeansNewsSinceLastLook() {
        val t = task("2", TaskState.Idle, updated = 50)
        assertTrue(t.isUnread(emptyMap()))
        assertFalse(t.isUnread(mapOf("2" to 50L)))
        assertFalse(task("1", TaskState.Working, updated = 99).isUnread(emptyMap()))
    }

    @Test
    fun turnsThatEndBecomeChanges() {
        val after = listOf(
            task("1", TaskState.Idle),
            task("2", TaskState.Idle),
            task("3", TaskState.Failed),
        )
        val before = listOf(task("1", TaskState.Working), task("2", TaskState.Idle), task("3", TaskState.Stalled))
        val changes = changesBetween(before, after)
        assertEquals(listOf("1" to ChangeKind.Finished, "3" to ChangeKind.Failed), changes.map { it.task.id to it.kind })
        assertEquals(ChangeKind.Stalled, changesBetween(listOf(task("1", TaskState.Working)), listOf(task("1", TaskState.Stalled))).single().kind)
    }
}
