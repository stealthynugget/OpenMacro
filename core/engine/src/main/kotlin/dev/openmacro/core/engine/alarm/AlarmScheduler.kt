package dev.openmacro.core.engine.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.openmacro.core.engine.EngineLog
import java.time.ZonedDateTime

/**
 * Keeps exactly one alarm armed: the next time-trigger occurrence across all macros. A fixed
 * request code means rescheduling always replaces the previous alarm, so none can go stale.
 */
internal class AlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(at: ZonedDateTime?) {
        if (at == null) {
            alarms.cancel(pendingIntent(0))
            EngineLog.i("No time triggers; alarm cancelled")
            return
        }
        val millis = at.toInstant().toEpochMilli()
        val operation = pendingIntent(millis)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            EngineLog.i("Exact alarm set for $at")
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            EngineLog.w("Exact alarms not permitted; inexact alarm set for $at")
        }
    }

    private fun pendingIntent(fireAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_FIRE_AT, fireAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 7_001
    }
}
