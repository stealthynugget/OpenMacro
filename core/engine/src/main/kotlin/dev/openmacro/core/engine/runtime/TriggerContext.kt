package dev.openmacro.core.engine.runtime

import dev.openmacro.core.model.TriggerEvent

/**
 * Values carried from the event that started a macro, exposed to magic text (e.g. `{sms_body}`).
 * Empty for test runs or macros invoked without an event.
 */
class TriggerContext(val event: TriggerEvent?, val label: String) {
    private val values: Map<String, String> = buildMap {
        when (event) {
            is TriggerEvent.SmsReceived -> {
                put("sms_from", event.from)
                put("sms_body", event.body)
            }
            is TriggerEvent.CallChanged -> put("call_number", event.number)
            is TriggerEvent.WebhookReceived -> { put("webhook_body", event.body); put("webhook_method", event.method) }
            is TriggerEvent.PackageChanged -> put("app_package", event.packageName)
            is TriggerEvent.BatteryLevelChanged -> put("battery", event.current.toString())
            is TriggerEvent.IntentReceived -> event.extras.forEach { (k, v) -> put("extra=$k", v) }
            is TriggerEvent.AppForegroundChanged -> put("app_package", event.packageName)
            is TriggerEvent.NotificationPosted -> {
                put("notification_app", event.packageName)
                put("notification_title", event.title)
                put("notification_text", event.text)
            }
            else -> Unit
        }
    }

    fun value(key: String): String? = when (key) {
        "trigger" -> label
        else -> values[key]
    }

    companion object {
        val NONE = TriggerContext(null, "")
    }
}
