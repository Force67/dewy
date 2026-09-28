package dev.dewy.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.dewy.DewyApp
import kotlinx.coroutines.launch

/** Sends what the user typed into a notification to its task. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        val app = context.applicationContext as DewyApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.store.steer(taskId, text)
                app.alerts.dismiss(taskId)
            } catch (_: Exception) {
                // The notification stays, so the user can open the task and retry.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK = "task"
        const val KEY_TEXT = "text"
    }
}
