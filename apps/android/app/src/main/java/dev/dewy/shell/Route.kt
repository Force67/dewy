package dev.dewy.shell

import androidx.navigation3.runtime.NavKey
import dev.dewy.tasks.Folder
import kotlinx.serialization.Serializable

/**
 * Where the user is. One back stack serves every width: a phone shows its
 * top entry, a wide screen shows the folders, the latest task list, and the
 * top detail entry side by side.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Folders : Route
    @Serializable data class Tasks(val folder: Folder) : Route
    @Serializable data class Thread(val taskId: String) : Route
    @Serializable data class BoxInfo(val name: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object Usage : Route

    val isDetail get() = this is Thread || this is BoxInfo || this is Settings || this is Usage
}
