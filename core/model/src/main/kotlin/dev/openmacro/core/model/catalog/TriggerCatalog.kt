package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.BluetoothEvent
import dev.openmacro.core.model.CallEvent
import dev.openmacro.core.model.FileEvent
import dev.openmacro.core.model.HttpTriggerMethod
import dev.openmacro.core.model.EventSource
import dev.openmacro.core.model.LevelDirection
import dev.openmacro.core.model.PackageEvent
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.SourceKey
import dev.openmacro.core.model.TriggerEvent as E
import dev.openmacro.core.model.TriggerSpec as T
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal object TriggerCatalog {

    private fun src(source: EventSource, param: String = "") = SourceKey(source, param)

    private fun contains(filter: String, value: String?): Boolean =
        filter.isBlank() || value?.contains(filter.trim(), ignoreCase = true) == true

    private val directionLabel: (LevelDirection) -> String = {
        when (it) {
            LevelDirection.FALLS_TO -> "Drops to"
            LevelDirection.RISES_TO -> "Rises to"
            LevelDirection.ANY_CHANGE -> "Any change"
        }
    }

    private fun crossed(direction: LevelDirection, threshold: Int, previous: Int, current: Int): Boolean = when (direction) {
        LevelDirection.FALLS_TO -> previous > threshold && current <= threshold
        LevelDirection.RISES_TO -> previous < threshold && current >= threshold
        LevelDirection.ANY_CHANGE -> previous != current
    }

    fun nextTimeOfDay(spec: T.TimeOfDay, now: ZonedDateTime): ZonedDateTime? {
        if (spec.daysOfWeek.isEmpty()) return null
        val today = now.truncatedTo(ChronoUnit.DAYS)
        for (offset in 0..7L) {
            // Resolve via the local date-time so DST gaps shift forward rather than vanish.
            val candidate = today.plusDays(offset).toLocalDate().atTime(spec.hour, spec.minute).atZone(now.zone)
            if (candidate.isAfter(now) && candidate.dayOfWeek.value in spec.daysOfWeek) return candidate
        }
        return null
    }

    fun nextInterval(spec: T.RegularInterval, now: ZonedDateTime): ZonedDateTime {
        val interval = spec.minutes.coerceAtLeast(1).toLong()
        val epochMinute = now.toEpochSecond() / 60
        val next = (epochMinute / interval + 1) * interval
        return Instant.ofEpochSecond(next * 60).atZone(now.zone)
    }

    val all: List<TriggerDef<*>> = listOf(
        // region Battery & power
        trigger<T.BatteryLevel>(
            Category.BATTERY_POWER, "🔋", "Battery level", "Battery drops to or rises to a level",
            create = { T.BatteryLevel() },
            summary = {
                when (it.direction) {
                    LevelDirection.FALLS_TO -> "Battery drops to ${it.threshold}%"
                    LevelDirection.RISES_TO -> "Battery rises to ${it.threshold}%"
                    LevelDirection.ANY_CHANGE -> "Battery level changes"
                }
            },
            fields = listOf(
                choice("When", LevelDirection.entries, { it.direction }, { s, v -> s.copy(direction = v) }, directionLabel),
                number("Level", 1..100, { it.threshold }, { s, v -> s.copy(threshold = v) }, "%", visible = { it.direction != LevelDirection.ANY_CHANGE }),
            ),
            source = { src(EventSource.BATTERY) },
            matches = { s, e -> e is E.BatteryLevelChanged && crossed(s.direction, s.threshold, e.previous, e.current) },
        ),
        trigger<T.BatteryTemperature>(
            Category.BATTERY_POWER, "🌡️", "Battery temperature", "Battery gets hotter or cooler than a limit",
            create = { T.BatteryTemperature() },
            summary = {
                when (it.direction) {
                    LevelDirection.FALLS_TO -> "Battery cools to ${it.celsius}°C"
                    LevelDirection.RISES_TO -> "Battery heats to ${it.celsius}°C"
                    LevelDirection.ANY_CHANGE -> "Battery temperature changes"
                }
            },
            fields = listOf(
                choice("When", LevelDirection.entries, { it.direction }, { s, v -> s.copy(direction = v) }, directionLabel),
                number("Temperature", 0..70, { it.celsius }, { s, v -> s.copy(celsius = v) }, "°C", visible = { it.direction != LevelDirection.ANY_CHANGE }),
            ),
            source = { src(EventSource.BATTERY) },
            matches = { s, e -> e is E.BatteryTemperatureChanged && crossed(s.direction, s.celsius, e.previous, e.current) },
        ),
        trigger<T.PowerConnection>(
            Category.BATTERY_POWER, "🔌", "Power", "Charger connected or disconnected",
            create = { T.PowerConnection() },
            summary = { if (it.connected) "Power connected" else "Power disconnected" },
            fields = listOf(toggle("State", "Connected", "Disconnected", { it.connected }, { s, v -> s.copy(connected = v) })),
            source = { src(EventSource.POWER) },
            matches = { s, e -> e is E.PowerConnectionChanged && e.connected == s.connected },
        ),
        trigger<T.BatterySaver>(
            Category.BATTERY_POWER, "🪫", "Battery saver", "Battery saver turns on or off",
            create = { T.BatterySaver() },
            summary = { "Battery saver ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.BATTERY_SAVER) },
            matches = { s, e -> e is E.BatterySaverChanged && e.on == s.on },
        ),
        // endregion

        // region Connectivity
        trigger<T.WifiState>(
            Category.CONNECTIVITY, "📶", "Wi-Fi", "Wi-Fi connects or disconnects",
            create = { T.WifiState() },
            summary = {
                val base = if (it.connected) "Wi-Fi connected" else "Wi-Fi disconnected"
                if (it.connected && it.ssid.isNotBlank()) "$base to ${it.ssid.quoted()}" else base
            },
            fields = listOf(
                toggle("State", "Connected", "Disconnected", { it.connected }, { s, v -> s.copy(connected = v) }),
                text("Network name (optional)", { it.ssid }, { s, v -> s.copy(ssid = v) }, magicText = false, visible = { it.connected }),
            ),
            requirements = { if (it.connected && it.ssid.isNotBlank()) setOf(Requirement.LOCATION) else emptySet() },
            source = { src(EventSource.WIFI) },
            matches = { s, e ->
                e is E.WifiChanged && e.connected == s.connected &&
                    (!s.connected || s.ssid.isBlank() || e.ssid?.equals(s.ssid.trim(), ignoreCase = true) == true)
            },
        ),
        trigger<T.BluetoothState>(
            Category.CONNECTIVITY, "🔵", "Bluetooth", "Bluetooth turns on/off or a device connects",
            create = { T.BluetoothState() },
            summary = {
                val base = when (it.event) {
                    BluetoothEvent.ADAPTER_ON -> "Bluetooth turned on"
                    BluetoothEvent.ADAPTER_OFF -> "Bluetooth turned off"
                    BluetoothEvent.DEVICE_CONNECTED -> "BT device connected"
                    BluetoothEvent.DEVICE_DISCONNECTED -> "BT device disconnected"
                }
                if (it.deviceNameFilter.isNotBlank() && it.event.isDevice()) "$base · ${it.deviceNameFilter.quoted()}" else base
            },
            fields = listOf(
                choice("Event", BluetoothEvent.entries, { it.event }, { s, v -> s.copy(event = v) }),
                text("Device name contains (optional)", { it.deviceNameFilter }, { s, v -> s.copy(deviceNameFilter = v) }, magicText = false, visible = { it.event.isDevice() }),
            ),
            requirements = { if (it.event.isDevice()) setOf(Requirement.BLUETOOTH_CONNECT) else emptySet() },
            source = { src(EventSource.BLUETOOTH) },
            matches = { s, e ->
                when (s.event) {
                    BluetoothEvent.ADAPTER_ON -> e is E.BluetoothAdapterChanged && e.on
                    BluetoothEvent.ADAPTER_OFF -> e is E.BluetoothAdapterChanged && !e.on
                    BluetoothEvent.DEVICE_CONNECTED -> e is E.BluetoothDeviceChanged && e.connected && contains(s.deviceNameFilter, e.deviceName)
                    BluetoothEvent.DEVICE_DISCONNECTED -> e is E.BluetoothDeviceChanged && !e.connected && contains(s.deviceNameFilter, e.deviceName)
                }
            },
        ),
        trigger<T.AirplaneMode>(
            Category.CONNECTIVITY, "✈️", "Airplane mode", "Airplane mode turns on or off",
            create = { T.AirplaneMode() },
            summary = { "Airplane mode ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.AIRPLANE) },
            matches = { s, e -> e is E.AirplaneModeChanged && e.on == s.on },
        ),
        trigger<T.SimStateChanged>(
            Category.PHONE_SMS, "📇", "SIM state changed", "A SIM card changes state (inserted, removed, ready)",
            create = { T.SimStateChanged },
            summary = { "SIM state changed" },
            source = { src(EventSource.CUSTOM_INTENT, "android.intent.action.SIM_STATE_CHANGED") },
            matches = { _, e -> e is E.IntentReceived && e.action == "android.intent.action.SIM_STATE_CHANGED" },
        ),
        trigger<T.NfcState>(
            Category.CONNECTIVITY, "📳", "NFC", "NFC turns on or off",
            create = { T.NfcState() },
            summary = { "NFC ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.NFC) },
            matches = { s, e -> e is E.NfcChanged && e.on == s.on },
        ),
        trigger<T.LocationServices>(
            Category.CONNECTIVITY, "📍", "Location services", "Location is switched on or off",
            create = { T.LocationServices() },
            summary = { "Location ${if (it.enabled) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.enabled }, { s, v -> s.copy(enabled = v) })),
            source = { src(EventSource.LOCATION_MODE) },
            matches = { s, e -> e is E.LocationServicesChanged && e.enabled == s.enabled },
        ),
        // endregion

        // region Device
        trigger<T.ScreenState>(
            Category.DEVICE, "📱", "Screen", "Screen turns on or off",
            create = { T.ScreenState() },
            summary = { if (it.on) "Screen turned on" else "Screen turned off" },
            fields = listOf(toggle("State", "Turned on", "Turned off", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.SCREEN) },
            matches = { s, e -> e is E.ScreenChanged && e.on == s.on },
        ),
        trigger<T.DeviceUnlocked>(
            Category.DEVICE, "🔓", "Device unlocked", "You unlock the device",
            create = { T.DeviceUnlocked },
            summary = { "Device unlocked" },
            source = { src(EventSource.UNLOCK) },
            matches = { _, e -> e is E.DeviceUnlocked },
        ),
        trigger<T.DarkTheme>(
            Category.DEVICE, "🌙", "Dark theme", "System dark theme turns on or off",
            create = { T.DarkTheme() },
            summary = { "Dark theme ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.DARK_THEME) },
            matches = { s, e -> e is E.DarkThemeChanged && e.on == s.on },
        ),
        trigger<T.Headphones>(
            Category.DEVICE, "🎧", "Headphones", "Wired, USB or Bluetooth headphones connect or disconnect",
            create = { T.Headphones() },
            summary = { if (it.connected) "Headphones connected" else "Headphones disconnected" },
            fields = listOf(toggle("State", "Connected", "Disconnected", { it.connected }, { s, v -> s.copy(connected = v) })),
            source = { src(EventSource.HEADPHONES) },
            matches = { s, e -> e is E.HeadphonesChanged && e.connected == s.connected },
        ),
        trigger<T.Usb>(
            Category.CONNECTIVITY, "🔌", "USB", "A USB cable is connected or disconnected",
            create = { T.Usb() },
            summary = { if (it.connected) "USB connected" else "USB disconnected" },
            fields = listOf(toggle("State", "Connected", "Disconnected", { it.connected }, { s, v -> s.copy(connected = v) })),
            source = { src(EventSource.BATTERY) },
            matches = { s, e -> e is E.UsbChanged && e.connected == s.connected },
        ),
        trigger<T.Dock>(
            Category.DEVICE, "🛏️", "Dock", "Device is docked or undocked",
            create = { T.Dock() },
            summary = { if (it.docked) "Docked" else "Undocked" },
            fields = listOf(toggle("State", "Docked", "Undocked", { it.docked }, { s, v -> s.copy(docked = v) })),
            source = { src(EventSource.DOCK) },
            matches = { s, e -> e is E.DockChanged && e.docked == s.docked },
        ),
        trigger<T.DozeMode>(
            Category.DEVICE, "😴", "Doze (idle) mode", "The device enters or exits deep idle",
            create = { T.DozeMode() },
            summary = { "Doze ${if (it.on) "entered" else "exited"}" },
            fields = listOf(toggle("State", "Entered", "Exited", { it.on }, { s, v -> s.copy(on = v) })),
            source = { src(EventSource.DOZE) },
            matches = { s, e -> e is E.DozeChanged && e.on == s.on },
        ),
        trigger<T.RingerModeChanged>(
            Category.AUDIO_MEDIA, "🔕", "Ringer mode changed", "Ringer switches to normal, vibrate or silent",
            create = { T.RingerModeChanged() },
            summary = { "Ringer → ${humanize(it.mode)}" },
            fields = listOf(choice("Mode", RingerMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            source = { src(EventSource.RINGER) },
            matches = { s, e -> e is E.RingerModeChanged && e.mode == s.mode },
        ),
        trigger<T.Boot>(
            Category.DEVICE, "🔁", "Device boot", "The device finished starting up",
            create = { T.Boot },
            summary = { "Device boot" },
            source = { src(EventSource.INTERNAL) },
            matches = { _, e -> e is E.Boot },
        ),
        trigger<T.FileChanged>(
            Category.DEVICE, "📂", "File changed", "A file or folder is created, modified or deleted",
            create = { T.FileChanged() },
            summary = { "${humanize(it.event)}: ${it.path}" },
            fields = listOf(
                text("Path", { it.path }, { s, v -> s.copy(path = v) }, magicText = false, hint = "/sdcard/Download"),
                choice("Event", FileEvent.entries, { it.event }, { s, v -> s.copy(event = v) }),
            ),
            requirements = { setOf(Requirement.ALL_FILES) },
            source = { src(EventSource.FILE, it.path) },
            matches = { s, e -> e is E.FileChangedEvent && e.path == s.path && e.event == s.event },
        ),
        trigger<T.StorageLow>(
            Category.DEVICE, "💽", "Storage low", "Device storage runs low (or recovers)",
            create = { T.StorageLow() },
            summary = { if (it.low) "Storage low" else "Storage OK" },
            fields = listOf(toggle("State", "Low", "OK", { it.low }, { s, v -> s.copy(low = v) })),
            source = { src(EventSource.STORAGE) },
            matches = { s, e -> e is E.StorageChanged && e.low == s.low },
        ),
        trigger<T.TimezoneChanged>(
            Category.DATE_TIME, "🌐", "Time zone changed", "The device time zone changes (e.g. travel)",
            create = { T.TimezoneChanged },
            summary = { "Time zone changed" },
            source = { src(EventSource.CLOCK) },
            matches = { _, e -> e is E.TimezoneChangedEvent },
        ),
        trigger<T.Shake>(
            Category.DEVICE, "📳", "Shake device", "You shake the device",
            create = { T.Shake },
            summary = { "Device shaken" },
            source = { src(EventSource.SENSORS, "shake") },
            matches = { _, e -> e is E.ShakeDetected },
        ),
        trigger<T.FlipDevice>(
            Category.DEVICE, "🙃", "Flip device", "The device is turned face down or face up",
            create = { T.FlipDevice() },
            summary = { "Device flipped ${if (it.faceDown) "face down" else "face up"}" },
            fields = listOf(toggle("Direction", "Face down", "Face up", { it.faceDown }, { s, v -> s.copy(faceDown = v) })),
            source = { src(EventSource.SENSORS, "orientation") },
            matches = { s, e -> e is E.FlipDetected && e.faceDown == s.faceDown },
        ),
        trigger<T.Proximity>(
            Category.DEVICE, "✋", "Proximity", "Something comes near or moves away from the top sensor",
            create = { T.Proximity() },
            summary = { if (it.near) "Proximity near" else "Proximity far" },
            fields = listOf(toggle("State", "Near", "Far", { it.near }, { s, v -> s.copy(near = v) })),
            source = { src(EventSource.SENSORS, "proximity") },
            matches = { s, e -> e is E.ProximityChanged && e.near == s.near },
        ),
        trigger<T.Geofence>(
            Category.CONNECTIVITY, "📍", "Geofence (location)", "Entering or leaving a place",
            create = { T.Geofence() },
            summary = {
                val where = it.name.ifBlank { "%.4f, %.4f".format(it.latitude, it.longitude) }
                "${if (it.entering) "Enter" else "Leave"} $where (${it.radiusMeters}m)"
            },
            fields = listOf(
                text("Place name (optional)", { it.name }, { s, v -> s.copy(name = v) }, magicText = false),
                location(
                    "Area",
                    lat = { it.latitude }, lng = { it.longitude }, radius = { it.radiusMeters },
                    setLatLng = { s, lat, lng -> s.copy(latitude = lat, longitude = lng) },
                    setRadius = { s, r -> s.copy(radiusMeters = r) },
                ),
                toggle("When", "Entering", "Leaving", { it.entering }, { s, v -> s.copy(entering = v) }),
                info("Uses GPS/network location while enabled. Grant background location for reliable detection."),
            ),
            requirements = { setOf(Requirement.LOCATION, Requirement.BACKGROUND_LOCATION) },
            source = { src(EventSource.LOCATION, it.areaKey) },
            matches = { s, e -> e is E.GeofenceTransition && e.areaKey == s.areaKey && e.entering == s.entering },
        ),
        trigger<T.StepCount>(
            Category.DEVICE, "👟", "Step count", "Every time you take a number of steps",
            create = { T.StepCount() },
            summary = { "Every ${it.steps} steps" },
            fields = listOf(number("Steps", 1..100000, { it.steps }, { s, v -> s.copy(steps = v) })),
            requirements = { setOf(Requirement.ACTIVITY_RECOGNITION) },
            source = { src(EventSource.SENSORS, "steps:${it.steps}") },
            matches = { s, e -> e is E.StepsReached && e.steps == s.steps },
        ),
        trigger<T.LightLevel>(
            Category.DEVICE, "💡", "Light level", "Ambient light rises above or falls below a level",
            create = { T.LightLevel() },
            summary = {
                when (it.direction) {
                    LevelDirection.FALLS_TO -> "Light falls below ${it.lux} lux"
                    LevelDirection.RISES_TO -> "Light rises above ${it.lux} lux"
                    LevelDirection.ANY_CHANGE -> "Light level changes"
                }
            },
            fields = listOf(
                choice("When", LevelDirection.entries, { it.direction }, { s, v -> s.copy(direction = v) }, directionLabel),
                number("Level", 0..2000, { it.lux }, { s, v -> s.copy(lux = v) }, "lux", visible = { it.direction != LevelDirection.ANY_CHANGE }),
            ),
            source = { src(EventSource.SENSORS, "light") },
            matches = { s, e -> e is E.LightChanged && crossed(s.direction, s.lux, e.previousLux, e.currentLux) },
        ),
        // endregion

        // region Date & time
        trigger<T.TimeOfDay>(
            Category.DATE_TIME, "⏰", "Time of day", "At a set time on chosen days",
            create = { T.TimeOfDay() },
            summary = { "At ${formatTime(it.hour, it.minute)} · ${formatDays(it.daysOfWeek)}" },
            fields = listOf(
                time("At", { it.hour }, { it.minute }, { s, h, m -> s.copy(hour = h, minute = m) }),
                multiChoice("Days", (1..7).toList(), ::dayName, { it.daysOfWeek }, { s, v -> s.copy(daysOfWeek = v) }),
            ),
            requirements = { setOf(Requirement.EXACT_ALARMS) },
            source = { src(EventSource.ALARM) },
            matches = { s, e -> e is E.TimeTick && e.hour == s.hour && e.minute == s.minute && e.dayOfWeek in s.daysOfWeek },
            schedule = ::nextTimeOfDay,
        ),
        trigger<T.RegularInterval>(
            Category.DATE_TIME, "🔂", "Regular interval", "Every N minutes",
            create = { T.RegularInterval() },
            summary = { "Every ${formatDuration(it.minutes * 60)}" },
            fields = listOf(
                number("Every", 1..1440, { it.minutes }, { s, v -> s.copy(minutes = v) }, "min"),
                info("Aligned to the clock (e.g. every 15 min fires at :00, :15, :30, :45). Android may delay very frequent alarms while the device sleeps."),
            ),
            requirements = { setOf(Requirement.EXACT_ALARMS) },
            source = { src(EventSource.ALARM) },
            matches = { s, e -> e is E.TimeTick && e.epochMinute % s.minutes.coerceAtLeast(1) == 0L },
            schedule = { s, now -> nextInterval(s, now) },
        ),
        trigger<T.SunriseSunset>(
            Category.DATE_TIME, "🌅", "Sunrise / sunset", "At sunrise or sunset for a location",
            create = { T.SunriseSunset() },
            summary = {
                val off = when {
                    it.offsetMinutes > 0 -> " +${it.offsetMinutes}m"
                    it.offsetMinutes < 0 -> " ${it.offsetMinutes}m"
                    else -> ""
                }
                "${if (it.event == dev.openmacro.core.model.SunEvent.SUNRISE) "Sunrise" else "Sunset"}$off"
            },
            fields = listOf(
                choice("Event", dev.openmacro.core.model.SunEvent.entries, { it.event }, { s, v -> s.copy(event = v) }),
                number("Offset", -120..120, { it.offsetMinutes }, { s, v -> s.copy(offsetMinutes = v) }, "min"),
                location(
                    "Location",
                    lat = { it.latitude }, lng = { it.longitude },
                    setLatLng = { s, lat, lng -> s.copy(latitude = lat, longitude = lng) },
                    showRadius = false,
                ),
            ),
            requirements = { setOf(Requirement.LOCATION) },
            source = { src(EventSource.ALARM) },
            schedule = { s, now ->
                dev.openmacro.core.model.SolarTime.next(now, s.latitude, s.longitude, s.event == dev.openmacro.core.model.SunEvent.SUNRISE, s.offsetMinutes)
            },
            matches = { s, e ->
                e is E.TimeTick && run {
                    val zone = java.time.ZoneId.systemDefault()
                    val tick = java.time.Instant.ofEpochSecond(e.epochMinute * 60).atZone(zone)
                    val sun = dev.openmacro.core.model.SolarTime.next(tick.minusMinutes(1), s.latitude, s.longitude, s.event == dev.openmacro.core.model.SunEvent.SUNRISE, s.offsetMinutes)
                    sun != null && sun.toEpochSecond() / 60 == e.epochMinute
                }
            },
        ),
        // endregion

        // region Call & SMS
        trigger<T.Call>(
            Category.PHONE_SMS, "📞", "Phone call", "Incoming, answered, missed, ended or outgoing call",
            create = { T.Call() },
            summary = {
                val base = when (it.event) {
                    CallEvent.INCOMING -> "Incoming call"
                    CallEvent.ANSWERED -> "Call answered"
                    CallEvent.ENDED -> "Call ended"
                    CallEvent.MISSED -> "Missed call"
                    CallEvent.OUTGOING -> "Outgoing call"
                }
                if (it.number.isNotBlank()) "$base · ${it.number.quoted()}" else base
            },
            fields = listOf(
                choice("Event", CallEvent.entries, { it.event }, { s, v -> s.copy(event = v) }),
                text("Number contains (optional)", { it.number }, { s, v -> s.copy(number = v) }, magicText = false),
            ),
            requirements = { setOf(Requirement.PHONE_STATE, Requirement.CALL_LOG) },
            source = { src(EventSource.CALLS) },
            matches = { s, e -> e is E.CallChanged && e.event == s.event && contains(s.number, e.number) },
        ),
        trigger<T.SmsReceived>(
            Category.PHONE_SMS, "✉️", "SMS received", "A text message arrives",
            create = { T.SmsReceived() },
            summary = {
                when {
                    it.from.isNotBlank() && it.contains.isNotBlank() -> "SMS from ${it.from.quoted()} containing ${it.contains.quoted()}"
                    it.from.isNotBlank() -> "SMS from ${it.from.quoted()}"
                    it.contains.isNotBlank() -> "SMS containing ${it.contains.quoted()}"
                    else -> "Any SMS received"
                }
            },
            fields = listOf(
                text("Sender contains (optional)", { it.from }, { s, v -> s.copy(from = v) }, magicText = false),
                text("Message contains (optional)", { it.contains }, { s, v -> s.copy(contains = v) }, magicText = false),
            ),
            requirements = { setOf(Requirement.RECEIVE_SMS) },
            source = { src(EventSource.SMS) },
            matches = { s, e -> e is E.SmsReceived && contains(s.from, e.from) && contains(s.contains, e.body) },
        ),
        // endregion

        // region Apps & intents
        trigger<T.AppInstallRemove>(
            Category.APPS, "📦", "App installed/removed", "An app is installed, removed or updated",
            create = { T.AppInstallRemove() },
            summary = {
                val verb = when (it.event) {
                    PackageEvent.INSTALLED -> "installed"
                    PackageEvent.REMOVED -> "removed"
                    PackageEvent.UPDATED -> "updated"
                }
                if (it.packageName.isBlank()) "Any app $verb" else "${it.packageName} $verb"
            },
            fields = listOf(
                choice("Event", PackageEvent.entries, { it.event }, { s, v -> s.copy(event = v) }),
                app("App (blank = any)", { it.packageName }, { s, v -> s.copy(packageName = v) }, allowAny = true),
            ),
            source = { src(EventSource.PACKAGES) },
            matches = { s, e -> e is E.PackageChanged && e.event == s.event && (s.packageName.isBlank() || s.packageName == e.packageName) },
        ),
        trigger<T.IntentReceived>(
            Category.APPS, "📨", "Intent received", "A custom broadcast intent action is received",
            create = { T.IntentReceived() },
            summary = { if (it.action.isBlank()) "Intent received" else "Intent: ${it.action}" },
            fields = listOf(
                text("Intent action", { it.action }, { s, v -> s.copy(action = v) }, magicText = false, hint = "com.example.MY_ACTION"),
                info("Send from another app or ADB: am broadcast -a <action>. Extras are available as {extra=key}."),
            ),
            source = { src(EventSource.CUSTOM_INTENT, it.action) },
            matches = { s, e -> e is E.IntentReceived && e.action == s.action },
        ),
        trigger<T.NotificationPosted>(
            Category.NOTIFICATIONS, "🔔", "Notification received", "Another app posts a notification",
            create = { T.NotificationPosted() },
            summary = {
                when {
                    it.packageName.isNotBlank() && it.textContains.isNotBlank() -> "${it.packageName} notification containing ${it.textContains.quoted()}"
                    it.packageName.isNotBlank() -> "${it.packageName} notification"
                    it.textContains.isNotBlank() -> "Notification containing ${it.textContains.quoted()}"
                    else -> "Any notification"
                }
            },
            fields = listOf(
                app("App (blank = any)", { it.packageName }, { s, v -> s.copy(packageName = v) }, allowAny = true),
                text("Text contains (optional)", { it.textContains }, { s, v -> s.copy(textContains = v) }, magicText = false),
            ),
            requirements = { setOf(Requirement.NOTIFICATION_ACCESS) },
            source = { src(EventSource.NOTIFICATIONS) },
            matches = { s, e ->
                e is E.NotificationPosted &&
                    (s.packageName.isBlank() || s.packageName == e.packageName) &&
                    (s.textContains.isBlank() || "${e.title} ${e.text}".contains(s.textContains.trim(), ignoreCase = true))
            },
        ),
        trigger<T.WebHook>(
            Category.NETWORK, "🪝", "Webhook (HTTP)", "An HTTP request hits the built-in server at /<token>",
            create = { T.WebHook() },
            summary = { "Webhook /${it.token.ifBlank { "(token)" }} (${it.method})" },
            fields = listOf(
                text("Token (URL path)", { it.token }, { s, v -> s.copy(token = v) }, magicText = false, hint = "e.g. lights-on"),
                choice("Method", HttpTriggerMethod.entries, { it.method }, { s, v -> s.copy(method = v) }),
                info("Call http://<device-ip>:8585/<token> on your Wi-Fi. The request body is available as {webhook_body}."),
            ),
            source = { src(EventSource.WEBHOOK) },
            matches = { s, e -> e is E.WebhookReceived && e.token == s.token && (s.method == dev.openmacro.core.model.HttpTriggerMethod.ANY || e.method.equals(s.method.name, ignoreCase = true)) },
            needsNetwork = true,
        ),
        trigger<T.AppForeground>(
            Category.APPS, "🗔", "App in foreground", "An app comes to the foreground",
            create = { T.AppForeground() },
            summary = { if (it.packageName.isBlank()) "Any app opened" else "${it.packageName} opened" },
            fields = listOf(app("App (blank = any)", { it.packageName }, { s, v -> s.copy(packageName = v) }, allowAny = true)),
            requirements = { setOf(Requirement.ACCESSIBILITY) },
            source = { src(EventSource.ACCESSIBILITY) },
            matches = { s, e -> e is E.AppForegroundChanged && (s.packageName.isBlank() || s.packageName == e.packageName) },
        ),
        // endregion

        // region OpenMacro
        trigger<T.Manual>(
            Category.MACRO, "👆", "Manual / no trigger", "Only runs when started by hand or by another macro",
            create = { T.Manual },
            summary = { "Manual only" },
            source = { src(EventSource.INTERNAL) },
            matches = { _, _ -> false },
        ),
        trigger<T.QuickTile>(
            Category.MACRO, "🔲", "Quick Settings tile", "You tap OpenMacro's Quick Settings tile",
            create = { T.QuickTile },
            summary = { "Quick Settings tile tapped" },
            source = { src(EventSource.INTERNAL) },
            matches = { _, e -> e is E.QuickTileTapped },
        ),
        trigger<T.BatteryFull>(
            Category.BATTERY_POWER, "🔋", "Battery full", "The battery reaches 100% while charging",
            create = { T.BatteryFull },
            summary = { "Battery full" },
            source = { src(EventSource.BATTERY) },
            matches = { _, e -> e is E.BatteryFull },
        ),
        trigger<T.MacroFinished>(
            Category.MACRO, "🏁", "Macro finished", "Another macro finishes running",
            create = { T.MacroFinished() },
            summary = { "When ${it.macro.display()} finishes" },
            fields = listOf(macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) })),
            source = { src(EventSource.INTERNAL) },
            matches = { s, e -> e is E.MacroFinished && e.macroId == s.macro.id },
        ),
        trigger<T.MacroEnabledChanged>(
            Category.MACRO, "🎚️", "Macro enabled/disabled", "Another macro is enabled or disabled",
            create = { T.MacroEnabledChanged() },
            summary = { "When ${it.macro.display()} is ${if (it.enabled) "enabled" else "disabled"}" },
            fields = listOf(
                macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) }),
                toggle("State", "Enabled", "Disabled", { it.enabled }, { s, v -> s.copy(enabled = v) }),
            ),
            source = { src(EventSource.INTERNAL) },
            matches = { s, e -> e is E.MacroEnabledChanged && e.macroId == s.macro.id && e.enabled == s.enabled },
        ),
        trigger<T.VariableChanged>(
            Category.VARIABLES, "🔢", "Variable changed", "A global variable changes value",
            create = { T.VariableChanged() },
            summary = {
                when {
                    it.name.isBlank() -> "Any global variable changes"
                    it.value.isBlank() -> "${it.name} changes"
                    else -> "${it.name} becomes ${it.value.quoted()}"
                }
            },
            fields = listOf(
                text("Variable name", { it.name }, { s, v -> s.copy(name = v) }, magicText = false),
                text("Equals value (optional)", { it.value }, { s, v -> s.copy(value = v) }),
            ),
            source = { src(EventSource.INTERNAL) },
            matches = { s, e -> e is E.VariableChanged && (s.name.isBlank() || e.name == s.name) && (s.value.isBlank() || e.value == s.value) },
        ),
        // endregion
    )
}

private fun BluetoothEvent.isDevice(): Boolean =
    this == BluetoothEvent.DEVICE_CONNECTED || this == BluetoothEvent.DEVICE_DISCONNECTED