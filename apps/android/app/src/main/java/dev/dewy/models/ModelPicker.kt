package dev.dewy.models

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.dewy.ui.CardShape
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type

/** "opencode-go/kimi-k3" reads as "kimi-k3"; the provider heads its group. */
fun modelName(model: String) = model.substringAfter('/')

fun modelLabel(model: String) = if (model.isEmpty()) "Default model" else modelName(model)

/** Picks one of [models], or "" for the agent's own default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPicker(models: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val groups = models.distinct().filter { it.contains(query.trim(), ignoreCase = true) }.groupBy { it.substringBefore('/') }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = p.ground) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()) {
            Text("Model", style = Type.title, color = p.ink, modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier.padding(16.dp).clip(CardShape).background(p.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.inkFaint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search ${models.size} models", style = Type.callout, color = p.inkFaint)
                    BasicTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = Type.callout.copy(color = p.ink),
                        cursorBrush = SolidColor(p.dew),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                if (query.isBlank()) {
                    item(key = "default") {
                        Choice("Default", "Whatever opencode is set to use", current.isEmpty()) { onPick("") }
                    }
                }
                groups.forEach { (provider, list) ->
                    item(key = "p:$provider") {
                        Text(
                            provider.uppercase(),
                            style = Type.sectionLabel,
                            color = p.inkSoft,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                        )
                        Hairline(0.dp)
                    }
                    items(list, key = { "m:$it" }) { m -> Choice(modelName(m), null, m == current) { onPick(m) } }
                }
                if (groups.isEmpty() && query.isNotBlank()) {
                    item(key = "none") {
                        Text("No model matches.", style = Type.callout, color = p.inkSoft, modifier = Modifier.padding(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, subtitle: String?, chosen: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = p.ink)
            if (subtitle != null) Text(subtitle, style = Type.caption, color = p.inkFaint)
        }
        if (chosen) Icon(Icons.Rounded.Check, "Chosen", tint = p.dew)
    }
}
