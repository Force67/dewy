package dev.dewy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import dev.dewy.images.LocalImageStore
import dev.dewy.shell.Shell
import dev.dewy.ui.DewyTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** Tasks to open, from notification taps. */
    private val openRequests = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DewyApp
        if (savedInstanceState == null) handle(intent)
        setContent {
            DewyTheme {
                CompositionLocalProvider(LocalImageStore provides app.images) {
                    Shell(
                        store = app.store,
                        settings = app.settings,
                        readMarks = app.readMarks,
                        alerts = app.alerts,
                        openRequests = openRequests.receiveAsFlow(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TASK)?.let { openRequests.trySend(it) }
    }

    companion object {
        const val EXTRA_TASK = "task"
    }
}
