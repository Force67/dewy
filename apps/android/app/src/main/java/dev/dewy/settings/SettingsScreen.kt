package dev.dewy.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dewy.tasks.Board
import dev.dewy.tasks.Link
import dev.dewy.ui.Group
import dev.dewy.ui.GroupRow
import dev.dewy.ui.Hairline
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: ConnectionSettings, board: Board, onBack: (() -> Unit)?) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val connection by settings.connection.collectAsStateWithLifecycle(null)
    val notify by settings.notify.collectAsStateWithLifecycle(true)
    Scaffold(
        containerColor = p.ground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = Type.headline) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.ground, navigationIconContentColor = p.dew),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Group("Orchestrator") {
                    GroupRow(
                        connection?.serverUrl ?: "",
                        icon = Icons.Rounded.Dns,
                        subtitle = when (board.link) {
                            Link.Live -> "Connected · ${board.boxes.count { it.online }} of ${board.boxes.size} boxes online"
                            Link.Retrying -> board.problem ?: "Reconnecting"
                            else -> "Connecting"
                        },
                        chevron = false,
                    )
                }
            }
            item {
                Group("Notifications") {
                    GroupRow(
                        "Tell me when agents finish",
                        icon = Icons.Rounded.Notifications,
                        subtitle = "Also keeps a live notification while they work",
                        chevron = false,
                    ) {
                        Switch(
                            checked = notify,
                            onCheckedChange = { on -> scope.launch { settings.setNotify(on) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = p.dew),
                        )
                    }
                }
            }
            item {
                Group(null) {
                    GroupRow(
                        "Disconnect",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        iconTint = p.red,
                        chevron = false,
                        onClick = { scope.launch { settings.forget() } },
                    )
                    Hairline(52.dp)
                    GroupRow("Dewy 0.1.0", subtitle = "Agents stay on your boxes; this app only steers.", chevron = false)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
