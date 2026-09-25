package dev.dewy.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dewy.R
import dev.dewy.net.Task
import dev.dewy.net.TaskState
import dev.dewy.ui.AgentMark
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.StatePill
import dev.dewy.ui.Type
import dev.dewy.ui.rememberNow
import dev.dewy.ui.shortAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    folder: Folder,
    tasks: List<Task>,
    seen: Map<String, Long>,
    link: Link,
    selectedId: String?,
    onBack: (() -> Unit)?,
    onOpen: (Task) -> Unit,
    onArchive: (Task, Boolean) -> Unit,
    onCompose: () -> Unit,
) {
    val p = LocalPalette.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val now = rememberNow()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = p.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, Modifier.size(16.dp))
                            Text("Folders", style = Type.body)
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = p.surface,
                    scrolledContainerColor = p.surface,
                    titleContentColor = p.ink,
                    navigationIconContentColor = p.dew,
                ),
            )
        },
        bottomBar = { StatusBar(tasks, link, onCompose) },
    ) { padding ->
        if (tasks.isEmpty()) {
            EmptyFolder(folder, Modifier.padding(padding), onCompose)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(tasks, key = { it.id }) { task ->
                SwipeToArchive(task, onArchive) {
                    TaskRow(task, task.isUnread(seen), task.id == selectedId, now) { onOpen(task) }
                }
                Hairline(72.dp)
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, unread: Boolean, selected: Boolean, now: Long, onClick: () -> Unit) {
    val p = LocalPalette.current
    val background by animateColorAsState(if (selected) p.dewWash else p.surface, label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(Modifier.width(18.dp).padding(top = 14.dp), contentAlignment = Alignment.Center) {
            if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(p.dew))
        }
        AgentMark(task.agent, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = Type.headline,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(shortAge(task.updated, now), style = Type.footnote, color = if (unread) p.dew else p.inkFaint)
            }
            Text(
                "${task.projectName} · ${task.box}",
                style = Type.footnote,
                color = p.inkSoft,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            val line = when {
                task.state == TaskState.Working && task.activity.isNotBlank() -> task.activity
                task.preview.isNotBlank() -> task.preview
                task.state == TaskState.Working -> "Thinking…"
                else -> ""
            }
            if (line.isNotEmpty()) {
                Text(
                    line,
                    style = if (task.state == TaskState.Working) Type.footnote else Type.callout,
                    color = if (task.state == TaskState.Working) p.dew else p.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.state != TaskState.Idle || unread) {
                StatePill(task.state, Modifier.padding(top = 7.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToArchive(task: Task, onArchive: (Task, Boolean) -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onArchive(task, !task.archived) },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(if (task.archived) p.dew else p.amber).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (task.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null, tint = p.onDew)
                    Text(if (task.archived) "Restore" else "Archive", style = Type.caption, color = p.onDew)
                }
            }
        },
    ) { content() }
}

@Composable
private fun StatusBar(tasks: List<Task>, link: Link, onCompose: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.background(p.surface)) {
        Hairline(0.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 56.dp, end = 8.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val working = tasks.count { it.state == TaskState.Working }
            val text = when (link) {
                Link.Retrying -> "Reconnecting…"
                Link.Connecting -> "Connecting…"
                else -> when {
                    working > 0 -> "$working working"
                    tasks.isEmpty() -> ""
                    else -> "${tasks.size} tasks"
                }
            }
            Text(
                text,
                style = Type.caption,
                color = if (link == Link.Retrying) p.coral else p.inkSoft,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onCompose) { Icon(Icons.Rounded.Edit, "New task", tint = p.dew) }
        }
    }
}

@Composable
private fun EmptyFolder(folder: Folder, modifier: Modifier, onCompose: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxSize().padding(PaddingValues(horizontal = 40.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_drop), null, tint = p.dew.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            when (folder) {
                Folder.NeedsYou -> "Nothing needs you"
                Folder.Running -> "No agent is working"
                Folder.Archive -> "Archive is empty"
                else -> "No tasks yet"
            },
            style = Type.title,
            color = p.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (folder == Folder.NeedsYou) "Finished and stuck tasks land here." else "Hand an agent something to do.",
            style = Type.callout,
            color = p.inkSoft,
        )
        if (folder != Folder.Archive) {
            TextButton(onClick = onCompose, modifier = Modifier.padding(top = 12.dp)) {
                Text("Start a task", style = Type.headline, color = p.dew)
            }
        }
    }
}
