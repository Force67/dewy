package dev.dewy.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dewy.images.ImageStrip
import dev.dewy.net.Entry
import dev.dewy.net.ImageRef
import dev.dewy.net.EntryKind
import dev.dewy.ui.CardShape
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Markdown
import dev.dewy.ui.Type

private val BubbleShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)

@Composable
fun SaidView(entry: Entry) {
    when (entry.kind) {
        EntryKind.User -> UserBubble(entry.text, entry.images)
        EntryKind.Agent -> Markdown(entry.text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        EntryKind.Note -> Callout(entry.text, Icons.Rounded.Info, LocalPalette.current.amber)
        EntryKind.Error -> Callout(entry.text, Icons.Rounded.ErrorOutline, LocalPalette.current.red)
        EntryKind.Tool -> Unit
    }
}

@Composable
private fun UserBubble(text: String, images: List<ImageRef>) {
    val p = LocalPalette.current
    ImageStrip(images, 120.dp, Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 10.dp), alignEnd = true)
    if (text.isBlank()) return
    Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.CenterEnd) {
        SelectionContainer {
            Text(
                text,
                style = Type.body,
                color = p.onDew,
                modifier = Modifier
                    .clip(BubbleShape)
                    .background(Brush.linearGradient(listOf(p.dew, p.dewDeep)))
                    .padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Callout(text: String, icon: ImageVector, tint: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(CardShape)
            .background(tint.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        SelectionContainer { Text(text, style = Type.callout, color = LocalPalette.current.ink) }
    }
}

/** A run of tool calls: a one-line summary that opens into every step. */
@Composable
fun StepsView(steps: Item.Steps) {
    val p = LocalPalette.current
    var open by rememberSaveable(steps.key) { mutableStateOf(false) }
    val shown = if (open) steps.tools else steps.tools.takeLast(if (steps.running > 0) 2 else 1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(CardShape)
            .border(0.5.dp, p.hairline, CardShape)
            .background(p.surface)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                summarize(steps.tools),
                style = Type.subhead,
                color = p.inkSoft,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (steps.failed > 0) {
                Text("${steps.failed} failed", style = Type.caption, color = p.red, modifier = Modifier.padding(end = 6.dp))
            }
            Icon(
                Icons.Rounded.ExpandMore,
                if (open) "Fewer steps" else "All steps",
                tint = p.inkFaint,
                modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f),
            )
        }
        shown.forEach { ToolRow(it) }
    }
}

@Composable
private fun ToolRow(tool: Entry) {
    val p = LocalPalette.current
    var open by rememberSaveable(tool.id) { mutableStateOf(false) }
    val hasOutput = tool.output.isNotBlank()
    Column(Modifier.fillMaxWidth().background(p.ground.copy(alpha = if (p.isDark) 0.4f else 0.55f))) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = hasOutput) { open = !open }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(toolIcon(tool.tool), null, tint = p.inkSoft, modifier = Modifier.size(17.dp))
            Text(tool.tool, style = Type.caption, color = p.ink)
            Text(
                tool.detail,
                style = Type.mono,
                color = p.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ToolState(tool.toolState)
        }
        // What the agent looked at shows even with the output folded.
        ImageStrip(tool.images, 96.dp, Modifier.padding(start = 41.dp, end = 14.dp, bottom = 8.dp))
        AnimatedVisibility(open && hasOutput, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SelectionContainer {
                Text(
                    tool.output.trimEnd(),
                    style = Type.mono,
                    color = p.ink,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.code)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolState(state: String) {
    val p = LocalPalette.current
    when (state) {
        "running" -> CircularProgressIndicator(Modifier.size(14.dp), color = p.dew, strokeWidth = 1.8.dp)
        "error" -> Icon(Icons.Rounded.ErrorOutline, "Failed", tint = p.red, modifier = Modifier.size(16.dp))
        else -> Icon(Icons.Rounded.CheckCircle, "Done", tint = p.inkFaint, modifier = Modifier.size(16.dp))
    }
}

private fun toolIcon(tool: String): ImageVector = when (tool.lowercase()) {
    "bash", "shell" -> Icons.Rounded.Terminal
    "edit", "multiedit", "write", "notebookedit" -> Icons.Rounded.EditNote
    "read" -> Icons.Rounded.Description
    "grep", "glob", "search", "websearch" -> Icons.Rounded.Search
    "webfetch" -> Icons.Rounded.Public
    "task", "agent" -> Icons.Rounded.AccountTree
    "todowrite" -> Icons.Rounded.Checklist
    else -> Icons.Rounded.Build
}

/** Three dots that ripple while the agent thinks. */
@Composable
fun ThinkingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = a)))
        }
    }
}
