package dev.openmacro.core.engine.runtime

import android.os.Build
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.VarScope
import dev.openmacro.core.model.Variable
import dev.openmacro.core.model.text.MagicText
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Resolves magic-text tokens against live device state, the trigger context and variables. */
class MagicResolver(
    private val device: DeviceProbe,
    private val globals: GlobalVariables,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    fun render(template: String, scope: RunScope?): String =
        MagicText.render(template) { key -> resolve(key, scope) }

    /** Reads a variable reference from the run's locals or the global store. */
    fun variable(ref: VarRef, scope: RunScope?): Variable? = when (ref.scope) {
        VarScope.LOCAL -> scope?.local(ref.name)
        VarScope.GLOBAL -> globals.get(ref.name)
    }

    private fun resolve(key: String, scope: RunScope?): String? {
        when {
            key.startsWith("v=") -> return globals.get(key.removePrefix("v="))?.value ?: ""
            key.startsWith("lv=") -> return scope?.local(key.removePrefix("lv="))?.value ?: ""
            key.startsWith("extra=") -> return scope?.trigger?.value(key) ?: ""
        }
        val t = now()
        return when (key) {
            "battery" -> device.batteryPercent().toString()
            "battery_temp" -> device.batteryTemperatureC().toString()
            "power" -> if (device.isPowerConnected()) "on" else "off"
            "time" -> t.format(HM)
            "date" -> t.format(DATE)
            "datetime" -> t.format(DATETIME)
            "hour" -> t.hour.toString()
            "minute" -> t.minute.toString().padStart(2, '0')
            "day_of_week" -> t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "day_of_month" -> t.dayOfMonth.toString()
            "month" -> t.monthValue.toString()
            "year" -> t.year.toString()
            "epoch" -> t.toEpochSecond().toString()
            "macro_name" -> scope?.macroName ?: ""
            "device_model" -> Build.MODEL
            "android_version" -> Build.VERSION.RELEASE
            "nl" -> "\n"
            else -> scope?.trigger?.value(key)
        }
    }

    private companion object {
        val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
