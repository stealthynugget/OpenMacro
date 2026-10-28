package dev.openmacro.core.engine.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.openmacro.core.engine.engine

/** Receives the engine's single exact alarm and dispatches the scheduled time tick. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fireAt = intent.getLongExtra(EXTRA_FIRE_AT, System.currentTimeMillis())
        context.engine.handleBroadcast(goAsync()) { onAlarm(fireAt) }
    }

    internal companion object {
        const val EXTRA_FIRE_AT = "dev.openmacro.extra.FIRE_AT"
    }
}
