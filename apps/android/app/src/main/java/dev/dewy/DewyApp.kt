package dev.dewy

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.dewy.alerts.Alerts
import dev.dewy.alerts.WatchService
import dev.dewy.settings.ConnectionSettings
import dev.dewy.tasks.ReadMarks
import dev.dewy.tasks.TaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Builds the app's parts once and wires them together. */
class DewyApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: ConnectionSettings
        private set
    lateinit var readMarks: ReadMarks
        private set
    lateinit var store: TaskStore
        private set
    lateinit var alerts: Alerts
        private set

    override fun onCreate() {
        super.onCreate()
        settings = ConnectionSettings(this)
        readMarks = ReadMarks(this)
        store = TaskStore(settings.connection, scope)
        alerts = Alerts(this)
        alerts.watch(store.board.map { it.tasks }, settings.notify, scope)

        val visible = MutableStateFlow(false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var hold: AutoCloseable? = null

            override fun onStart(owner: LifecycleOwner) {
                hold = store.hold()
                visible.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                hold?.close()
                visible.value = false
            }
        })

        // A foreground service may only start while the app is visible, so it
        // starts as soon as agents work, not when the user leaves.
        scope.launch {
            val working = store.board.map { b -> b.tasks.any { !it.archived && it.state.isLive } }.distinctUntilChanged()
            combine(working, visible, settings.notify) { w, v, on -> w && v && on }
                .distinctUntilChanged()
                .collect { if (it) WatchService.start(this@DewyApp) }
        }
    }
}
