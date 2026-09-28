package dev.dewy.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dev.dewy.MainActivity
import dev.dewy.R
import dev.dewy.net.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The phone's side of a task: a notification when a turn ends, fails, or
 * stalls, with a reply field that steers the task right from the shade.
 */
class Alerts(private val context: Context) {
    /** The thread on screen; its changes need no notification. */
    val openThread = MutableStateFlow<String?>(null)

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(TURNS, "Finished and stuck tasks", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When an agent is done, fails, or goes quiet"
                },
                NotificationChannel(LIVE, "Agents at work", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while agents run, so updates keep arriving"
                    setShowBadge(false)
                },
            ),
        )
    }

    /** Posts a notification for each change of [tasks] while [enabled]. */
    fun watch(tasks: Flow<List<Task>>, enabled: Flow<Boolean>, scope: CoroutineScope) = scope.launch {
        var last: List<Task>? = null
        combine(tasks, enabled) { t, on -> t to on }.collect { (now, on) ->
            val before = last
            last = now
            if (before == null || !on) return@collect
            changesBetween(before, now)
                .filter { it.task.id != openThread.value }
                .forEach(::post)
        }
    }

    fun post(change: Change) {
        if (!allowed()) return
        val t = change.task
        val title = when (change.kind) {
            ChangeKind.Finished -> t.title
            ChangeKind.Failed -> "Failed · ${t.title}"
            ChangeKind.Stalled -> "Stuck? · ${t.title}"
        }
        val text = when (change.kind) {
            ChangeKind.Stalled -> "No output for a few minutes. Nudge it or stop it."
            else -> t.preview.ifBlank { if (change.kind == ChangeKind.Failed) "The agent stopped with an error." else "Done." }
        }
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_drop, "Reply", replyIntent(t.id))
            .addRemoteInput(RemoteInput.Builder(ReplyReceiver.KEY_TEXT).setLabel("Tell ${t.agent}…").build())
            .setAllowGeneratedReplies(false)
            .build()
        val n = NotificationCompat.Builder(context, TURNS)
            .setSmallIcon(R.drawable.ic_drop)
            .setColor(ContextCompat.getColor(context, R.color.dew))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("${t.projectName} · ${t.box}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(t.id))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(reply)
            .build()
        NotificationManagerCompat.from(context).notify(t.id, TURN_ID, n)
    }

    fun dismiss(taskId: String) = NotificationManagerCompat.from(context).cancel(taskId, TURN_ID)

    fun openIntent(taskId: String?): PendingIntent = PendingIntent.getActivity(
        context,
        taskId?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_TASK, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun replyIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, ReplyReceiver::class.java).putExtra(ReplyReceiver.EXTRA_TASK, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        const val TURNS = "turns"
        const val LIVE = "live"
        private const val GROUP = "tasks"
        private const val TURN_ID = 1
    }
}
