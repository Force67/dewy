package dev.dewy.boxes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.dewy.net.Box as DewyBox
import dev.dewy.net.PowerOff
import dev.dewy.net.Task
import dev.dewy.net.TaskState
import dev.dewy.ui.AgentMark
import dev.dewy.ui.CardShape
import dev.dewy.ui.Group
import dev.dewy.ui.GroupRow
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.StateDot
import dev.dewy.ui.Type
import dev.dewy.ui.agentLabel
import dev.dewy.ui.rememberNow
import dev.dewy.ui.shortAge
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScreen(
    box: DewyBox?,
    tasks: List<Task>,
    onBack: (() -> Unit)?,
    onOpenTask: (Task) -> Unit,
    onStartIn: (box: String, project: String) -> Unit,
    onAttach: (box: String, session: String) -> Unit,
    onArmPowerOff: (box: String, armed: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    Scaffold(
        containerColor = p.ground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(box?.name ?: "", style = Type.headline) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.ground, navigationIconContentColor = p.dew),
            )
        },
    ) { padding ->
        if (box == null) return@Scaffold
        val now = rememberNow()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(if (box.online) p.dewWash else p.hairline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Computer, null, tint = if (box.online) p.dew else p.inkFaint, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(box.name, style = Type.title, color = p.ink)
                    Text(
                        if (box.online) "${box.host} · ${box.os}" else "Offline · last seen ${shortAge(box.lastSeen, now)} ago",
                        style = Type.footnote,
                        color = p.inkSoft,
                    )
                }
            }
            if (box.online) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val s = box.stats
                        val load = if (s.cpus > 0) (s.load / s.cpus).toFloat() else 0f
                        Meter("Load", "%.2f".format(s.load), "${s.cpus} cores", load, Modifier.weight(1f))
                        val mem = if (s.memTotal > 0) (s.memUsed / s.memTotal).toFloat() else 0f
                        Meter("Memory", "${(mem * 100).roundToInt()}%", "of ${gigabytes(s.memTotal)}", mem, Modifier.weight(1f))
                        Meter("Up", uptime(s.uptime), "", -1f, Modifier.weight(1f))
                    }
                }
            }
            item {
                Group("Agents") {
                    if (box.agents.isEmpty()) {
                        GroupRow("None found on PATH", chevron = false)
                    }
                    box.agents.forEachIndexed { i, agent ->
                        if (i > 0) Hairline(56.dp)
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AgentMark(agent, size = 28.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(agentLabel(agent), style = Type.body, color = p.ink)
                        }
                    }
                }
            }
            if (box.online && box.poweroff.can) {
                item {
                    val running = tasks.count { it.box == box.name && it.state.isLive }
                    val sessions = box.sessions.count { it.state == TaskState.Working }
                    Group("Power") {
                        Row(
                            Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (box.poweroff.armed) p.coral else p.inkSoft, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Turn off when tasks finish", style = Type.body, color = p.ink)
                                Text(
                                    powerOffNote(box.poweroff, running, sessions, rememberNow(1_000)),
                                    style = Type.footnote,
                                    color = if (box.poweroff.at > 0 || box.poweroff.error.isNotEmpty()) p.coral else p.inkSoft,
                                )
                            }
                            Switch(
                                checked = box.poweroff.armed,
                                onCheckedChange = { onArmPowerOff(box.name, it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = p.coral),
                            )
                        }
                    }
                }
            }
            val here = tasks.filter { it.box == box.name && !it.archived }.sortedByDescending { it.updated }
            if (here.isNotEmpty()) {
                item {
                    Group("Tasks") {
                        here.take(8).forEachIndexed { i, t ->
                            if (i > 0) Hairline(52.dp)
                            GroupRow(t.title, subtitle = t.projectName, onClick = { onOpenTask(t) }) {
                                StateDot(t.state, Modifier.padding(end = 6.dp))
                            }
                        }
                    }
                }
            }
            if (box.sessions.isNotEmpty()) {
                item {
                    Group("Terminal sessions") {
                        box.sessions.forEachIndexed { i, s ->
                            if (i > 0) Hairline(52.dp)
                            val followed = tasks.find { it.box == box.name && it.session == s.id }
                            GroupRow(
                                s.name.ifBlank { s.projectName },
                                icon = Icons.Rounded.Terminal,
                                iconTint = p.inkSoft,
                                subtitle = if (followed != null) "${s.projectName} · following" else s.projectName,
                                onClick = { onAttach(box.name, s.id) },
                            ) {
                                StateDot(s.state, Modifier.padding(end = 6.dp))
                            }
                        }
                    }
                }
            }
            if (box.projects.isNotEmpty()) {
                item {
                    Group("Projects") {
                        box.projects.forEachIndexed { i, project ->
                            if (i > 0) Hairline(52.dp)
                            GroupRow(
                                project.name,
                                icon = Icons.Rounded.Folder,
                                iconTint = p.inkSoft,
                                chevron = false,
                                onClick = if (box.online) ({ onStartIn(box.name, project.path) }) else null,
                            ) {
                                if (box.online) Icon(Icons.Rounded.Add, "Start a task here", tint = p.dew, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Meter(label: String, value: String, note: String, fraction: Float, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(CardShape).background(p.surface).padding(12.dp)) {
        Text(label.uppercase(), style = Type.sectionLabel, color = p.inkSoft)
        Spacer(Modifier.height(4.dp))
        Text(value, style = Type.title, color = p.ink)
        if (note.isNotEmpty()) Text(note, style = Type.caption, color = p.inkFaint)
        if (fraction >= 0f) {
            Spacer(Modifier.height(8.dp))
            val color = when {
                fraction > 0.9f -> p.red
                fraction > 0.7f -> p.amber
                else -> p.dew
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.hairline)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color))
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** What the switch will do, or is doing: "Turning off in 42 s". */
fun powerOffNote(power: PowerOff, running: Int, sessions: Int, now: Long): String {
    val waiting = listOfNotNull(
        running.takeIf { it > 0 }?.let { if (it == 1) "1 task" else "$it tasks" },
        sessions.takeIf { it > 0 }?.let { if (it == 1) "1 terminal session" else "$it terminal sessions" },
    )
    return when {
        power.armed && power.at > 0 -> "Turning off in ${((power.at - now + 999) / 1000).coerceAtLeast(0)} s"
        power.armed && waiting.isNotEmpty() -> "Waiting for ${waiting.joinToString(" and ")} to finish"
        power.armed -> "Once nothing runs here"
        power.error.isNotEmpty() -> power.error
        else -> "Shuts the machine down a minute after its last task ends"
    }
}

private fun gigabytes(bytes: Double) = "%.0f GB".format(bytes / (1 shl 30))

private fun uptime(seconds: Double): String {
    val s = seconds.toLong()
    return when {
        s >= 86_400 -> "${s / 86_400}d"
        s >= 3600 -> "${s / 3600}h"
        else -> "${s / 60}m"
    }
}
