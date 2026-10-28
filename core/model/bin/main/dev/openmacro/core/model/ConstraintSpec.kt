package dev.openmacro.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A condition evaluated at run time. Serial names are a storage contract and must never change. */
@Serializable
sealed interface ConstraintSpec {

    /** Placeholder for a constraint this version can no longer read. Never satisfied. */
    @Serializable @SerialName("unsupported")
    data class Unsupported(val originalType: String = "", val raw: String = "") : ConstraintSpec

    /** Start inclusive, end exclusive; wraps past midnight when end < start; equal = all day. */
    @Serializable @SerialName("time_window")
    data class TimeWindow(val startHour: Int = 22, val startMinute: Int = 0, val endHour: Int = 7, val endMinute: Int = 0) : ConstraintSpec

    @Serializable @SerialName("day_of_week")
    data class DayOfWeek(val days: Set<Int> = (1..5).toSet()) : ConstraintSpec

    @Serializable @SerialName("day_of_month")
    data class DayOfMonth(val days: Set<Int> = setOf(1)) : ConstraintSpec

    @Serializable @SerialName("month_of_year")
    data class MonthOfYear(val months: Set<Int> = setOf(1)) : ConstraintSpec

    /** Satisfied during daytime (or night) for a location (needs location set). */
    @Serializable @SerialName("daytime")
    data class Daytime(val day: Boolean = true, val latitude: Double = 0.0, val longitude: Double = 0.0) : ConstraintSpec

    @Serializable @SerialName("device_lock")
    data class DeviceLock(val locked: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("screen")
    data class Screen(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("orientation")
    data class Orientation(val portrait: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("battery_threshold")
    data class BatteryThreshold(val comparison: Comparison = Comparison.AT_LEAST, val percent: Int = 50) : ConstraintSpec

    @Serializable @SerialName("battery_temperature")
    data class BatteryTemperature(val comparison: Comparison = Comparison.AT_MOST, val celsius: Int = 40) : ConstraintSpec

    @Serializable @SerialName("power")
    data class Power(val connected: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("battery_saver")
    data class BatterySaver(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("charging_method")
    data class ChargingMethod(val source: ChargingSource = ChargingSource.AC) : ConstraintSpec

    @Serializable @SerialName("doze")
    data class Doze(val idle: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("wifi")
    data class Wifi(val state: WifiCondition = WifiCondition.CONNECTED) : ConstraintSpec

    @Serializable @SerialName("bluetooth")
    data class Bluetooth(val enabled: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("airplane_mode")
    data class AirplaneMode(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("roaming")
    data class Roaming(val roaming: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("vpn")
    data class Vpn(val active: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("nfc")
    data class Nfc(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("location_enabled")
    data class LocationEnabled(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("headphones")
    data class Headphones(val connected: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("auto_rotate")
    data class AutoRotate(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("brightness")
    data class Brightness(val comparison: Comparison = Comparison.AT_MOST, val percent: Int = 50) : ConstraintSpec

    @Serializable @SerialName("auto_brightness")
    data class AutoBrightness(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("dark_theme")
    data class DarkTheme(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("torch")
    data class Torch(val on: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("ringer_mode")
    data class RingerModeIs(val mode: RingerMode = RingerMode.NORMAL) : ConstraintSpec

    @Serializable @SerialName("volume_level")
    data class VolumeLevel(val stream: AudioStream = AudioStream.MEDIA, val comparison: Comparison = Comparison.AT_LEAST, val percent: Int = 50) : ConstraintSpec

    @Serializable @SerialName("music_active")
    data class MusicActive(val active: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("call_state")
    data class Call(val state: CallState = CallState.IN_CALL) : ConstraintSpec

    /** Satisfied when a given app is (or isn't) in the foreground (needs accessibility). */
    @Serializable @SerialName("active_app")
    data class ActiveApp(val packageName: String = "", val present: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("macro_enabled")
    data class MacroEnabled(val macro: MacroRef = MacroRef(), val enabled: Boolean = true) : ConstraintSpec

    @Serializable @SerialName("macro_running")
    data class MacroRunning(val macro: MacroRef = MacroRef(), val running: Boolean = true) : ConstraintSpec

    /** Minutes since this macro last ran (never-run counts as infinitely long ago). */
    @Serializable @SerialName("last_run")
    data class LastRun(val comparison: Comparison = Comparison.AT_LEAST, val minutes: Int = 10) : ConstraintSpec

    @Serializable @SerialName("time_since_boot")
    data class TimeSinceBoot(val comparison: Comparison = Comparison.AT_LEAST, val minutes: Int = 5) : ConstraintSpec

    /** [value] supports magic text; numeric comparisons apply when both sides are numbers. */
    @Serializable @SerialName("variable_compare")
    data class VariableCompare(val variable: VarRef = VarRef(), val op: CompareOp = CompareOp.EQUALS, val value: String = "") : ConstraintSpec

    /** Text is (or isn't) visible on screen now (needs the accessibility service). */
    @Serializable @SerialName("screen_contains")
    data class ScreenContains(val text: String = "", val present: Boolean = true) : ConstraintSpec

    /** A matching notification is (or isn't) currently shown (needs notification access). */
    @Serializable @SerialName("notification_present")
    data class NotificationPresent(val packageName: String = "", val textContains: String = "", val present: Boolean = true) : ConstraintSpec

    /** Combines nested constraints; NOT negates the AND of its children. */
    @Serializable @SerialName("logic")
    data class Logic(val op: LogicOp = LogicOp.AND, val constraints: List<ConstraintSpec> = emptyList()) : ConstraintSpec
}

enum class Comparison { AT_LEAST, AT_MOST }

enum class WifiCondition { ENABLED, DISABLED, CONNECTED, NOT_CONNECTED }

enum class CompareOp { EQUALS, NOT_EQUALS, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, CONTAINS, NOT_CONTAINS, MATCHES_REGEX }

enum class LogicOp { AND, OR, NOT, XOR }
