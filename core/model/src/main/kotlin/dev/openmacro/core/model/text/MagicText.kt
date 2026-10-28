package dev.openmacro.core.model.text

/**
 * Magic text: `{token}` placeholders resolved at run time, e.g. `{battery}`, `{v=name}` (global
 * variable), `{lv=name}` (local variable). Unknown tokens are left untouched so literal braces
 * in user text survive. There is deliberately no brace-escape syntax: `{{`/`}}` appear constantly
 * in JSON, and rewriting them corrupted nested JSON bodies.
 */
object MagicText {

    data class Token(val key: String, val description: String)

    /** Built-in tokens shown in the editor's insert menu. */
    val builtIns: List<Token> = listOf(
        Token("battery", "Battery %"),
        Token("battery_temp", "Battery °C"),
        Token("power", "Charging (on/off)"),
        Token("time", "Time HH:mm"),
        Token("date", "Date yyyy-MM-dd"),
        Token("datetime", "Date and time"),
        Token("hour", "Hour"),
        Token("minute", "Minute"),
        Token("day_of_week", "Day name"),
        Token("day_of_month", "Day of month"),
        Token("month", "Month number"),
        Token("year", "Year"),
        Token("epoch", "Unix time (s)"),
        Token("macro_name", "This macro's name"),
        Token("trigger", "Trigger that fired"),
        Token("sms_from", "SMS sender"),
        Token("sms_body", "SMS text"),
        Token("call_number", "Call number"),
        Token("app_package", "Installed/removed app"),
        Token("notification_app", "Notification's app"),
        Token("notification_title", "Notification title"),
        Token("notification_text", "Notification text"),
        Token("device_model", "Device model"),
        Token("android_version", "Android version"),
        Token("nl", "New line"),
        Token("v=name", "Global variable"),
        Token("lv=name", "Local variable"),
        Token("extra=key", "Intent extra"),
    )

    fun render(template: String, resolve: (String) -> String?): String {
        if ('{' !in template && '}' !in template) return template
        val out = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            when {
                c == '{' -> {
                    val end = template.indexOf('}', i + 1)
                    if (end < 0) {
                        out.append(template, i, template.length)
                        i = template.length
                    } else {
                        val key = template.substring(i + 1, end)
                        if ('{' in key) {
                            // Another '{' opens before this one closes, so this brace is literal
                            // (e.g. JSON). Emit it and rescan from the next character.
                            out.append('{')
                            i++
                        } else {
                            val value = resolve(key)
                            if (value == null) out.append(template, i, end + 1) else out.append(value)
                            i = end + 1
                        }
                    }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
