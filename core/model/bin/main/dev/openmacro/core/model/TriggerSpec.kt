package dev.openmacro.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration of an event that can start a macro. Serialised with discriminator `"type"`;
 * serial names are a storage contract and must never change.
 */
@Serializable
sealed interface TriggerSpec {

    /** Placeholder for a trigger this version can no longer read. Never fires. */
    @Serializable @SerialName("unsupported")
    data class Unsupported(val originalType: String = "", val raw: String = "") : TriggerSpec

    @Serializable @SerialName("battery_level")
    data class BatteryLevel(val threshold: Int = 20, val direction: LevelDirection = LevelDirection.FALLS_TO) : TriggerSpec

    @Serializable @SerialName("battery_temperature")
    data class BatteryTemperature(val celsius: Int = 40, val direction: LevelDirection = LevelDirection.RISES_TO) : TriggerSpec

    @Serializable @SerialName("power_connection")
    data class PowerConnection(val connected: Boolean = true) : TriggerSpec

    @Serializable @SerialName("battery_saver")
    data class BatterySaver(val on: Boolean = true) : TriggerSpec

    /** [ssid] filters by network name on connect (case-insensitive, blank = any). */
    @Serializable @SerialName("wifi_state")
    data class WifiState(val connected: Boolean = true, val ssid: String = "") : TriggerSpec

    /** [deviceNameFilter] only applies to device events; blank matches any device. */
    @Serializable @SerialName("bluetooth_state")
    data class BluetoothState(val event: BluetoothEvent = BluetoothEvent.ADAPTER_ON, val deviceNameFilter: String = "") : TriggerSpec

    @Serializable @SerialName("airplane_mode")
    data class AirplaneMode(val on: Boolean = true) : TriggerSpec

    @Serializable @SerialName("nfc_state")
    data class NfcState(val on: Boolean = true) : TriggerSpec

    @Serializable @SerialName("sim_state")
    data object SimStateChanged : TriggerSpec

    @Serializable @SerialName("location_services")
    data class LocationServices(val enabled: Boolean = true) : TriggerSpec

    @Serializable @SerialName("screen_state")
    data class ScreenState(val on: Boolean = true) : TriggerSpec

    @Serializable @SerialName("device_unlocked")
    data object DeviceUnlocked : TriggerSpec

    @Serializable @SerialName("dark_theme")
    data class DarkTheme(val on: Boolean = true) : TriggerSpec

    @Serializable @SerialName("headphones")
    data class Headphones(val connected: Boolean = true) : TriggerSpec

    @Serializable @SerialName("usb")
    data class Usb(val connected: Boolean = true) : TriggerSpec

    @Serializable @SerialName("dock")
    data class Dock(val docked: Boolean = true) : TriggerSpec

    @Serializable @SerialName("doze")
    data class DozeMode(val on: Boolean = true) : TriggerSpec

    @Serializable @SerialName("ringer_mode_changed")
    data class RingerModeChanged(val mode: RingerMode = RingerMode.SILENT) : TriggerSpec

    @Serializable @SerialName("boot")
    data object Boot : TriggerSpec

    @Serializable @SerialName("storage_low")
    data class StorageLow(val low: Boolean = true) : TriggerSpec

    @Serializable @SerialName("timezone_changed")
    data object TimezoneChanged : TriggerSpec

    /** [daysOfWeek] uses ISO numbering (1 = Monday … 7 = Sunday). */
    @Serializable @SerialName("time_of_day")
    data class TimeOfDay(val hour: Int = 8, val minute: Int = 0, val daysOfWeek: Set<Int> = ALL_DAYS) : TriggerSpec

    /** Fires every [minutes], aligned to the Unix epoch minute so no state is needed. */
    @Serializable @SerialName("regular_interval")
    data class RegularInterval(val minutes: Int = 30) : TriggerSpec

    /** Fires at sunrise or sunset for a location, with an optional minute offset (needs location). */
    @Serializable @SerialName("sun")
    data class SunriseSunset(
        val event: SunEvent = SunEvent.SUNRISE,
        val offsetMinutes: Int = 0,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
    ) : TriggerSpec

