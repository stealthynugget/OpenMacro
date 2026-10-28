package dev.openmacro.core.engine.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.openmacro.core.engine.perform.ActionOutcome
import dev.openmacro.core.engine.R
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    const val ENGINE_CHANNEL = "engine_status"
    const val MACRO_CHANNEL = "macro_alerts"
    const val ENGINE_NOTIFICATION_ID = 1

    private val nextId = AtomicInteger(1_000)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(ENGINE_CHANNEL, "Engine status", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Persistent notification while the automation engine is listening"
                    setShowBadge(false)
                },
                NotificationChannel(MACRO_CHANNEL, "Macro notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications posted by your macros"
                },
            ),
        )
    }

    fun engineNotification(context: Context, activeMacros: Int): Notification =
        NotificationCompat.Builder(context, ENGINE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openmacro)
            .setContentTitle("Automation engine active")
            .setContentText(
                when (activeMacros) {
                    0 -> "No enabled macros"
                    1 -> "1 macro listening"
                    else -> "$activeMacros macros listening"
                },
            )
            .setContentIntent(launchIntent(context))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun cameraNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ENGINE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openmacro)
            .setContentTitle("Taking a photo…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    internal fun showMacroNotification(context: Context, title: String, text: String): ActionOutcome {
        if (!canPost(context)) return ActionOutcome.Failed("Notification permission not granted")
        val notification = NotificationCompat.Builder(context, MACRO_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openmacro)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(launchIntent(context))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by canPost()
        NotificationManagerCompat.from(context).notify(nextId.getAndIncrement(), notification)
        return ActionOutcome.Success
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun launchIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
