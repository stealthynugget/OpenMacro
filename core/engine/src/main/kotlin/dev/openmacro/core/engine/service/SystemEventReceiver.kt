package dev.openmacro.core.engine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.openmacro.core.engine.engine
import kotlinx.coroutines.flow.first

/**
 * Boot, app update, clock changes and exact-alarm permission changes: re-arm the time-trigger
 * alarm and, where the platform allows a background FGS start, bring the daemon back.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val canStartDaemon = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        context.engine.handleBroadcast(goAsync()) {
            if (canStartDaemon && settings.engineEnabled.first()) EngineService.start(context)
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) submit(dev.openmacro.core.model.TriggerEvent.Boot)
            rescheduleAlarms()
        }
    }
}
