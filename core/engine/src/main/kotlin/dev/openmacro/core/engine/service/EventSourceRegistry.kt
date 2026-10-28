package dev.openmacro.core.engine.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.model.EventSource
import dev.openmacro.core.model.PackageEvent
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.SourceKey
import dev.openmacro.core.model.TriggerEvent

internal fun interface Subscription {
    fun close()
}

/**
 * Opens and closes system subscriptions so exactly the [SourceKey]s needed by active macros are
 * live. Used from the main thread. Edge-detecting sources seed their state on open so an initial
 * sticky broadcast never fires a macro.
 */
internal class EventSourceRegistry(
    private val context: Context,
    private val emit: (TriggerEvent) -> Unit,
) {
    private val open = mutableMapOf<SourceKey, Subscription>()

    fun update(required: Set<SourceKey>) {
        // ALARM is driven by AlarmManager; the rest push events directly from their own services.
        val managed = setOf(EventSource.ALARM, EventSource.INTERNAL, EventSource.ACCESSIBILITY, EventSource.NOTIFICATIONS)
        val wanted = required.filter { it.source !in managed }.toSet()
        (open.keys - wanted).forEach { open.remove(it)?.close() }
        (wanted - open.keys).forEach { open[it] = subscribe(it) }
        EngineLog.i("Live sources: ${open.keys.map { it.source }}")
    }

    fun refreshPermissionDependent() {
        open.keys.filter { it.source == EventSource.BLUETOOTH || it.source == EventSource.SMS || it.source == EventSource.CALLS }
            .forEach { key -> open.remove(key)?.close(); open[key] = subscribe(key) }
    }

    fun closeAll() {
        open.values.forEach { it.close() }
        open.clear()
    }

    private fun subscribe(key: SourceKey): Subscription = when (key.source) {
        EventSource.BATTERY -> battery()
        EventSource.POWER -> receiver(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED) {
            emit(TriggerEvent.PowerConnectionChanged(it.action == Intent.ACTION_POWER_CONNECTED))
        }
        EventSource.SCREEN -> receiver(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF) {
            emit(TriggerEvent.ScreenChanged(it.action == Intent.ACTION_SCREEN_ON))
        }
        EventSource.UNLOCK -> receiver(Intent.ACTION_USER_PRESENT) { emit(TriggerEvent.DeviceUnlocked) }
        EventSource.HEADPHONES -> headphones()
        EventSource.WIFI -> wifi()
        EventSource.BLUETOOTH -> bluetooth()
        EventSource.AIRPLANE -> airplane()
        // The NFC service broadcasts from its own UID, which a not-exported receiver would drop.
        EventSource.NFC -> exportedReceiver(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
            when (it.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)) {
                NfcAdapter.STATE_ON -> emit(TriggerEvent.NfcChanged(true))
                NfcAdapter.STATE_OFF -> emit(TriggerEvent.NfcChanged(false))
            }
        }
        EventSource.LOCATION_MODE -> receiver(android.location.LocationManager.MODE_CHANGED_ACTION) {
            val lm = context.getSystemService(android.location.LocationManager::class.java)
            emit(TriggerEvent.LocationServicesChanged(lm.isLocationEnabled))
        }
        EventSource.DARK_THEME -> darkTheme()
        EventSource.BATTERY_SAVER -> receiver(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
            emit(TriggerEvent.BatterySaverChanged(context.getSystemService(PowerManager::class.java).isPowerSaveMode))
        }
        EventSource.DOZE -> receiver(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
            emit(TriggerEvent.DozeChanged(context.getSystemService(PowerManager::class.java).isDeviceIdleMode))
        }
        @Suppress("DEPRECATION")
        EventSource.STORAGE -> receiver(Intent.ACTION_DEVICE_STORAGE_LOW, Intent.ACTION_DEVICE_STORAGE_OK) {
            emit(TriggerEvent.StorageChanged(it.action == Intent.ACTION_DEVICE_STORAGE_LOW))
        }
        EventSource.CLOCK -> receiver(Intent.ACTION_TIMEZONE_CHANGED) { emit(TriggerEvent.TimezoneChangedEvent) }
        EventSource.RINGER -> ringer()
        EventSource.DOCK -> dock()
        EventSource.PACKAGES -> packages()
        EventSource.CUSTOM_INTENT -> customIntent(key.param)
        EventSource.SMS -> sms()
        EventSource.CALLS -> calls()
        EventSource.SENSORS -> SensorEventSource.open(context, key.param, emit)
        EventSource.LOCATION -> LocationEventSource.open(context, key.param, emit)
        EventSource.FILE -> FileEventSource.open(key.param, emit)
        EventSource.WEBHOOK -> WebhookServer.open(emit)
        EventSource.ALARM, EventSource.INTERNAL, EventSource.ACCESSIBILITY, EventSource.NOTIFICATIONS -> Subscription {}
    }

    /**
     * User-defined intent action. Registered as exported on purpose: the whole point of an intent
     * trigger is that other apps, scripts and `adb shell am broadcast` can fire it.
     */
    @Suppress("DEPRECATION")
    private fun customIntent(action: String): Subscription {
        if (action.isBlank()) return Subscription {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val bundle = intent.extras
                val extras = bundle?.keySet()?.associateWith { k -> bundle.get(k)?.toString() ?: "" } ?: emptyMap()
                emit(TriggerEvent.IntentReceived(intent.action ?: action, extras))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        return Subscription { context.unregisterReceiver(receiver) }
    }

    /**
     * Watches the airplane-mode setting. Current Android no longer delivers the
     * AIRPLANE_MODE broadcast reliably to apps, but the setting itself always changes.
     */
    private fun airplane(): Subscription {
        val resolver = context.contentResolver
        fun isOn() = android.provider.Settings.Global.getInt(resolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        var last = isOn()
        fun check() {
            val now = isOn()
            if (now != last) { last = now; emit(TriggerEvent.AirplaneModeChanged(now)) }
        }
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = check()
        }
        resolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.AIRPLANE_MODE_ON), false, observer)
        // Belt and braces: whichever of the observer or the protected broadcast arrives first wins.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = check()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        // Some paths (e.g. `cmd connectivity airplane-mode`) change the setting without notifying
        // observers or sending the broadcast, so a cheap poll backstops both.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() { check(); handler.postDelayed(this, 2_000) }
        }
        handler.postDelayed(poll, 2_000)
        return Subscription {
            handler.removeCallbacks(poll)
            resolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    private fun battery(): Subscription {
        var lastLevel: Int? = null
        var lastTemp: Int? = null
        var wasFull = false
        var wasUsb = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val full = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
                if (full && !wasFull && !isInitialStickyBroadcast) emit(TriggerEvent.BatteryFull)
                wasFull = full
                val usb = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_USB
                if (usb != wasUsb && !isInitialStickyBroadcast) emit(TriggerEvent.UsbChanged(usb))
                wasUsb = usb
                if (level >= 0 && scale > 0) {
                    val percent = level * 100 / scale
                    val prev = lastLevel
                    lastLevel = percent
                    if (prev != null && prev != percent && !isInitialStickyBroadcast) emit(TriggerEvent.BatteryLevelChanged(prev, percent))
                }
                if (tempTenths != Int.MIN_VALUE) {
                    val temp = Math.round(tempTenths / 10.0).toInt()
                    val prev = lastTemp
                    lastTemp = temp
                    if (prev != null && prev != temp && !isInitialStickyBroadcast) emit(TriggerEvent.BatteryTemperatureChanged(prev, temp))
                }
            }
        }
        return register(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    @Suppress("DEPRECATION")
    private fun headphones(): Subscription {
        // ACTION_HEADSET_PLUG is sticky; ignore repeats of the state we already saw.
        var last: Boolean? = null
        return receiver(Intent.ACTION_HEADSET_PLUG) { intent ->
            val connected = intent.getIntExtra("state", 0) == 1
            if (last != null && last != connected) emit(TriggerEvent.HeadphonesChanged(connected))
            last = connected
        }
    }

    private fun wifi(): Subscription {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val networks = mutableSetOf<Network>()
        var connected = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = change { networks += network }
            override fun onLost(network: Network) = change { networks -= network }
            private fun change(block: () -> Unit) = synchronized(networks) {
                block()
                val now = networks.isNotEmpty()
                if (now != connected) { connected = now; emit(TriggerEvent.WifiChanged(now, if (now) currentSsid() else null)) }
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
        return Subscription { connectivity.unregisterNetworkCallback(callback) }
    }

    /** Current Wi-Fi SSID, or null. Needs location permission and location services on (Android 10+). */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun currentSsid(): String? {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        return try {
            val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            wifi?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        } catch (e: Exception) {
            null
        }
    }

    private fun bluetooth(): Subscription {
        val actions = buildList {
            add(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT, min = Build.VERSION_CODES.S)) {
                add(BluetoothDevice.ACTION_ACL_CONNECTED)
                add(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        }
        return receiver(*actions.toTypedArray()) { intent ->
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> emit(TriggerEvent.BluetoothAdapterChanged(true))
                    BluetoothAdapter.STATE_OFF -> emit(TriggerEvent.BluetoothAdapterChanged(false))
                }
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    emit(TriggerEvent.BluetoothDeviceChanged(intent.action == BluetoothDevice.ACTION_ACL_CONNECTED, deviceName(intent)))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceName(intent: Intent): String? {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT, min = Build.VERSION_CODES.S)) return null
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        return try { device?.name } catch (e: SecurityException) { null }
    }

    private fun ringer(): Subscription {
        val audio = context.getSystemService(AudioManager::class.java)
        var last = audio.ringerMode
        return receiver(AudioManager.RINGER_MODE_CHANGED_ACTION) {
            val mode = audio.ringerMode
            if (mode == last) return@receiver
            last = mode
            emit(TriggerEvent.RingerModeChanged(when (mode) {
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            }))
        }
    }

    /**
     * Uses a configuration callback rather than ACTION_CONFIGURATION_CHANGED: the broadcast can
     * arrive before the app's own resources are updated, which made the trigger report the
     * previous state. The callback delivers the new configuration directly.
     */
    private fun darkTheme(): Subscription {
        fun isDark(uiMode: Int) = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        var last = isDark(context.resources.configuration.uiMode)
        val callbacks = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                val now = isDark(newConfig.uiMode)
                if (now != last) { last = now; emit(TriggerEvent.DarkThemeChanged(now)) }
            }

            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)
        return Subscription { context.unregisterComponentCallbacks(callbacks) }
    }

    @Suppress("DEPRECATION")
    private fun dock(): Subscription = receiver(Intent.ACTION_DOCK_EVENT) {
        val state = it.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED)
        emit(TriggerEvent.DockChanged(state != Intent.EXTRA_DOCK_STATE_UNDOCKED))
    }

    private fun packages(): Subscription {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                val event = when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> if (replacing) PackageEvent.UPDATED else PackageEvent.INSTALLED
                    Intent.ACTION_PACKAGE_REMOVED -> if (replacing) return else PackageEvent.REMOVED
                    else -> return
                }
                emit(TriggerEvent.PackageChanged(event, pkg))
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        // Package broadcasts are system-sent; a data scheme filter still needs explicit registration.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return Subscription { context.unregisterReceiver(receiver) }
    }

    private fun sms(): Subscription {
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            EngineLog.w("RECEIVE_SMS not granted; SMS triggers inactive")
            return Subscription {}
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                val from = messages.firstOrNull()?.originatingAddress ?: ""
                val body = messages.joinToString("") { it.messageBody ?: "" }
                emit(TriggerEvent.SmsReceived(from, body))
            }
        }
        // SMS_RECEIVED is a protected broadcast delivered to a matching exported receiver.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), ContextCompat.RECEIVER_EXPORTED,
        )
        return Subscription { context.unregisterReceiver(receiver) }
    }

    private fun calls(): Subscription {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            EngineLog.w("READ_PHONE_STATE not granted; call triggers inactive")
            return Subscription {}
        }
        var lastState = TelephonyManager.CALL_STATE_IDLE
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
                val stateName = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
                val state = when (stateName) {
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    else -> TelephonyManager.CALL_STATE_IDLE
                }
                val prev = lastState
                lastState = state
                val event = when {
                    state == TelephonyManager.CALL_STATE_RINGING -> dev.openmacro.core.model.CallEvent.INCOMING
                    state == TelephonyManager.CALL_STATE_OFFHOOK && prev == TelephonyManager.CALL_STATE_RINGING -> dev.openmacro.core.model.CallEvent.ANSWERED
                    state == TelephonyManager.CALL_STATE_IDLE && prev == TelephonyManager.CALL_STATE_RINGING -> dev.openmacro.core.model.CallEvent.MISSED
                    state == TelephonyManager.CALL_STATE_IDLE && prev == TelephonyManager.CALL_STATE_OFFHOOK -> dev.openmacro.core.model.CallEvent.ENDED
                    else -> return
                }
                emit(TriggerEvent.CallChanged(event, number))
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        return Subscription { context.unregisterReceiver(receiver) }
    }

    private fun hasPermission(permission: String, min: Int = 0): Boolean =
        Build.VERSION.SDK_INT < min ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun receiver(vararg actions: String, onReceive: (Intent) -> Unit): Subscription {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onReceive(intent)
        }
        return register(receiver, IntentFilter().apply { actions.filter { it.isNotBlank() }.forEach(::addAction) })
    }

    private fun exportedReceiver(vararg actions: String, onReceive: (Intent) -> Unit): Subscription {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onReceive(intent)
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter().apply { actions.forEach(::addAction) }, ContextCompat.RECEIVER_EXPORTED,
        )
        return Subscription { context.unregisterReceiver(receiver) }
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter): Subscription {
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return Subscription { context.unregisterReceiver(receiver) }
    }
}
