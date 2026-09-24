package dev.dewy.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dewy.net.TaskState

@Composable
fun stateColor(state: TaskState): Color {
    val p = LocalPalette.current
    return when (state) {
        TaskState.Working -> p.dew
        TaskState.Idle -> p.amber
        TaskState.Stalled -> p.coral
        TaskState.Failed -> p.red
        TaskState.Stopped -> p.slate
    }
}

fun stateLabel(state: TaskState) = when (state) {
    TaskState.Working -> "Working"
    TaskState.Idle -> "Your turn"
    TaskState.Stalled -> "Stuck?"
    TaskState.Failed -> "Failed"
    TaskState.Stopped -> "Stopped"
}

/** A status dot; a working one breathes. */
@Composable
fun StateDot(state: TaskState, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val color = stateColor(state)
    if (state == TaskState.Working) {
        val t = rememberInfiniteTransition(label = "breath")
        val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
        Box(
            modifier
                .size(size)
                .drawBehind {
                    drawCircle(color.copy(alpha = 0.35f * (1f - pulse)), radius = this.size.minDimension / 2 * (1f + pulse * 1.3f))
                    drawCircle(color)
                },
        )
    } else {
        Box(modifier.size(size).clip(CircleShape).background(color))
    }
}

@Composable
fun StatePill(state: TaskState, modifier: Modifier = Modifier) {
    val color = stateColor(state)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StateDot(state, size = 7.dp)
        Text(stateLabel(state), style = Type.caption, color = color)
    }
}

/** The agent's mark: Claude in its clay, Codex in graphite. */
@Composable
fun AgentMark(agent: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val p = LocalPalette.current
    val (brush, glyph) = when (agent) {
        "claude" -> Brush.linearGradient(listOf(Color(0xFFE08A67), Color(0xFFC4613D))) to "✳"
        "codex" -> Brush.linearGradient(
            if (p.isDark) listOf(Color(0xFF3A4247), Color(0xFF22282B)) else listOf(Color(0xFF30373B), Color(0xFF111517)),
        ) to "◎"
        else -> Brush.linearGradient(listOf(p.dew, p.dewDeep)) to agent.take(1).uppercase()
    }
    Box(modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(glyph, color = Color.White, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.SemiBold)
    }
}

fun agentLabel(agent: String) = when (agent) {
    "claude" -> "Claude Code"
    "codex" -> "Codex"
    else -> agent.replaceFirstChar { it.uppercase() }
}