    /** [packageName] blank matches any app. */
    @Serializable @SerialName("app_install")
    data class AppInstallRemove(val event: PackageEvent = PackageEvent.INSTALLED, val packageName: String = "") : TriggerSpec

    @Serializable @SerialName("intent_received")
    data class IntentReceived(val action: String = "") : TriggerSpec

    /** Fires when the built-in HTTP server receives a request at /<token> (online flavor). */
    @Serializable @SerialName("webhook")
    data class WebHook(val token: String = "", val method: HttpTriggerMethod = HttpTriggerMethod.ANY) : TriggerSpec

    /** An app comes to the foreground (needs the accessibility service). Blank matches any app. */
    @Serializable @SerialName("app_foreground")
    data class AppForeground(val packageName: String = "") : TriggerSpec

    /** A notification is posted (needs notification access). Filters are case-insensitive substrings. */
    @Serializable @SerialName("notification_posted")
    data class NotificationPosted(val packageName: String = "", val textContains: String = "") : TriggerSpec

    /** Filters are case-insensitive substrings; blank matches anything. */
    @Serializable @SerialName("sms_received")
    data class SmsReceived(val from: String = "", val contains: String = "") : TriggerSpec

    @Serializable @SerialName("call")
    data class Call(val event: CallEvent = CallEvent.INCOMING, val number: String = "") : TriggerSpec

    /** Never fires by itself; the macro runs only when started manually or by another macro. */
    @Serializable @SerialName("empty")
    data object Manual : TriggerSpec

    /** Fires when the user taps OpenMacro's Quick Settings tile. */
    @Serializable @SerialName("quick_tile")
    data object QuickTile : TriggerSpec

    /** Fires when the device's charging battery reaches full. */
    @Serializable @SerialName("battery_full")
    data object BatteryFull : TriggerSpec

    @Serializable @SerialName("macro_finished")
    data class MacroFinished(val macro: MacroRef = MacroRef()) : TriggerSpec

    @Serializable @SerialName("macro_enabled")
    data class MacroEnabledChanged(val macro: MacroRef = MacroRef(), val enabled: Boolean = true) : TriggerSpec

    /** A global variable changed; blank [value] matches any change. */
    @Serializable @SerialName("variable_changed")
    data class VariableChanged(val name: String = "", val value: String = "") : TriggerSpec

    /** Fires when a file or folder is created, modified or deleted at [path] (needs file access). */
    @Serializable @SerialName("file_changed")
    data class FileChanged(val path: String = "/sdcard/Download", val event: FileEvent = FileEvent.MODIFIED) : TriggerSpec

    @Serializable @SerialName("shake")
    data object Shake : TriggerSpec

    @Serializable @SerialName("flip_device")
    data class FlipDevice(val faceDown: Boolean = true) : TriggerSpec

    @Serializable @SerialName("proximity")
    data class Proximity(val near: Boolean = true) : TriggerSpec

    /** Fires every time [steps] more steps are taken (needs physical-activity permission). */
    @Serializable @SerialName("step_count")
    data class StepCount(val steps: Int = 100) : TriggerSpec

    @Serializable @SerialName("light_level")
    data class LightLevel(val lux: Int = 50, val direction: LevelDirection = LevelDirection.FALLS_TO) : TriggerSpec

    /** Fires on entering or leaving a circular area (needs location). */
    @Serializable @SerialName("geofence")
    data class Geofence(
        val name: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val radiusMeters: Int = 150,
        val entering: Boolean = true,
    ) : TriggerSpec {
        /** Stable key identifying the area, independent of enter/leave direction. */
        val areaKey: String get() = "$latitude,$longitude,$radiusMeters"
    }

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
    }
}

enum class LevelDirection { FALLS_TO, RISES_TO, ANY_CHANGE }

enum class BluetoothEvent { ADAPTER_ON, ADAPTER_OFF, DEVICE_CONNECTED, DEVICE_DISCONNECTED }

enum class PackageEvent { INSTALLED, REMOVED, UPDATED }

enum class CallEvent { INCOMING, ANSWERED, ENDED, MISSED, OUTGOING }

enum class FileEvent { CREATED, MODIFIED, DELETED }

enum class HttpTriggerMethod { ANY, GET, POST }

enum class SunEvent { SUNRISE, SUNSET }
