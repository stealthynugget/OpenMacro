package dev.openmacro.core.engine.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.engine.engine
import dev.openmacro.core.model.NotificationInfo
import dev.openmacro.core.model.TriggerEvent

/**
 * Opt-in notification-listener service. When the user grants notification access it reports posted
 * and removed notifications to the engine and exposes the current set for the "notification present"
 * constraint and the "clear notifications" action. Works entirely on-device.
 */
class OpenMacroNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        EngineLog.i("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        EngineLog.i("Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val info = sbn?.toInfo() ?: return
        if (info.packageName == packageName) return // ignore our own engine/status notifications
        runCatching { engine.submit(TriggerEvent.NotificationPosted(info.packageName, info.title, info.text)) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == packageName) return
        runCatching { engine.submit(TriggerEvent.NotificationRemovedEvent(pkg)) }
    }

    fun activeNotifications(): List<NotificationInfo> = try {
        activeNotifications.orEmpty().mapNotNull { it.toInfo() }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Dismisses notifications from [packageName], or all dismissable ones when blank. */
    fun clear(packageName: String) {
        try {
            if (packageName.isBlank()) {
                cancelAllNotifications()
            } else {
                activeNotifications.orEmpty().filter { it.packageName == packageName }.forEach { cancelByKey(it) }
            }
        } catch (e: SecurityException) {
            EngineLog.w("Clear notifications failed", e)
        }
    }

    private fun cancelByKey(sbn: StatusBarNotification) {
        cancelNotification(sbn.key)
    }

    private fun StatusBarNotification.toInfo(): NotificationInfo? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        return NotificationInfo(packageName, title, text)
    }

    companion object {
        @Volatile
        var instance: OpenMacroNotificationListener? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
