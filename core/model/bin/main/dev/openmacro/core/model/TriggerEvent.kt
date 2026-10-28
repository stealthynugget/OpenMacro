package dev.openmacro.core.model

/** A normalised event, already edge-detected by its source. */
sealed interface TriggerEvent {
    data class BatteryLevelChanged(val previous: Int, val current: Int) : TriggerEvent
    data class BatteryTemperatureChanged(val previous: Int, val current: Int) : TriggerEvent
    data class PowerConnectionChanged(val connected: Boolean) : TriggerEvent
    data class WifiChanged(val connected: Boolean, val ssid: String? = null) : TriggerEvent
    data class BluetoothAdapterChanged(val on: Boolean) : TriggerEvent
    data class BluetoothDeviceChanged(val connected: Boolean, val deviceName: String?) : TriggerEvent
    data class ScreenChanged(val on: Boolean) : TriggerEvent
    data class HeadphonesChanged(val connected: Boolean) : TriggerEvent
    data class UsbChanged(val connected: Boolean) : TriggerEvent
    data class AirplaneModeChanged(val on: Boolean) : TriggerEvent
    data object DeviceUnlocked : TriggerEvent
    data class BatterySaverChanged(val on: Boolean) : TriggerEvent
    data class DarkThemeChanged(val on: Boolean) : TriggerEvent
    data class RingerModeChanged(val mode: RingerMode) : TriggerEvent
    data class LocationServicesChanged(val enabled: Boolean) : TriggerEvent
    data class NfcChanged(val on: Boolean) : TriggerEvent
    data class DockChanged(val docked: Boolean) : TriggerEvent
    data class DozeChanged(val on: Boolean) : TriggerEvent
    data class PackageChanged(val event: PackageEvent, val packageName: String) : TriggerEvent
    data class AppForegroundChanged(val packageName: String) : TriggerEvent
    data class NotificationPosted(val packageName: String, val title: String, val text: String) : TriggerEvent
    data class NotificationRemovedEvent(val packageName: String) : TriggerEvent
    data class IntentReceived(val action: String, val extras: Map<String, String>) : TriggerEvent
    data class WebhookReceived(val token: String, val method: String, val body: String) : TriggerEvent
    data class SmsReceived(val from: String, val body: String) : TriggerEvent
    data class CallChanged(val event: CallEvent, val number: String) : TriggerEvent
    data object Boot : TriggerEvent
    data class StorageChanged(val low: Boolean) : TriggerEvent
    data object TimezoneChangedEvent : TriggerEvent
    data object QuickTileTapped : TriggerEvent
    data object BatteryFull : TriggerEvent
    data class MacroFinished(val macroId: Long) : TriggerEvent
    data class MacroEnabledChanged(val macroId: Long, val enabled: Boolean) : TriggerEvent
    data class VariableChanged(val name: String, val value: String) : TriggerEvent
    data object ShakeDetected : TriggerEvent
    data class FlipDetected(val faceDown: Boolean) : TriggerEvent
    data class ProximityChanged(val near: Boolean) : TriggerEvent
    data class LightChanged(val previousLux: Int, val currentLux: Int) : TriggerEvent
    data class StepsReached(val steps: Int) : TriggerEvent
    data class GeofenceTransition(val areaKey: String, val entering: Boolean) : TriggerEvent
    data class FileChangedEvent(val path: String, val event: FileEvent) : TriggerEvent

    /** A scheduled wall-clock minute; [dayOfWeek] is ISO (1 = Monday). */
    data class TimeTick(val hour: Int, val minute: Int, val dayOfWeek: Int, val epochMinute: Long) : TriggerEvent
}

/** The system subscription that produces events for a trigger type. */
enum class EventSource {
    BATTERY, POWER, WIFI, BLUETOOTH, SCREEN, HEADPHONES, AIRPLANE, UNLOCK, BATTERY_SAVER, DARK_THEME,
    RINGER, LOCATION_MODE, NFC, DOCK, PACKAGES, CUSTOM_INTENT, SMS, CALLS,

    /** Pushed by the accessibility service when connected; not a registered receiver. */
    ACCESSIBILITY,

    /** Pushed by the notification-listener service when connected; not a registered receiver. */
    NOTIFICATIONS,

    /** Hardware sensors (accelerometer, proximity, light); the SourceKey param selects which. */
    SENSORS,

    /** Location updates for geofences; the SourceKey param identifies the area. */
    LOCATION,

    /** FileObserver on a path; the SourceKey param is the watched path. */
    FILE,

    /** The built-in HTTP server (one shared instance for all webhook triggers). */
    WEBHOOK,

    /** Device idle (doze) mode changes. */
    DOZE,

    /** Low/OK storage broadcasts. */
    STORAGE,

    /** Time-zone change broadcast. */
    CLOCK,

    /** Driven by the engine's single exact alarm. */
    ALARM,

    /** Emitted by the engine itself (boot, macro and variable events); no subscription needed. */
    INTERNAL,
}

/** A concrete subscription: a source plus an optional parameter (e.g. a custom intent action). */
data class SourceKey(val source: EventSource, val param: String = "")
