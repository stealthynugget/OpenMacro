package dev.openmacro.core.engine.platform

import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import dev.openmacro.core.model.AudioStream
import dev.openmacro.core.model.CallState
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.RingerMode
import java.time.LocalTime
import kotlin.math.roundToInt

/** Reads live device state on demand for constraint evaluation and magic text. */
internal class AndroidDeviceProbe(private val context: Context) : DeviceProbe {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
    }

    override fun batteryPercent(): Int = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    override fun batteryTemperatureC(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tenths < 0) 0 else (tenths / 10.0).roundToInt()
    }

    override fun isPowerConnected(): Boolean {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    override fun isDeviceLocked(): Boolean = keyguard.isKeyguardLocked

    override fun isScreenOn(): Boolean = power.isInteractive

    @Suppress("DEPRECATION")
    override fun isHeadphonesConnected(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            )
        }

    override fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    override fun isWifiConnected(): Boolean =
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    override fun isBluetoothEnabled(): Boolean = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true

    override fun isAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    override fun isAutoRotate(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0

    override fun isBatterySaver(): Boolean = power.isPowerSaveMode

    override fun isDarkTheme(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun ringerMode(): RingerMode = when (audio.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
        else -> RingerMode.NORMAL
    }

    override fun volumePercent(stream: AudioStream): Int {
        val s = stream.androidStream()
        val max = audio.getStreamMaxVolume(s)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(s) else 0
        if (max <= min) return 0
        return ((audio.getStreamVolume(s) - min) * 100.0 / (max - min)).roundToInt()
    }

    override fun isMusicActive(): Boolean = audio.isMusicActive

    override fun callState(): CallState = when (telephony?.callStateCompat()) {
        TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
        TelephonyManager.CALL_STATE_OFFHOOK -> CallState.IN_CALL
        else -> CallState.IDLE
    }

    override fun isRoaming(): Boolean = telephony?.isNetworkRoaming == true

    override fun isVpnActive(): Boolean =
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    override fun isLocationEnabled(): Boolean = locationManager.isLocationEnabled

    override fun isNfcEnabled(): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    override fun isTorchOn(): Boolean = TorchState.isOn(cameras)

    override fun isPortrait(): Boolean = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    override fun uptimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun screenText(): String =
        dev.openmacro.core.engine.accessibility.OpenMacroAccessibilityService.instance?.readScreenText() ?: ""

    override fun activeNotifications(): List<dev.openmacro.core.model.NotificationInfo> =
        dev.openmacro.core.engine.notifications.OpenMacroNotificationListener.instance?.activeNotifications() ?: emptyList()

    override fun chargingSource(): dev.openmacro.core.model.ChargingSource {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> dev.openmacro.core.model.ChargingSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> dev.openmacro.core.model.ChargingSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> dev.openmacro.core.model.ChargingSource.WIRELESS
            else -> dev.openmacro.core.model.ChargingSource.UNPLUGGED
        }
    }

    override fun foregroundApp(): String? =
        dev.openmacro.core.engine.accessibility.OpenMacroAccessibilityService.foregroundApp

    override fun isDeviceIdle(): Boolean = power.isDeviceIdleMode

    override fun brightnessPercent(): Int {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
        return (raw * 100 / 255).coerceIn(0, 100)
    }

    override fun isAutoBrightness(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun localTime(): LocalTime = LocalTime.now()

    @Suppress("DEPRECATION")
    private fun TelephonyManager.callStateCompat(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) callStateForSubscription else callState
    } catch (e: SecurityException) {
        TelephonyManager.CALL_STATE_IDLE
    }
}

internal fun AudioStream.androidStream(): Int = when (this) {
    AudioStream.MEDIA -> AudioManager.STREAM_MUSIC
    AudioStream.RING -> AudioManager.STREAM_RING
    AudioStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    AudioStream.ALARM -> AudioManager.STREAM_ALARM
    AudioStream.CALL -> AudioManager.STREAM_VOICE_CALL
}
