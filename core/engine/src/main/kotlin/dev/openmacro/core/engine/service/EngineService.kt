package dev.openmacro.core.engine.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.engine.engine
import dev.openmacro.core.engine.platform.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The persistent, low-power daemon. It owns no logic of its own: it keeps the process alive and
 * hosts the dynamic receivers that feed [dev.openmacro.core.engine.MacroEngine].
 */
class EngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var registry: EventSourceRegistry

    override fun onCreate() {
        super.onCreate()
        val engine = engine
        engine.start()
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifications.ENGINE_NOTIFICATION_ID,
            Notifications.engineNotification(this, activeMacros = 0),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        registry = EventSourceRegistry(this, engine::submit)
        scope.launch { engine.requiredSources.collect(registry::update) }
        scope.launch {
            engine.activeMacroCount.collect { count ->
                if (Notifications.canPost(this@EngineService)) {
                    @Suppress("MissingPermission") // checked by canPost()
                    NotificationManagerCompat.from(this@EngineService)
                        .notify(Notifications.ENGINE_NOTIFICATION_ID, Notifications.engineNotification(this@EngineService, count))
                }
            }
        }
        runningState.value = true
        EngineLog.i("EngineService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Each start (e.g. app returning to foreground) re-checks permission-dependent sources.
        registry.refreshPermissionDependent()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        registry.closeAll()
        runningState.value = false
        EngineLog.i("EngineService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val runningState = MutableStateFlow(false)
        val running: StateFlow<Boolean> = runningState.asStateFlow()

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, EngineService::class.java))
            } catch (e: IllegalStateException) {
                // Background start not allowed right now (Android 12+); the next foreground visit retries.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    EngineLog.w("Foreground service start not allowed from background", e)
                } else {
                    throw e
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineService::class.java))
        }
    }
}
