package dev.dewy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(14.dp)

/** An inset, rounded group of rows with a small caps label above it. */
@Composable
fun Group(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.padding(horizontal = 16.dp)) {
        if (label != null) {
            Text(
                label.uppercase(),
                style = Type.sectionLabel,
                color = p.inkSoft,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp, top = 22.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(CardShape).background(p.surface), content = content)
    }
}

@Composable
fun Hairline(inset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalPalette.current.hairline),
    )
}

/** A tappable row in a [Group]: an icon, a title, and whatever trails. */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    subtitle: String? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(PaddingValues(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint ?: p.dew, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = p.ink, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = p.inkSoft, maxLines = 1)
        }
        trailing()
        if (chevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = p.inkFaint,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun CountBadge(count: Int, highlighted: Boolean = false) {
    if (count <= 0) return
    val p = LocalPalette.current
    Text(
        count.toString(),
        style = Type.subhead,
        color = if (highlighted) p.dew else p.inkSoft,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
