package dev.dewy.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.dewy.net.TaskState
import dev.dewy.tasks.Board
import dev.dewy.tasks.Folder
import dev.dewy.tasks.Link
import dev.dewy.tasks.inFolder
import dev.dewy.tasks.projects
import dev.dewy.ui.CountBadge
import dev.dewy.ui.Group
import dev.dewy.ui.GroupRow
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.StateDot
import dev.dewy.ui.Type

/** The mailboxes: smart folders, projects with tasks, and the boxes. */
@Composable
fun FoldersScreen(
    board: Board,
    selected: Folder?,
    onFolder: (Folder) -> Unit,
    onBox: (String) -> Unit,
    onSettings: () -> Unit,
    onUsage: () -> Unit,
    onCompose: () -> Unit,
) {
    val p = LocalPalette.current
    val tasks = board.tasks
    val projects = tasks.projects()
    LazyColumn(Modifier.fillMaxSize().background(p.ground)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dewy", style = Type.largeTitle, color = p.ink)
                    LinkLine(board)
                }
                IconButton(onClick = onCompose) {
                    Icon(Icons.Rounded.Edit, "New task", tint = p.dew)
                }
            }
        }
        item {
            Group(null, Modifier.padding(top = 18.dp)) {
                FolderRow(Folder.All, Icons.Rounded.Inbox, tasks.inFolder(Folder.All).size, selected, onFolder)
                Hairline(52.dp)
                FolderRow(
                    Folder.NeedsYou,
                    Icons.Rounded.NotificationImportant,
                    tasks.inFolder(Folder.NeedsYou).size,
                    selected,
                    onFolder,
                    tint = p.amber,
                    highlight = true,
                )
                Hairline(52.dp)
                FolderRow(Folder.Running, Icons.Rounded.Bolt, tasks.inFolder(Folder.Running).size, selected, onFolder)
                Hairline(52.dp)
                FolderRow(Folder.Archive, Icons.Rounded.Archive, 0, selected, onFolder, tint = p.inkSoft)
            }
        }
        if (projects.isNotEmpty()) {
            item {
                Group("Projects") {
                    projects.forEachIndexed { i, project ->
                        if (i > 0) Hairline(52.dp)
                        val folder = Folder.InProject(project.path, project.name)
                        FolderRow(folder, Icons.Rounded.Folder, project.open, selected, onFolder) {
                            if (project.live > 0) StateDot(TaskState.Working, Modifier.padding(end = 6.dp))
                        }
                    }
                }
            }
        }
        if (board.boxes.isNotEmpty()) {
            item {
                Group("Boxes") {
                    board.boxes.forEachIndexed { i, box ->
                        if (i > 0) Hairline(52.dp)
                        val working = tasks.count { it.box == box.name && it.state.isLive && !it.archived }
                        GroupRow(
                            box.name,
                            icon = Icons.Rounded.Computer,
                            iconTint = if (box.online) p.dew else p.inkFaint,
                            subtitle = when {
                                !box.online -> "Offline"
                                box.poweroff.armed -> "Turns off when done"
                                working > 0 -> "$working working"
                                else -> "Online"
                            },
                            onClick = { onBox(box.name) },
                        ) {
                            Box(
                                Modifier
                                    .padding(end = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (box.online) p.dew else p.inkFaint.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
            }
        }
        item {
            Group(null, Modifier.padding(top = 22.dp)) {
                GroupRow("Usage", icon = Icons.Rounded.DataUsage, iconTint = p.inkSoft, onClick = onUsage)
                Hairline(52.dp)
                GroupRow("Settings", icon = Icons.Outlined.Settings, iconTint = p.inkSoft, onClick = onSettings)
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun LinkLine(board: Board) {
    val p = LocalPalette.current
    val (text, color) = when (board.link) {
        Link.Live -> {
            val live = board.tasks.count { it.state.isLive && !it.archived }
            (if (live > 0) "$live agents at work" else "All quiet") to p.inkSoft
        }
        Link.Connecting -> "Connecting…" to p.inkSoft
        Link.Retrying -> "Reconnecting…" to p.coral
        Link.NotSetUp -> "Not connected" to p.inkSoft
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (board.link == Link.Live) StateDot(if (board.tasks.any { it.state == TaskState.Working }) TaskState.Working else TaskState.Stopped, size = 7.dp)
        Text(text, style = Type.footnote, color = color)
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    selected: Folder?,
    onFolder: (Folder) -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
    highlight: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    val isSelected = selected == folder
    GroupRow(
        folder.title,
        modifier = if (isSelected) Modifier.background(p.dewWash) else Modifier,
        icon = icon,
        iconTint = tint,
        onClick = { onFolder(folder) },
        chevron = selected == null,
    ) {
        extra()
        CountBadge(count, highlighted = highlight)
    }
}
