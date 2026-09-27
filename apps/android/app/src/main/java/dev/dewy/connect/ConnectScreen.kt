package dev.dewy.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dewy.R
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import kotlinx.coroutines.launch

/** First run: where the orchestrator is and its token. */
@Composable
fun ConnectScreen(onConnect: suspend (url: String, token: String) -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("http://") }
    var token by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    fun connect() {
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            try {
                onConnect(url.trim(), token.trim())
            } catch (e: Exception) {
                problem = e.message ?: "Could not reach the orchestrator."
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.dewWash, p.ground, p.ground)))
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(p.dew, p.dewDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = p.onDew, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Dewy", style = Type.largeTitle, color = p.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your coding agents, in your pocket.",
                style = Type.callout,
                color = p.inkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            val colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.dew,
                unfocusedBorderColor = p.hairline,
                focusedContainerColor = p.surface,
                unfocusedContainerColor = p.surface,
                cursorColor = p.dew,
                focusedLabelColor = p.dew,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Orchestrator") },
                placeholder = { Text("http://100.64.0.2:8787") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = colors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                problem ?: "The token is the orchestrator's DEWY_TOKEN.",
                style = Type.footnote,
                color = if (problem != null) p.red else p.inkFaint,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = ::connect,
                enabled = url.length > 8 && token.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.dew, contentColor = p.onDew),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = p.onDew, strokeWidth = 2.dp)
                } else {
                    Text("Connect", style = Type.headline)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Keep it on a private network such as a tailnet;", style = Type.caption, color = p.inkFaint)
                Text("the orchestrator speaks plain HTTP.", style = Type.caption, color = p.inkFaint)
            }
        }
    }
}
