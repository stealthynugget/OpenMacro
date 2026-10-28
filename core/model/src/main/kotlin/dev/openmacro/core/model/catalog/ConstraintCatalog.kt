package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.AudioStream
import dev.openmacro.core.model.CallState
import dev.openmacro.core.model.CompareOp
import dev.openmacro.core.model.Comparison
import dev.openmacro.core.model.ConstraintSpec as C
import dev.openmacro.core.model.EvalContext
import dev.openmacro.core.model.LogicOp
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.WifiCondition
import dev.openmacro.core.model.text.Expression

internal object ConstraintCatalog {

    private fun compare(comparison: Comparison, actual: Int, bound: Int): Boolean = when (comparison) {
        Comparison.AT_LEAST -> actual >= bound
        Comparison.AT_MOST -> actual <= bound
    }

    private val comparisonLabel: (Comparison) -> String = { if (it == Comparison.AT_LEAST) "At least" else "At most" }

    /** Compares two rendered strings; numeric ops fall back to false when either side isn't numeric. */
    fun compareValues(left: String, op: CompareOp, right: String): Boolean {
        val l = Expression.evaluateOrNull(left) ?: left.trim().toDoubleOrNull()
        val r = Expression.evaluateOrNull(right) ?: right.trim().toDoubleOrNull()
        val numeric = l != null && r != null
        return when (op) {
            CompareOp.EQUALS -> if (numeric) l == r else left == right
            CompareOp.NOT_EQUALS -> if (numeric) l != r else left != right
            CompareOp.GREATER -> numeric && l!! > r!!
            CompareOp.GREATER_OR_EQUAL -> numeric && l!! >= r!!
            CompareOp.LESS -> numeric && l!! < r!!
            CompareOp.LESS_OR_EQUAL -> numeric && l!! <= r!!
            CompareOp.CONTAINS -> left.contains(right, ignoreCase = true)
            CompareOp.NOT_CONTAINS -> !left.contains(right, ignoreCase = true)
            CompareOp.MATCHES_REGEX -> runCatching { Regex(right).matches(left) }.getOrDefault(false)
        }
    }

    private fun opSymbol(op: CompareOp): String = when (op) {
        CompareOp.EQUALS -> "="
        CompareOp.NOT_EQUALS -> "≠"
        CompareOp.GREATER -> ">"
        CompareOp.GREATER_OR_EQUAL -> "≥"
        CompareOp.LESS -> "<"
        CompareOp.LESS_OR_EQUAL -> "≤"
        CompareOp.CONTAINS -> "contains"
        CompareOp.NOT_CONTAINS -> "excludes"
        CompareOp.MATCHES_REGEX -> "matches"
    }

    /** Satisfied between start (incl.) and end (excl.); wraps past midnight; equal = all day. */
    fun inWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean = when {
        startMinutes == endMinutes -> true
        startMinutes < endMinutes -> nowMinutes in startMinutes until endMinutes
        else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
    }

