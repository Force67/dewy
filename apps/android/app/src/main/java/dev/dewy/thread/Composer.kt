package dev.dewy.thread

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type

/** The message field. With nothing typed while the agent works, it offers stop. */
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    canStop: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(p.surface)
                .border(0.5.dp, p.hairline, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(placeholder, style = Type.body, color = p.inkFaint)
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = Type.body.copy(color = p.ink),
                cursorBrush = SolidColor(p.dew),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val mode = when {
            text.isNotBlank() -> 1
            canStop -> 2
            else -> 0
        }
        AnimatedContent(mode, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "action") { m ->
            when (m) {
                1 -> RoundButton(Icons.Rounded.ArrowUpward, "Send", enabled = true) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSend()
                }
                2 -> RoundButton(Icons.Rounded.Stop, "Stop", enabled = true, tinted = false) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStop()
                }
                else -> RoundButton(Icons.Rounded.ArrowUpward, "Send", enabled = false) {}
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tinted: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val background = when {
        !enabled -> p.hairline
        tinted -> p.dew
        else -> p.ink
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(background),
    ) {
        Icon(icon, label, tint = if (enabled) p.surface else p.inkFaint, modifier = Modifier.size(22.dp))
    }
}
