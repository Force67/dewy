package dev.dewy.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dewy.alerts.Alerts
import dev.dewy.models.ModelPicker
import dev.dewy.models.modelLabel
import dev.dewy.net.Task
import dev.dewy.net.TaskState
import dev.dewy.ui.AgentMark
import dev.dewy.ui.CardShape
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.StatePill
import dev.dewy.ui.Type
import dev.dewy.ui.agentLabel
import dev.dewy.ui.rememberNow
import dev.dewy.ui.span

private const val NUDGE = "Are you stuck? Give me a one-line status, then carry on."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(model: ThreadViewModel, alerts: Alerts, onBack: (() -> Unit)?) {
    val p = LocalPalette.current
    val task by model.task.collectAsStateWithLifecycle()
    val entries by model.entries.collectAsStateWithLifecycle()
    val attachments by model.attachments.collectAsStateWithLifecycle()
    val sending by model.sending.collectAsStateWithLifecycle()
    val models by model.models.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var draft by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(model) { model.problems.collect { snackbar.showSnackbar(it) } }
    // Your own message is followed even when the thread was not at its end:
    // the attachments above the composer can push the last entry out of view.
    var followNext by remember { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.sent.collect {
            draft = ""
            followNext = true
        }
    }
    LifecycleStartEffect(task?.id) {
        alerts.openThread.value = task?.id
        task?.id?.let(alerts::dismiss)
        onStopOrDispose { alerts.openThread.value = null }
    }

    val t = task
    Scaffold(
        containerColor = p.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t?.title ?: "", style = Type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (t != null) {
                            Text("${t.projectName} · ${t.box}", style = Type.caption, color = p.inkSoft, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", Modifier.size(20.dp)) }
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (t?.state?.isLive == true) {
                            DropdownMenuItem(text = { Text("Stop") }, onClick = { menu = false; model.stop() })
                        }
                        DropdownMenuItem(
                            text = { Text(if (t?.archived == true) "Move to tasks" else "Archive") },
                            onClick = {
                                menu = false
                                model.archive(t?.archived != true)
                                if (t?.archived != true) onBack?.invoke()
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = p.ground,
                    scrolledContainerColor = p.surface,
                    navigationIconContentColor = p.dew,
                    actionIconContentColor = p.dew,
                    titleContentColor = p.ink,
                ),
            )
        },
        bottomBar = {
            Column(Modifier.background(p.ground).navigationBarsPadding().imePadding()) {
                if (t != null) LiveBar(t, onNudge = { model.send(NUDGE) }, onStop = model::stop)
                Composer(
                    text = draft,
                    onText = { draft = it },
                    placeholder = when {
                        t == null -> "Message"
                        t.state == TaskState.Working -> "Queue a message…"
                        else -> "Message ${agentLabel(t.agent)}…"
                    },
                    canStop = t?.state?.isLive == true,
                    onSend = { model.sendDraft(draft.trim()) },
                    onStop = model::stop,
                    attachments = attachments,
                    onAttach = model::attach,
                    onRemove = model::detach,
                    sending = sending,
                )
            }
        },
    ) { padding ->
        val list = entries
        if (t == null || list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = p.dew, strokeWidth = 2.dp)
            }
            return@Scaffold
        }
        val items = remember(list) { timeline(list) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.size)
        // Follow new entries while the reader is at the bottom.
        LaunchedEffect(listState) {
            var lastCount = 0
            snapshotFlow { listState.layoutInfo.totalItemsCount }.collect { count ->
                val atBottom = !listState.canScrollForward || lastCount == 0 || followNext
                if (count > lastCount) followNext = false
                if (count > lastCount && atBottom) listState.animateScrollToItem((count - 1).coerceAtLeast(0))
                lastCount = count
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp),
        ) {
            item(key = "header") { Header(t, pickable = models.isNotEmpty(), onPickModel = { picking = true }) }
            items(items, key = { it.key }) { item ->
                when (item) {
                    is Item.Said -> SaidView(item.entry)
                    is Item.Steps -> StepsView(item)
                }
            }
            if (t.state == TaskState.Working && items.lastOrNull() !is Item.Steps) {
                item(key = "thinking") {
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) { ThinkingDots(p.inkFaint) }
                }
            }
        }
    }
    if (picking && t != null) {
        ModelPicker(
            models,
            t.model,
            onPick = {
                model.setModel(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Header(t: Task, pickable: Boolean, onPickModel: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AgentMark(t.agent, size = 52.dp)
        Spacer(Modifier.height(10.dp))
        Text(agentLabel(t.agent), style = Type.subhead, color = p.inkSoft)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatePill(t.state)
            if (t.yolo) {
                Text(
                    "Yolo",
                    style = Type.caption,
                    color = p.coral,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.coral.copy(alpha = 0.13f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            if (pickable || t.model.isNotEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(p.dewWash)
                        .clickable(enabled = pickable, onClick = onPickModel)
                        .padding(start = 9.dp, end = if (pickable) 5.dp else 9.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(modelLabel(t.model), style = Type.caption, color = p.dewDeep)
                    if (pickable) Icon(Icons.Rounded.ExpandMore, "Change the model", tint = p.dewDeep, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.project,
            style = Type.caption,
            color = p.inkFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
    Hairline(0.dp)
}

/** What the agent is doing now, or a warning when it went quiet or broke. */
@Composable
private fun LiveBar(t: Task, onNudge: () -> Unit, onStop: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(1_000)
    AnimatedVisibility(
        t.state == TaskState.Working || t.state == TaskState.Stalled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        if (t.state == TaskState.Stalled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(p.coral.copy(alpha = 0.12f), CardShape)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet for ${span(now - t.updated)}", style = Type.subhead, color = p.coral)
                    Text("It may be stuck or waiting on something.", style = Type.caption, color = p.inkSoft)
                }
                TextButton(onClick = onNudge) { Text("Nudge", color = p.coral) }
                TextButton(onClick = onStop) { Text("Stop", color = p.inkSoft) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThinkingDots(p.dew)
                Text(
                    t.activity.ifBlank { "Thinking" },
                    style = Type.footnote,
                    color = p.dew,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(span(now - t.updated), style = Type.caption, color = p.inkFaint)
            }
        }
    }
}
