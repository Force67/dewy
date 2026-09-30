package dev.dewy.newtask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import dev.dewy.images.AttachButton
import dev.dewy.images.AttachmentRow
import dev.dewy.images.discardStaged
import dev.dewy.images.MaxImages
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dewy.net.Box as DewyBox
import dev.dewy.net.Task
import dev.dewy.ui.AgentMark
import dev.dewy.ui.CardShape
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import dev.dewy.ui.agentLabel
import kotlinx.coroutines.launch

// Picked images survive a rotation as strings; a Uri is not Serializable.
private val UriListSaver = listSaver<List<Uri>, String>(save = { it.map(Uri::toString) }, restore = { it.map(Uri::parse) })

/** Where a new task should start, when the user picked it before opening the sheet. */
data class Draft(val box: String? = null, val project: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    boxes: List<DewyBox>,
    draft: Draft,
    yolo: Boolean,
    onYolo: (Boolean) -> Unit,
    onStart: suspend (box: String, project: String, agent: String, text: String, images: List<Uri>, yolo: Boolean) -> Task,
    onStarted: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val online = boxes.filter { it.online }
    var boxName by rememberSaveable { mutableStateOf(draft.box ?: online.firstOrNull()?.name) }
    val box = online.find { it.name == boxName }
    var agent by rememberSaveable(boxName) { mutableStateOf(box?.agents?.firstOrNull()) }
    var project by rememberSaveable(boxName) { mutableStateOf(draft.project.takeIf { draft.box == boxName }) }
    var query by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var images by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf(listOf<Uri>()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = p.ground) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("New Task", style = Type.title, color = p.ink, modifier = Modifier.padding(horizontal = 20.dp))
            if (online.isEmpty()) {
                Text(
                    "No box is online. Start a worker on one of your machines.",
                    style = Type.callout,
                    color = p.inkSoft,
                    modifier = Modifier.padding(20.dp),
                )
                return@Column
            }

            Label("Box")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                online.forEach { b -> Chip(b.name, b.name == boxName) { boxName = b.name } }
            }

            if (box != null && box.agents.size > 1) {
                Label("Agent")
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    box.agents.forEach { a ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (a == agent) p.dewWash else p.surface)
                                .border(0.5.dp, if (a == agent) p.dew else p.hairline, RoundedCornerShape(50))
                                .clickable { agent = a }
                                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AgentMark(a, size = 24.dp)
                            Text(agentLabel(a), style = Type.subhead, color = if (a == agent) p.dewDeep else p.ink)
                        }
                    }
                }
            }

            Label("Project")
            Column(Modifier.padding(horizontal = 16.dp).clip(CardShape).background(p.surface)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = p.inkFaint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search ${box?.projects?.size ?: 0} projects", style = Type.callout, color = p.inkFaint)
                        BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = Type.callout.copy(color = p.ink), cursorBrush = SolidColor(p.dew))
                    }
                }
                Hairline(0.dp)
                val matches = box?.projects.orEmpty().filter { it.name.contains(query, ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(matches, key = { it.path }) { proj ->
                        Row(
                            Modifier.fillMaxWidth().clickable { project = proj.path }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(proj.name, style = Type.body, color = p.ink)
                                Text(proj.path, style = Type.caption, color = p.inkFaint, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                            }
                            if (proj.path == project) Icon(Icons.Rounded.Check, "Chosen", tint = p.dew)
                        }
                    }
                }
            }

            Label("What should it do?")
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(CardShape)
                    .background(p.surface)
                    .padding(14.dp),
            ) {
                if (text.isEmpty()) Text("Fix the flaky login test and open a PR…", style = Type.body, color = p.inkFaint)
                BasicTextField(
                    text,
                    { text = it },
                    textStyle = Type.body.copy(color = p.ink),
                    cursorBrush = SolidColor(p.dew),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AttachButton(room = MaxImages - images.size, onPicked = { images = (images + it).distinct().take(MaxImages) })
                AttachmentRow(images, onRemove = { uri ->
                    images = images - uri
                    scope.launch { discardStaged(listOf(uri)) }
                })
            }

            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(p.surface)
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Yolo mode", style = Type.body, color = p.ink)
                    Text(
                        if (yolo) "Runs any command without asking" else "Edits files; commands that need permission are refused",
                        style = Type.caption,
                        color = if (yolo) p.coral else p.inkSoft,
                    )
                }
                Switch(checked = yolo, onCheckedChange = onYolo, colors = SwitchDefaults.colors(checkedTrackColor = p.coral))
            }

            if (problem != null) {
                Text(problem!!, style = Type.footnote, color = p.red, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            val ready = box != null && agent != null && project != null && (text.isNotBlank() || images.isNotEmpty()) && !busy
            Button(
                onClick = {
                    busy = true
                    problem = null
                    scope.launch {
                        try {
                            val task = onStart(box!!.name, project!!, agent!!, text.trim(), images, yolo)
                            sheet.hide()
                            onStarted(task)
                        } catch (e: Exception) {
                            problem = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = ready,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.dew, contentColor = p.onDew),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = p.onDew, strokeWidth = 2.dp)
                else Text("Start", style = Type.headline)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = Type.sectionLabel,
        color = LocalPalette.current.inkSoft,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.dew else p.surface)
            .border(0.5.dp, if (selected) p.dew else p.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) p.onDew else p.dew))
        Text(text, style = Type.subhead, color = if (selected) p.onDew else p.ink)
    }
}
