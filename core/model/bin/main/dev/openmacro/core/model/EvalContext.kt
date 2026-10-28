package dev.openmacro.core.model

import java.time.ZonedDateTime

enum class CallState { IDLE, RINGING, IN_CALL }

enum class ChargingSource { UNPLUGGED, AC, USB, WIRELESS }

/** A currently-shown notification, as seen by the notification-listener service. */
data class NotificationInfo(val packageName: String, val title: String, val text: String)

/** On-demand device queries used by constraints. Implemented on Android by the engine. */
interface DeviceProbe {
    fun batteryPercent(): Int
    fun batteryTemperatureC(): Int
    fun isPowerConnected(): Boolean
    fun isDeviceLocked(): Boolean
    fun isScreenOn(): Boolean
    fun isHeadphonesConnected(): Boolean
    fun isWifiEnabled(): Boolean
    fun isWifiConnected(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun isAirplaneMode(): Boolean
    fun isAutoRotate(): Boolean
    fun isBatterySaver(): Boolean
    fun isDarkTheme(): Boolean
    fun ringerMode(): RingerMode
    fun volumePercent(stream: AudioStream): Int
    fun isMusicActive(): Boolean
    fun callState(): CallState
    fun isRoaming(): Boolean
    fun isVpnActive(): Boolean
    fun isLocationEnabled(): Boolean
    fun isTorchOn(): Boolean
    fun isNfcEnabled(): Boolean
    fun isPortrait(): Boolean
    fun uptimeMillis(): Long

    /** All text currently visible on screen, or empty if the accessibility service is off. */
    fun screenText(): String

    /** Notifications currently shown, or empty if notification access is off. */
    fun activeNotifications(): List<NotificationInfo>

    /** How the device is charging right now. */
    fun chargingSource(): ChargingSource

    /** True while the device is in deep idle (doze). */
    fun isDeviceIdle(): Boolean

    /** Screen brightness as a percentage (0–100). */
    fun brightnessPercent(): Int

    /** True when automatic brightness is enabled. */
    fun isAutoBrightness(): Boolean

    /** Package of the current foreground app, or null if the accessibility service is off. */
    fun foregroundApp(): String?
}

/** Everything a constraint may consult while being evaluated. */
interface EvalContext {
    val device: DeviceProbe
    val currentMacroId: Long
    fun now(): ZonedDateTime
    fun variable(ref: VarRef): Variable?
    fun render(text: String): String
    fun isMacroEnabled(id: Long): Boolean
    fun isMacroRunning(id: Long): Boolean
    fun lastRunAt(id: Long): Long?
}
