package dev.dewy.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.dewy.DewyApp
import dev.dewy.R
import dev.dewy.net.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the event stream open while agents work and the app is in the
 * background, so the phone hears when they finish. It shows what they are
 * doing and stops itself once none are working.
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hold: AutoCloseable? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val app = application as DewyApp
        ServiceCompat.startForeground(this, ID, notification(emptyList()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        hold = app.store.hold()
        scope.launch {
            app.store.board
                .map { b -> b.tasks.filter { !it.archived && it.state.isLive } }
                .distinctUntilChanged()
                .collectLatest { live ->
                    if (live.isEmpty()) {
                        // A new turn may start right after one ends.
                        delay(5_000)
                        stopSelf()
                    } else {
                        getSystemService(android.app.NotificationManager::class.java).notify(ID, notification(live))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        hold?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(live: List<Task>): android.app.Notification {
        val app = application as DewyApp
        val title = when (live.size) {
            0 -> "Watching your agents"
            1 -> "${live[0].projectName} is working"
            else -> "${live.size} agents working"
        }
        val lines = live.take(5).map { t -> "${t.projectName}: ${t.activity.ifBlank { t.title }}" }
        return NotificationCompat.Builder(this, Alerts.LIVE)
            .setSmallIcon(R.drawable.ic_drop)
            .setColor(ContextCompat.getColor(this, R.color.dew))
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "Waiting for news")
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setContentIntent(app.alerts.openIntent(live.singleOrNull()?.id))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val ID = 7

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
        }
    }
}
