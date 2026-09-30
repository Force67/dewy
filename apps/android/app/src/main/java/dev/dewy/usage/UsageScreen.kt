package dev.dewy.usage

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dewy.net.Box as DewyBox
import dev.dewy.net.Meter
import dev.dewy.settings.ConnectionSettings
import dev.dewy.ui.AgentMark
import dev.dewy.ui.CardShape
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import dev.dewy.ui.agentLabel
import dev.dewy.ui.rememberNow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Every box's meters: how close each agent's provider is to its limits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(boxes: List<DewyBox>, settings: ConnectionSettings, onBack: (() -> Unit)?) {
    val p = LocalPalette.current
    val budgets by settings.budgets.collectAsStateWithLifecycle(emptyMap())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    val measured = boxes.filter { it.usage.isNotEmpty() }
    Scaffold(
        containerColor = p.ground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Usage", style = Type.headline) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.ground, navigationIconContentColor = p.dew),
            )
        },
    ) { padding ->
        val now = rememberNow()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (measured.isEmpty()) {
                item {
                    Text(
                        "No box has reported usage yet. Codex and opencode report within two minutes of a worker starting; Claude with its next turn.",
                        style = Type.callout,
                        color = p.inkSoft,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            measured.forEach { box ->
                if (measured.size > 1) {
                    item(key = "box:${box.name}") {
                        Text(
                            box.name.uppercase(),
                            style = Type.sectionLabel,
                            color = p.inkSoft,
                            modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                }
                items(box.usage, key = { "${box.name}:${it.agent}:${it.provider}" }) { m ->
                    MeterCard(
                        m,
                        budgets[m.provider].orEmpty(),
                        now,
                        onBudget = { editing = m.provider }.takeIf { m.agent == "opencode" && m.spend.isNotEmpty() },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    val provider = editing
    if (provider != null) {
        val windows = boxes.flatMap { it.usage }
            .firstOrNull { it.agent == "opencode" && it.provider == provider && it.spend.isNotEmpty() }
            ?.spend?.map { it.name } ?: SpendWindows
        BudgetDialog(
            provider = provider,
            windows = windows,
            caps = budgets[provider].orEmpty(),
            onSave = { caps ->
                scope.launch { settings.setBudget(provider, caps) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MeterCard(m: Meter, caps: Map<String, Double>, now: Long, onBudget: (() -> Unit)?) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(CardShape).background(p.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentMark(m.agent, size = 30.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(meterTitle(m), style = Type.body, color = p.ink)
                val age = if (m.at > 0) "as of ${ago(now - m.at)}" else ""
                Text(listOf(m.plan, age).filter { it.isNotEmpty() }.joinToString(" · "), style = Type.caption, color = p.inkFaint)
            }
            if (onBudget != null) {
                TextButton(onClick = onBudget) { Text(if (caps.isEmpty()) "Set budget" else "Budget", color = p.dew) }
            }
        }
        m.limits.forEach { l ->
            Spacer(Modifier.height(12.dp))
            Bar(
                label = l.name,
                value = "${(l.used * 100).roundToInt()}%",
                note = if (l.resets > now) "resets in ${until(l.resets - now)}" else "",
                fraction = l.used.toFloat(),
            )
        }
        m.spend.forEach { s ->
            Spacer(Modifier.height(12.dp))
            val cap = caps[s.name]
            Bar(
                label = s.name,
                value = if (cap != null) "${dollars(s.cost)} of ${dollars(cap)}" else dollars(s.cost),
                note = "${compactTokens(s.tokens)} tokens",
                fraction = if (cap != null && cap > 0) (s.cost / cap).toFloat() else -1f,
            )
        }
    }
}

/** A labelled value; with a [fraction] of 0 or more, a bar that warns as it fills. */
@Composable
private fun Bar(label: String, value: String, note: String, fraction: Float) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = Type.subhead, color = p.inkSoft, modifier = Modifier.weight(1f))
        Text(value, style = Type.subhead, color = p.ink)
    }
    if (fraction >= 0f) {
        Spacer(Modifier.height(6.dp))
        val color = when {
            fraction > 0.9f -> p.red
            fraction > 0.7f -> p.amber
            else -> p.dew
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.hairline)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
        }
    }
    if (note.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(note, style = Type.caption, color = p.inkFaint)
    }
}

@Composable
private fun BudgetDialog(
    provider: String,
    windows: List<String>,
    caps: Map<String, Double>,
    onSave: (Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    var drafts by remember { mutableStateOf(windows.associateWith { w -> caps[w]?.let { "%.2f".format(it) } ?: "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for ${providerLabel(provider)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dollars per window; leave empty for no cap.", style = Type.footnote)
                windows.forEach { w ->
                    OutlinedTextField(
                        value = drafts[w].orEmpty(),
                        onValueChange = { v -> drafts = drafts + (w to v) },
                        label = { Text(w) },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parseCaps(drafts)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The caps typed in, without the empty or unreadable ones. */
fun parseCaps(drafts: Map<String, String>): Map<String, Double> =
    drafts.mapNotNull { (w, v) -> v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { w to it } }.toMap()

fun meterTitle(m: Meter) = if (m.agent == "opencode") "opencode · ${providerLabel(m.provider)}" else agentLabel(m.agent)

fun providerLabel(provider: String) = when (provider) {
    "opencode" -> "Zen"
    "opencode-go" -> "Go"
    "zai-coding-plan" -> "Z.ai Coding Plan"
    "github-copilot" -> "GitHub Copilot"
    else -> provider
}

fun dollars(d: Double) = if (d < 10) "$%.2f".format(d) else "$%.0f".format(d)

/** The windows opencode spend is summed over, as the worker names them. */
private val SpendWindows = listOf("5 hours", "7 days", "30 days")

/** 23537 reads as "23.5K", 101995173 as "102M", 2000000 as "2M". */
fun compactTokens(n: Double): String {
    val units = listOf(1e9 to "B", 1e6 to "M", 1e3 to "K")
    for ((size, unit) in units) {
        // 999,960 rounds to "1000K"; it reads better as "1M".
        if (n >= size * 0.99995) {
            val v = n / size
            val text = if (v >= 99.95) "%.0f".format(v) else "%.1f".format(v)
            return text.removeSuffix(".0").removeSuffix(",0") + unit
        }
    }
    return "%.0f".format(n)
}

/** "just now", "4 min ago", "3 h ago", "2 days ago". */
fun ago(millis: Long): String {
    val m = millis / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}

/** "12 min", "2 h 14 min", "6 days 5 h". */
fun until(millis: Long): String {
    val m = (millis + 59_999) / 60_000
    return when {
        m < 60 -> "$m min"
        m < 48 * 60 -> "${m / 60} h ${m % 60} min"
        else -> "${m / (24 * 60)} days ${(m % (24 * 60)) / 60} h"
    }
}
