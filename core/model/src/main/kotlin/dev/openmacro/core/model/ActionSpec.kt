package dev.openmacro.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Something a macro does. Actions run in list order, one at a time; control-flow markers
 * (If/Loop/…) structure the flat list. Serial names are a storage contract and must never change.
 */
@Serializable
sealed interface ActionSpec {

    /** Placeholder for an action this version can no longer read (e.g. one that was removed). Kept so the macro still loads. */
    @Serializable @SerialName("unsupported")
    data class Unsupported(val originalType: String = "", val raw: String = "") : ActionSpec

    // region Control flow
    @Serializable @SerialName("if")
    data class If(val conditions: List<ConstraintSpec> = emptyList(), val matchAll: Boolean = true) : ActionSpec

    @Serializable @SerialName("else_if")
    data class ElseIf(val conditions: List<ConstraintSpec> = emptyList(), val matchAll: Boolean = true) : ActionSpec

    @Serializable @SerialName("else")
    data object Else : ActionSpec

    @Serializable @SerialName("end_if")
    data object EndIf : ActionSpec

    /** COUNT repeats [count] times; WHILE repeats while the conditions hold (checked before each pass). */
    @Serializable @SerialName("loop")
    data class Loop(
        val mode: LoopMode = LoopMode.COUNT,
        val count: Int = 3,
        val conditions: List<ConstraintSpec> = emptyList(),
        val matchAll: Boolean = true,
    ) : ActionSpec

    @Serializable @SerialName("end_loop")
    data object EndLoop : ActionSpec

    @Serializable @SerialName("break_loop")
    data object BreakLoop : ActionSpec

    @Serializable @SerialName("continue_loop")
    data object ContinueLoop : ActionSpec

    @Serializable @SerialName("stop_macro")
    data object StopMacro : ActionSpec

    /** Pauses [seconds]; if [maxSeconds] exceeds it, waits a random time in the range. */
    @Serializable @SerialName("wait")
    data class Wait(val seconds: Int = 1, val maxSeconds: Int = 0) : ActionSpec
    // endregion

    // region Variables, text & logging
    /** [value] supports magic text; numeric types also evaluate arithmetic, booleans accept "toggle". */
    @Serializable @SerialName("set_variable")
    data class SetVariable(
        val variable: VarRef = VarRef(),
        // Serialized key differs from "type" to avoid colliding with the polymorphic discriminator.
        @SerialName("var_type") val type: VarType = VarType.STRING,
        val value: String = "",
    ) : ActionSpec

    @Serializable @SerialName("clear_variable")
    data class ClearVariable(val variable: VarRef = VarRef()) : ActionSpec

    /** Adds [amount] (may be negative, supports expressions) to a numeric variable. */
    @Serializable @SerialName("increment_variable")
    data class IncrementVariable(val variable: VarRef = VarRef(), val amount: String = "1") : ActionSpec

    /** Appends text to a string variable, with an optional separator when it's non-empty. */
    @Serializable @SerialName("append_variable")
    data class AppendVariable(val variable: VarRef = VarRef(), val text: String = "", val separator: String = "") : ActionSpec

    @Serializable @SerialName("text_manipulation")
    data class TextManipulation(
        val input: String = "",
        val op: TextOp = TextOp.UPPERCASE,
        val arg1: String = "",
        val arg2: String = "",
        val target: VarRef = VarRef(),
    ) : ActionSpec

    @Serializable @SerialName("log")
    data class Log(val text: String = "") : ActionSpec

    @Serializable @SerialName("set_clipboard")
    data class SetClipboard(val text: String = "") : ActionSpec

    @Serializable @SerialName("clear_clipboard")
    data object ClearClipboard : ActionSpec

    /** Base64 / URL encode or decode text into a variable. */
    @Serializable @SerialName("encode_text")
    data class EncodeText(val mode: EncodeMode = EncodeMode.BASE64_ENCODE, val input: String = "", val target: VarRef = VarRef()) : ActionSpec

    /** Hash text with a digest algorithm into a variable (hex). */
    @Serializable @SerialName("hash_text")
    data class HashText(val algorithm: HashAlgorithm = HashAlgorithm.SHA256, val input: String = "", val target: VarRef = VarRef()) : ActionSpec