    val all: List<ConstraintDef<*>> = listOf(
        // region Date & time
        constraint<C.TimeWindow>(
            Category.DATE_TIME, "🕒", "Time of day", "Only between two times",
            create = { C.TimeWindow() },
            summary = { "Between ${formatTime(it.startHour, it.startMinute)} and ${formatTime(it.endHour, it.endMinute)}" },
            fields = listOf(
                time("From", { it.startHour }, { it.startMinute }, { s, h, m -> s.copy(startHour = h, startMinute = m) }),
                time("Until", { it.endHour }, { it.endMinute }, { s, h, m -> s.copy(endHour = h, endMinute = m) }),
                info("A window that ends before it starts runs past midnight."),
            ),
        ) { s, ctx ->
            val now = ctx.now().toLocalTime()
            inWindow(now.hour * 60 + now.minute, s.startHour * 60 + s.startMinute, s.endHour * 60 + s.endMinute)
        },
        constraint<C.DayOfWeek>(
            Category.DATE_TIME, "📅", "Day of week", "Only on chosen days",
            create = { C.DayOfWeek() },
            summary = { "On ${formatDays(it.days)}" },
            fields = listOf(multiChoice("Days", (1..7).toList(), ::dayName, { it.days }, { s, v -> s.copy(days = v) })),
        ) { s, ctx -> ctx.now().dayOfWeek.value in s.days },
        constraint<C.DayOfMonth>(
            Category.DATE_TIME, "🗓️", "Day of month", "Only on chosen dates",
            create = { C.DayOfMonth() },
            summary = { "On day ${formatSet(it.days) { d -> d.toString() }} of the month" },
            fields = listOf(multiChoice("Dates", (1..31).toList(), { it.toString() }, { it.days }, { s, v -> s.copy(days = v) })),
        ) { s, ctx -> ctx.now().dayOfMonth in s.days },
        constraint<C.MonthOfYear>(
            Category.DATE_TIME, "📆", "Month", "Only in chosen months",
            create = { C.MonthOfYear() },
            summary = { "In ${formatSet(it.months, ::monthName)}" },
            fields = listOf(multiChoice("Months", (1..12).toList(), ::monthName, { it.months }, { s, v -> s.copy(months = v) })),
        ) { s, ctx -> ctx.now().monthValue in s.months },
        // endregion

        // region Device
        constraint<C.Daytime>(
            Category.DATE_TIME, "🌓", "Daytime / night", "Only during daylight (or only at night)",
            create = { C.Daytime() },
            summary = { if (it.day) "During daytime" else "During night" },
            fields = listOf(
                toggle("When", "Daytime", "Night", { it.day }, { s, v -> s.copy(day = v) }),
                location(
                    "Location",
                    lat = { it.latitude }, lng = { it.longitude },
                    setLatLng = { s, lat, lng -> s.copy(latitude = lat, longitude = lng) },
                    showRadius = false,
                ),
            ),
            requirements = { setOf(Requirement.LOCATION) },
        ) { s, ctx ->
            dev.openmacro.core.model.SolarTime.isDaytime(ctx.now(), s.latitude, s.longitude) == s.day
        },
        constraint<C.DeviceLock>(
            Category.DEVICE, "🔒", "Device lock", "Only when locked or unlocked",
            create = { C.DeviceLock() },
            summary = { if (it.locked) "Device locked" else "Device unlocked" },
            fields = listOf(toggle("State", "Locked", "Unlocked", { it.locked }, { s, v -> s.copy(locked = v) })),
        ) { s, ctx -> ctx.device.isDeviceLocked() == s.locked },
        constraint<C.Screen>(
            Category.DEVICE, "📱", "Screen", "Only when screen is on or off",
            create = { C.Screen() },
            summary = { if (it.on) "Screen on" else "Screen off" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isScreenOn() == s.on },
        constraint<C.Orientation>(
            Category.DEVICE, "🔄", "Orientation", "Only in portrait or landscape",
            create = { C.Orientation() },
            summary = { if (it.portrait) "Portrait" else "Landscape" },
            fields = listOf(toggle("Orientation", "Portrait", "Landscape", { it.portrait }, { s, v -> s.copy(portrait = v) })),
        ) { s, ctx -> ctx.device.isPortrait() == s.portrait },
        constraint<C.AutoRotate>(
            Category.DEVICE, "🔃", "Auto-rotate", "Only when auto-rotate is on or off",
            create = { C.AutoRotate() },
            summary = { "Auto-rotate ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isAutoRotate() == s.on },
        constraint<C.Brightness>(
            Category.DEVICE, "🔆", "Screen brightness", "Based on the screen brightness level",
            create = { C.Brightness() },
            summary = { "Brightness ${it.comparison.symbol()} ${it.percent}%" },
            fields = listOf(
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Level", 0..100, { it.percent }, { s, v -> s.copy(percent = v) }, "%"),
            ),
        ) { s, ctx -> compare(s.comparison, ctx.device.brightnessPercent(), s.percent) },
        constraint<C.AutoBrightness>(
            Category.DEVICE, "🔆", "Auto-brightness", "Only when automatic brightness is on or off",
            create = { C.AutoBrightness() },
            summary = { "Auto-brightness ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isAutoBrightness() == s.on },
        constraint<C.DarkTheme>(
            Category.DEVICE, "🌙", "Dark theme", "Only when dark theme is on or off",
            create = { C.DarkTheme() },
            summary = { "Dark theme ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isDarkTheme() == s.on },
        constraint<C.Torch>(
            Category.DEVICE, "🔦", "Flashlight", "Only when the torch is on or off",
            create = { C.Torch() },
            summary = { "Flashlight ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isTorchOn() == s.on },
        constraint<C.Headphones>(
            Category.DEVICE, "🎧", "Headphones", "Only when headphones are connected or not",
            create = { C.Headphones() },
            summary = { "Headphones ${if (it.connected) "connected" else "disconnected"}" },
            fields = listOf(toggle("State", "Connected", "Disconnected", { it.connected }, { s, v -> s.copy(connected = v) })),
        ) { s, ctx -> ctx.device.isHeadphonesConnected() == s.connected },
        constraint<C.TimeSinceBoot>(
            Category.DEVICE, "⏱️", "Time since boot", "Based on how long since the device started",
            create = { C.TimeSinceBoot() },
            summary = { "Uptime ${it.comparison.symbol()} ${formatDuration(it.minutes * 60)}" },
            fields = listOf(
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Minutes", 0..1440, { it.minutes }, { s, v -> s.copy(minutes = v) }, "min"),
            ),
        ) { s, ctx -> compare(s.comparison, (ctx.device.uptimeMillis() / 60000).toInt(), s.minutes) },
        // endregion

        // region Battery & power
        constraint<C.BatteryThreshold>(
            Category.BATTERY_POWER, "🔋", "Battery level", "Only above or below a level",
            create = { C.BatteryThreshold() },
            summary = { "Battery ${it.comparison.symbol()} ${it.percent}%" },
            fields = listOf(
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Level", 0..100, { it.percent }, { s, v -> s.copy(percent = v) }, "%"),
            ),
        ) { s, ctx -> compare(s.comparison, ctx.device.batteryPercent(), s.percent) },
        constraint<C.BatteryTemperature>(
            Category.BATTERY_POWER, "🌡️", "Battery temperature", "Only above or below a temperature",
            create = { C.BatteryTemperature() },
            summary = { "Battery temp ${it.comparison.symbol()} ${it.celsius}°C" },
            fields = listOf(
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Temperature", 0..70, { it.celsius }, { s, v -> s.copy(celsius = v) }, "°C"),
            ),
        ) { s, ctx -> compare(s.comparison, ctx.device.batteryTemperatureC(), s.celsius) },
        constraint<C.Power>(
            Category.BATTERY_POWER, "🔌", "Power", "Only when charging or not",
            create = { C.Power() },
            summary = { if (it.connected) "Charging" else "Not charging" },
            fields = listOf(toggle("State", "Charging", "Not charging", { it.connected }, { s, v -> s.copy(connected = v) })),
        ) { s, ctx -> ctx.device.isPowerConnected() == s.connected },
        constraint<C.BatterySaver>(
            Category.BATTERY_POWER, "🪫", "Battery saver", "Only when battery saver is on or off",
            create = { C.BatterySaver() },
            summary = { "Battery saver ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isBatterySaver() == s.on },
        constraint<C.ChargingMethod>(
            Category.BATTERY_POWER, "🔌", "Charging method", "Only when charging via a given source",
            create = { C.ChargingMethod() },
            summary = { "Charging via ${humanize(it.source).lowercase()}" },
            fields = listOf(choice("Source", dev.openmacro.core.model.ChargingSource.entries, { it.source }, { s, v -> s.copy(source = v) })),
        ) { s, ctx -> ctx.device.chargingSource() == s.source },
        // endregion

        // region Connectivity
        constraint<C.Wifi>(
            Category.CONNECTIVITY, "📶", "Wi-Fi", "Based on Wi-Fi state",
            create = { C.Wifi() },
            summary = { humanize(it.state) },
            fields = listOf(choice("State", WifiCondition.entries, { it.state }, { s, v -> s.copy(state = v) })),
        ) { s, ctx ->
            when (s.state) {
                WifiCondition.ENABLED -> ctx.device.isWifiEnabled()
                WifiCondition.DISABLED -> !ctx.device.isWifiEnabled()
                WifiCondition.CONNECTED -> ctx.device.isWifiConnected()
                WifiCondition.NOT_CONNECTED -> !ctx.device.isWifiConnected()
            }
        },
        constraint<C.Bluetooth>(
            Category.CONNECTIVITY, "🔵", "Bluetooth", "Only when Bluetooth is on or off",
            create = { C.Bluetooth() },
            summary = { "Bluetooth ${if (it.enabled) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.enabled }, { s, v -> s.copy(enabled = v) })),
        ) { s, ctx -> ctx.device.isBluetoothEnabled() == s.enabled },
        constraint<C.AirplaneMode>(
            Category.CONNECTIVITY, "✈️", "Airplane mode", "Only when airplane mode is on or off",
            create = { C.AirplaneMode() },
            summary = { "Airplane mode ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isAirplaneMode() == s.on },
        constraint<C.Roaming>(
            Category.CONNECTIVITY, "🌍", "Roaming", "Only when roaming or not",
            create = { C.Roaming() },
            summary = { if (it.roaming) "Roaming" else "Not roaming" },
            fields = listOf(toggle("State", "Roaming", "Not roaming", { it.roaming }, { s, v -> s.copy(roaming = v) })),
        ) { s, ctx -> ctx.device.isRoaming() == s.roaming },
        constraint<C.Vpn>(
            Category.CONNECTIVITY, "🔐", "VPN", "Only when a VPN is active or not",
            create = { C.Vpn() },
            summary = { if (it.active) "VPN active" else "VPN inactive" },
            fields = listOf(toggle("State", "Active", "Inactive", { it.active }, { s, v -> s.copy(active = v) })),
        ) { s, ctx -> ctx.device.isVpnActive() == s.active },
        constraint<C.Nfc>(
            Category.CONNECTIVITY, "📳", "NFC", "Only when NFC is on or off",
            create = { C.Nfc() },
            summary = { "NFC ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isNfcEnabled() == s.on },
        constraint<C.LocationEnabled>(
            Category.CONNECTIVITY, "📍", "Location services", "Only when location is on or off",
            create = { C.LocationEnabled() },
            summary = { "Location ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ) { s, ctx -> ctx.device.isLocationEnabled() == s.on },
        // endregion

        // region Audio & media
        constraint<C.RingerModeIs>(
            Category.AUDIO_MEDIA, "🔔", "Ringer mode", "Only in a given ringer mode",
            create = { C.RingerModeIs() },
            summary = { "Ringer is ${humanize(it.mode)}" },
            fields = listOf(choice("Mode", RingerMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
        ) { s, ctx -> ctx.device.ringerMode() == s.mode },
        constraint<C.VolumeLevel>(
            Category.AUDIO_MEDIA, "🔊", "Volume level", "Based on a stream's volume",
            create = { C.VolumeLevel() },
            summary = { "${humanize(it.stream)} volume ${it.comparison.symbol()} ${it.percent}%" },
            fields = listOf(
                choice("Stream", AudioStream.entries, { it.stream }, { s, v -> s.copy(stream = v) }),
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Level", 0..100, { it.percent }, { s, v -> s.copy(percent = v) }, "%"),
            ),
        ) { s, ctx -> compare(s.comparison, ctx.device.volumePercent(s.stream), s.percent) },
        constraint<C.MusicActive>(
            Category.AUDIO_MEDIA, "🎵", "Music playing", "Only when audio is or isn't playing",
            create = { C.MusicActive() },
            summary = { if (it.active) "Music playing" else "No music playing" },
            fields = listOf(toggle("State", "Playing", "Not playing", { it.active }, { s, v -> s.copy(active = v) })),
        ) { s, ctx -> ctx.device.isMusicActive() == s.active },
        // endregion

        // region Call
        constraint<C.Call>(
            Category.PHONE_SMS, "📞", "Call state", "Only when idle, ringing or in a call",
            create = { C.Call() },
            summary = { "Call: ${humanize(it.state)}" },
            fields = listOf(choice("State", CallState.entries, { it.state }, { s, v -> s.copy(state = v) })),
            requirements = { setOf(Requirement.PHONE_STATE) },
        ) { s, ctx -> ctx.device.callState() == s.state },
        // endregion

        // region OpenMacro & variables
        constraint<C.Doze>(
            Category.DEVICE, "😴", "Doze (idle)", "Only when the device is (or isn't) in deep idle",
            create = { C.Doze() },
            summary = { if (it.idle) "In doze" else "Not in doze" },
            fields = listOf(toggle("State", "In doze", "Active", { it.idle }, { s, v -> s.copy(idle = v) })),
        ) { s, ctx -> ctx.device.isDeviceIdle() == s.idle },
        constraint<C.ActiveApp>(
            Category.APPS, "📲", "Active app", "Only when a given app is (or isn't) in the foreground",
            create = { C.ActiveApp() },
            summary = { "${it.packageName.orAny("App")} is ${if (it.present) "in foreground" else "not in foreground"}" },
            fields = listOf(
                app("App", { it.packageName }, { s, v -> s.copy(packageName = v) }),
                toggle("Condition", "In foreground", "Not in foreground", { it.present }, { s, v -> s.copy(present = v) }),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
        ) { s, ctx -> (ctx.device.foregroundApp() == s.packageName) == s.present },
        constraint<C.MacroEnabled>(
            Category.MACRO, "🎚️", "Macro enabled", "Only when another macro is enabled or disabled",
            create = { C.MacroEnabled() },
            summary = { "${it.macro.display()} is ${if (it.enabled) "enabled" else "disabled"}" },
            fields = listOf(
                macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) }),
                toggle("State", "Enabled", "Disabled", { it.enabled }, { s, v -> s.copy(enabled = v) }),
            ),
        ) { s, ctx -> ctx.isMacroEnabled(s.macro.id) == s.enabled },
        constraint<C.MacroRunning>(
            Category.MACRO, "🏃", "Macro running", "Only when another macro is running or not",
            create = { C.MacroRunning() },
            summary = { "${it.macro.display()} is ${if (it.running) "running" else "not running"}" },
            fields = listOf(
                macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) }),
                toggle("State", "Running", "Not running", { it.running }, { s, v -> s.copy(running = v) }),
            ),
        ) { s, ctx -> ctx.isMacroRunning(s.macro.id) == s.running },
        constraint<C.LastRun>(
            Category.MACRO, "⏲️", "Last run time", "Based on when this macro last ran",
            create = { C.LastRun() },
            summary = { "Last run ${it.comparison.symbol()} ${formatDuration(it.minutes * 60)} ago" },
            fields = listOf(
                choice("Comparison", Comparison.entries, { it.comparison }, { s, v -> s.copy(comparison = v) }, comparisonLabel),
                number("Minutes ago", 0..1440, { it.minutes }, { s, v -> s.copy(minutes = v) }, "min"),
            ),
        ) { s, ctx ->
            val last = ctx.lastRunAt(ctx.currentMacroId)
            val minutesAgo = if (last == null) Int.MAX_VALUE else ((System.currentTimeMillis() - last) / 60000).toInt()
            compare(s.comparison, minutesAgo, s.minutes)
        },
        constraint<C.VariableCompare>(
            Category.VARIABLES, "🔢", "Variable value", "Compare a variable to a value",
            create = { C.VariableCompare() },
            summary = { "${it.variable.display()} ${opSymbol(it.op)} ${it.value.quoted()}" },
            fields = listOf(
                variable("Variable", { it.variable }, { s, v -> s.copy(variable = v) }),
                choice("Operator", CompareOp.entries, { it.op }, { s, v -> s.copy(op = v) }, ::opSymbol),
                text("Value", { it.value }, { s, v -> s.copy(value = v) }),
            ),
        ) { s, ctx ->
            val left = ctx.variable(s.variable)?.value ?: ""
            compareValues(left, s.op, ctx.render(s.value))
        },
        constraint<C.ScreenContains>(
            Category.UI_AUTOMATION, "👁️", "Text on screen", "Text is (or isn't) visible on screen",
            create = { C.ScreenContains() },
            summary = { "${if (it.present) "Screen shows" else "Screen hides"} ${it.text.quoted()}" },
            fields = listOf(
                text("Text contains", { it.text }, { s, v -> s.copy(text = v) }),
                toggle("Condition", "Is shown", "Is not shown", { it.present }, { s, v -> s.copy(present = v) }),
            ),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
        ) { s, ctx ->
            val found = ctx.device.screenText().contains(ctx.render(s.text), ignoreCase = true)
            found == s.present
        },
        constraint<C.NotificationPresent>(
            Category.NOTIFICATIONS, "🔔", "Notification present", "A matching notification is currently shown",
            create = { C.NotificationPresent() },
            summary = {
                val who = if (it.packageName.isBlank()) "A notification" else "${it.packageName} notification"
                val suffix = if (it.textContains.isBlank()) "" else " containing ${it.textContains.quoted()}"
                "$who$suffix is ${if (it.present) "shown" else "not shown"}"
            },
            fields = listOf(
                app("App (blank = any)", { it.packageName }, { s, v -> s.copy(packageName = v) }, allowAny = true),
                text("Text contains (optional)", { it.textContains }, { s, v -> s.copy(textContains = v) }, magicText = false),
                toggle("Condition", "Is shown", "Is not shown", { it.present }, { s, v -> s.copy(present = v) }),
            ),
            requirements = { setOf(Requirement.NOTIFICATION_ACCESS) },
        ) { s, ctx ->
            val found = ctx.device.activeNotifications().any { n ->
                (s.packageName.isBlank() || n.packageName == s.packageName) &&
                    (s.textContains.isBlank() || "${n.title} ${n.text}".contains(s.textContains.trim(), ignoreCase = true))
            }
            found == s.present
        },
        constraint<C.Logic>(
            Category.LOGIC, "🧮", "Logic (AND/OR/NOT)", "Combine several conditions",
            create = { C.Logic() },
            summary = {
                if (it.constraints.isEmpty()) "Empty ${it.op} group" else "${it.op} of ${it.constraints.size} condition(s)"
            },
            fields = listOf(
                choice("Operator", LogicOp.entries, { it.op }, { s, v -> s.copy(op = v) }),
                conditions("Conditions", { it.constraints }, { s, v -> s.copy(constraints = v) }),
            ),
        ) { s, ctx ->
            val results = s.constraints.map { Catalog.evaluate(it, ctx) }
            when (s.op) {
                LogicOp.AND -> results.all { it }
                LogicOp.OR -> results.isEmpty() || results.any { it }
                LogicOp.NOT -> results.none { it }
                LogicOp.XOR -> results.count { it } % 2 == 1
            }
        },
        // endregion
    )
}