    /** Generate a UUID, random number in [min,max], or current epoch millis into a variable. */
    @Serializable @SerialName("generate_value")
    data class GenerateValue(val kind: GenerateKind = GenerateKind.UUID, val min: Int = 1, val max: Int = 100, val target: VarRef = VarRef()) : ActionSpec

    /** Extract a value from JSON by dot path (e.g. "data.items.0.name") into a variable. */
    @Serializable @SerialName("parse_json")
    data class ParseJson(val json: String = "", val path: String = "", val target: VarRef = VarRef()) : ActionSpec

    /**
     * Formats a time into a variable using a date pattern. [source] blank or "now" uses the current
     * time; otherwise it's parsed as epoch millis (or seconds).
     */
    @Serializable @SerialName("format_datetime")
    data class FormatDateTime(val source: String = "now", val pattern: String = "yyyy-MM-dd HH:mm:ss", val target: VarRef = VarRef()) : ActionSpec
    // endregion

    // region Device
    @Serializable @SerialName("flashlight")
    data class Flashlight(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    @Serializable @SerialName("vibrate")
    data class Vibrate(val pattern: VibrationPattern = VibrationPattern.SHORT) : ActionSpec

    /** Strobe the flashlight [times] times with [intervalMs] on/off periods. */
    @Serializable @SerialName("flashlight_strobe")
    data class FlashlightStrobe(val times: Int = 10, val intervalMs: Int = 200) : ActionSpec

    /** Capture a still photo from a camera to a file; optionally store the saved path. */
    @Serializable @SerialName("take_photo")
    data class TakePhoto(
        val frontCamera: Boolean = false,
        val destination: String = "/sdcard/Pictures/OpenMacro",
        val savedPath: VarRef = VarRef(),
    ) : ActionSpec

    /** Set the default ringtone / notification / alarm sound from a picked URI. */
    @Serializable @SerialName("set_ringtone")
    data class SetRingtone(
        // Serialized key differs from "type" to avoid colliding with the polymorphic discriminator.
        @SerialName("sound_type") val type: RingtoneType = RingtoneType.RINGTONE,
        val uri: String = "",
    ) : ActionSpec

    @Serializable @SerialName("keep_awake")
    data class KeepAwake(val on: Boolean = true, val minutes: Int = 10) : ActionSpec

    @Serializable @SerialName("screen_on")
    data object ScreenOn : ActionSpec

    @Serializable @SerialName("car_mode")
    data class CarMode(val on: Boolean = true) : ActionSpec
    // endregion

    // region Audio & media
    @Serializable @SerialName("set_volume")
    data class SetVolume(val stream: AudioStream = AudioStream.MEDIA, val percent: Int = 50) : ActionSpec

    @Serializable @SerialName("volume_step")
    data class VolumeStep(val stream: AudioStream = AudioStream.MEDIA, val direction: VolumeDirection = VolumeDirection.UP, val showUi: Boolean = false) : ActionSpec

    @Serializable @SerialName("ringer_mode")
    data class SetRingerMode(val mode: RingerMode = RingerMode.VIBRATE) : ActionSpec

    @Serializable @SerialName("do_not_disturb")
    data class DoNotDisturb(val mode: DndMode = DndMode.PRIORITY) : ActionSpec

    @Serializable @SerialName("media_control")
    data class MediaControl(val command: MediaCommand = MediaCommand.PLAY_PAUSE) : ActionSpec

    /** [uri] is only used for [SoundChoice.CUSTOM]. */
    @Serializable @SerialName("play_sound")
    data class PlaySound(val sound: SoundChoice = SoundChoice.NOTIFICATION, val uri: String = "", val waitToFinish: Boolean = false) : ActionSpec

    @Serializable @SerialName("speak_text")
    data class SpeakText(val text: String = "", val waitToFinish: Boolean = true) : ActionSpec

    @Serializable @SerialName("say_time")
    data object SayTime : ActionSpec

    @Serializable @SerialName("stop_speaking")
    data object StopSpeaking : ActionSpec

    @Serializable @SerialName("mute_microphone")
    data class MuteMicrophone(val mode: SwitchMode = SwitchMode.ON) : ActionSpec

    @Serializable @SerialName("speakerphone")
    data class Speakerphone(val on: Boolean = true) : ActionSpec
    // endregion

    // region Screen & display
    @Serializable @SerialName("set_brightness")
    data class SetBrightness(val auto: Boolean = false, val percent: Int = 50) : ActionSpec

    @Serializable @SerialName("screen_timeout")
    data class ScreenTimeout(val seconds: Int = 60) : ActionSpec

    @Serializable @SerialName("auto_rotate")
    data class SetAutoRotate(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    /** Force a screen orientation (AUTO re-enables auto-rotate). */
    @Serializable @SerialName("screen_rotation")
    data class SetScreenRotation(val orientation: ScreenRotation = ScreenRotation.PORTRAIT) : ActionSpec

    @Serializable @SerialName("font_scale")
    data class FontScale(val percent: Int = 100) : ActionSpec

    @Serializable @SerialName("set_wallpaper")
    data class SetWallpaper(val uri: String = "", val target: WallpaperTarget = WallpaperTarget.BOTH) : ActionSpec
    // endregion

    // region Notifications
    @Serializable @SerialName("notification")
    data class ShowNotification(val title: String = "OpenMacro", val text: String = "") : ActionSpec

    @Serializable @SerialName("toast")
    data class ShowToast(val text: String = "", val long: Boolean = false) : ActionSpec
    // endregion

    // region Apps & intents
    @Serializable @SerialName("launch_app")
    data class LaunchApp(val packageName: String = "") : ActionSpec

    @Serializable @SerialName("open_web_page")
    data class OpenWebPage(val url: String = "https://") : ActionSpec

    @Serializable @SerialName("launch_home")
    data object LaunchHome : ActionSpec

    /** Open maps / navigation to a place or address. */
    @Serializable @SerialName("open_maps")
    data class OpenMaps(val query: String = "", val navigate: Boolean = false) : ActionSpec

    /** Open the dialer pre-filled with a number (no call permission needed). */
    @Serializable @SerialName("dial_number")
    data class DialNumber(val number: String = "") : ActionSpec

    @Serializable @SerialName("launch_assistant")
    data object LaunchAssistant : ActionSpec

    /** [extras] is one `key=value` per line. */
    @Serializable @SerialName("send_intent")
    data class SendIntent(
        val target: IntentTarget = IntentTarget.ACTIVITY,
        val action: String = "",
        val data: String = "",
        val packageName: String = "",
        val className: String = "",
        val extras: String = "",
    ) : ActionSpec

    @Serializable @SerialName("share_text")
    data class ShareText(val text: String = "") : ActionSpec

    @Serializable @SerialName("set_alarm")
    data class SetAlarm(val hour: Int = 7, val minute: Int = 0, val label: String = "") : ActionSpec

    @Serializable @SerialName("set_timer")
    data class SetTimer(val seconds: Int = 300, val label: String = "") : ActionSpec

    @Serializable @SerialName("kill_background_app")
    data class KillBackgroundApp(val packageName: String = "") : ActionSpec

    /** Open the system App Info screen for a package. */
    @Serializable @SerialName("open_app_info")
    data class OpenAppInfo(val packageName: String = "") : ActionSpec

    /** Start the uninstall flow for an app (the system shows a confirmation). */
    @Serializable @SerialName("uninstall_app")
    data class UninstallApp(val packageName: String = "") : ActionSpec

    /** Force-stop an app via the privileged shell (needs Shizuku or root). */
    @Serializable @SerialName("force_stop_app")
    data class ForceStopApp(val packageName: String = "") : ActionSpec

    @Serializable @SerialName("open_camera_app")
    data object OpenCameraApp : ActionSpec

    @Serializable @SerialName("open_settings")
    data class OpenSettings(val screen: SettingsScreen = SettingsScreen.MAIN) : ActionSpec
    // endregion

    // region Phone & SMS
    @Serializable @SerialName("make_call")
    data class MakeCall(val number: String = "") : ActionSpec

    @Serializable @SerialName("send_sms")
    data class SendSms(val number: String = "", val message: String = "") : ActionSpec

    @Serializable @SerialName("answer_call")
    data object AnswerCall : ActionSpec

    @Serializable @SerialName("end_call")
    data object EndCall : ActionSpec
    // endregion

    // region Files
    @Serializable @SerialName("write_file")
    data class WriteFile(val path: String = "/sdcard/Documents/openmacro.txt", val text: String = "", val append: Boolean = true) : ActionSpec

    @Serializable @SerialName("read_file")
    data class ReadFile(val path: String = "/sdcard/Documents/openmacro.txt", val target: VarRef = VarRef()) : ActionSpec

    @Serializable @SerialName("file_operation")
    data class FileOperation(val op: FileOp = FileOp.COPY, val source: String = "/sdcard/", val destination: String = "/sdcard/") : ActionSpec
    // endregion

    // region OpenMacro
    @Serializable @SerialName("run_macro")
    data class RunMacro(val macro: MacroRef = MacroRef()) : ActionSpec

    @Serializable @SerialName("enable_macro")
    data class EnableMacro(val macro: MacroRef = MacroRef(), val mode: SwitchMode = SwitchMode.ON) : ActionSpec

    @Serializable @SerialName("cancel_macro")
    data class CancelMacro(val macro: MacroRef = MacroRef()) : ActionSpec

    /** Disables the macro that is currently running (e.g. run-once patterns). */
    @Serializable @SerialName("disable_this_macro")
    data object DisableThisMacro : ActionSpec

    /** Show floating text over other apps for a few seconds (needs display-over-apps). */
    @Serializable @SerialName("overlay_text")
    data class ShowOverlay(val text: String = "", val seconds: Int = 5, val position: OverlayPosition = OverlayPosition.CENTER) : ActionSpec

    @Serializable @SerialName("set_engine")
    data class SetEngine(val enabled: Boolean = false) : ActionSpec

    /** Runs via `sh -c` (or `su -c` when [root]); stdout optionally saved to [output]. */
    @Serializable @SerialName("shell_command")
    data class ShellCommand(val command: String = "", val root: Boolean = false, val output: VarRef = VarRef(), val timeoutSeconds: Int = 30) : ActionSpec
    // endregion

    // region Web & network (online flavor only)
    /** [headers] is one `Name: value` per line. Response body and status code are optionally stored. */
    @Serializable @SerialName("http_request")
    data class HttpRequest(
        val method: HttpMethod = HttpMethod.GET,
        val url: String = "https://",
        val headers: String = "",
        val body: String = "",
        val contentType: String = "application/json",
        val responseBody: VarRef = VarRef(),
        val responseCode: VarRef = VarRef(),
        val timeoutSeconds: Int = 30,
    ) : ActionSpec

    @Serializable @SerialName("udp_command")
    data class UdpCommand(val host: String = "", val port: Int = 9, val payload: String = "") : ActionSpec

    @Serializable @SerialName("connectivity_check")
    data class ConnectivityCheck(val url: String = "https://www.google.com/generate_204", val result: VarRef = VarRef()) : ActionSpec

    /** Download a URL to a file; optionally store the saved path in a variable. */
    @Serializable @SerialName("download_file")
    data class DownloadFile(
        val url: String = "https://",
        val destination: String = "/sdcard/Download/openmacro-download",
        val savedPath: VarRef = VarRef(),
        val timeoutSeconds: Int = 60,
    ) : ActionSpec

    /** Fetch the current weather (Open-Meteo, no API key) into variables (online flavor). */
    @Serializable @SerialName("get_weather")
    data class GetWeather(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val temperature: VarRef = VarRef(),
        val condition: VarRef = VarRef(),
    ) : ActionSpec
    // endregion

    // region Location
    /** Read the device's current location into latitude/longitude variables (needs location). */
    @Serializable @SerialName("get_location")
    data class GetLocation(val latitude: VarRef = VarRef(), val longitude: VarRef = VarRef()) : ActionSpec
    // endregion

    // region Accessibility (UI automation)
    @Serializable @SerialName("ui_button")
    data class PressButton(val button: UiButton = UiButton.BACK) : ActionSpec

    @Serializable @SerialName("ui_tap")
    data class TapScreen(val x: Int = 540, val y: Int = 1200, val longPress: Boolean = false) : ActionSpec

    @Serializable @SerialName("ui_tap_text")
    data class TapElement(val text: String = "", val longPress: Boolean = false) : ActionSpec

    @Serializable @SerialName("ui_swipe")
    data class Swipe(
        val startX: Int = 540,
        val startY: Int = 1600,
        val endX: Int = 540,
        val endY: Int = 600,
        val durationMs: Int = 300,
    ) : ActionSpec

    @Serializable @SerialName("ui_input_text")
    data class InputText(val text: String = "") : ActionSpec

    @Serializable @SerialName("ui_read_screen")
    data class ReadScreen(val target: VarRef = VarRef()) : ActionSpec
    // endregion

    // region Notifications (listener access)
    /** Dismiss shown notifications; blank [packageName] clears all dismissable ones. */
    @Serializable @SerialName("clear_notifications")
    data class ClearNotifications(val packageName: String = "") : ActionSpec
    // endregion

    // region System (secure settings / root)
    @Serializable @SerialName("set_wifi")
    data class SetWifi(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    @Serializable @SerialName("set_mobile_data")
    data class SetMobileData(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    @Serializable @SerialName("set_airplane_mode")
    data class SetAirplaneMode(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    @Serializable @SerialName("set_nfc")
    data class SetNfc(val mode: SwitchMode = SwitchMode.TOGGLE) : ActionSpec

    @Serializable @SerialName("set_location_mode")
    data class SetLocationMode(val mode: LocationMode = LocationMode.HIGH_ACCURACY) : ActionSpec

    @Serializable @SerialName("reboot")
    data class Reboot(val mode: RebootMode = RebootMode.NORMAL) : ActionSpec


    /** Inject a key event (Android KEYCODE_* number) via the privileged shell. */
    @Serializable @SerialName("input_key_event")
    data class InputKeyEvent(val keyCode: Int = 26) : ActionSpec

    /** Write an arbitrary system/secure/global setting (power-user escape hatch). */
    @Serializable @SerialName("write_setting")
    data class WriteSetting(
        val namespace: SettingNamespace = SettingNamespace.SYSTEM,
        val key: String = "",
        val value: String = "",
    ) : ActionSpec
    // endregion
}

enum class RebootMode { NORMAL, RECOVERY, BOOTLOADER, SOFT }

enum class SettingsScreen { MAIN, WIFI, BLUETOOTH, LOCATION, DISPLAY, SOUND, BATTERY, APPS, DATE, SECURITY, ACCESSIBILITY }

enum class SettingNamespace { SYSTEM, SECURE, GLOBAL }


enum class LocationMode { OFF, DEVICE_ONLY, BATTERY_SAVING, HIGH_ACCURACY }

enum class UiButton { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT, POWER_DIALOG, SPLIT_SCREEN }

enum class SwitchMode { ON, OFF, TOGGLE }

enum class AudioStream { MEDIA, RING, NOTIFICATION, ALARM, CALL }

enum class RingerMode { NORMAL, VIBRATE, SILENT }

enum class VibrationPattern { SHORT, LONG, DOUBLE, HEARTBEAT }

enum class RingtoneType { RINGTONE, NOTIFICATION, ALARM }

enum class ScreenRotation { AUTO, PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE }

enum class OverlayPosition { TOP, CENTER, BOTTOM }

enum class LoopMode { COUNT, WHILE }

enum class TextOp { UPPERCASE, LOWERCASE, TRIM, LENGTH, SUBSTRING, REPLACE, REGEX_REPLACE, EXTRACT_REGEX }

enum class EncodeMode { BASE64_ENCODE, BASE64_DECODE, URL_ENCODE, URL_DECODE }

enum class HashAlgorithm { MD5, SHA1, SHA256 }

enum class GenerateKind { UUID, RANDOM_NUMBER, TIMESTAMP }

enum class VolumeDirection { UP, DOWN, MUTE, UNMUTE }

enum class DndMode { ALL, PRIORITY, ALARMS, NONE }

enum class MediaCommand { PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS }

enum class SoundChoice { NOTIFICATION, RINGTONE, ALARM, CUSTOM }

enum class WallpaperTarget { HOME, LOCK, BOTH }

enum class IntentTarget { ACTIVITY, BROADCAST, SERVICE }

enum class FileOp { COPY, MOVE, DELETE, CREATE_FOLDER, RENAME }

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD }
