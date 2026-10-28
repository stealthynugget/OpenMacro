package dev.openmacro.core.engine.perform

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.openmacro.core.model.ActionSpec as A
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.RebootMode
import dev.openmacro.core.model.SwitchMode

/**
 * Protected system toggles (Wi-Fi, mobile data, airplane mode, NFC, reboot, arbitrary settings).
 * Each resolves [SwitchMode.TOGGLE] against the live state, then applies it through the most direct
 * backend available via [PrivilegedExecutor].
 */
internal class SystemActions(app: Context, private val device: DeviceProbe) {
    private val privileged = PrivilegedExecutor(app)
    private val appContext = app.applicationContext

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? = when (spec) {
        is A.SetWifi -> svcToggle("wifi", target(spec.mode, device.isWifiEnabled()))
        is A.SetMobileData -> svcToggle("data", target(spec.mode, device.isWifiConnected().let { mobileDataEnabled() }))
        is A.SetAirplaneMode -> airplane(target(spec.mode, device.isAirplaneMode()))
        is A.SetNfc -> setNfc(target(spec.mode, device.isNfcEnabled()))
        is A.Reboot -> reboot(spec.mode)
        is A.InputKeyEvent ->
            if (privileged.shell("input keyevent ${spec.keyCode}") != null) ActionOutcome.Success
            else ActionOutcome.Failed("Key event needs Shizuku or root")
        is A.ForceStopApp ->
            if (spec.packageName.isBlank()) ActionOutcome.Failed("No app selected")
            else if (privileged.shell("am force-stop ${spec.packageName}") != null) ActionOutcome.Success
            else ActionOutcome.Failed("Force-stop needs Shizuku or root")
        is A.SetLocationMode -> {
            val value = when (spec.mode) {
                dev.openmacro.core.model.LocationMode.OFF -> "0"
                dev.openmacro.core.model.LocationMode.DEVICE_ONLY -> "1"
                dev.openmacro.core.model.LocationMode.BATTERY_SAVING -> "2"
                dev.openmacro.core.model.LocationMode.HIGH_ACCURACY -> "3"
            }
            privileged.writeSetting(dev.openmacro.core.model.SettingNamespace.SECURE, "location_mode", value)
                ?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Success
        }
        is A.WriteSetting -> privileged.writeSetting(spec.namespace, spec.key.trim(), ctx.render(spec.value))
            ?.let { ActionOutcome.Failed(it) } ?: ActionOutcome.Success
        else -> null
    }

    private fun target(mode: SwitchMode, current: Boolean) = switchToTarget(mode, current)

    private fun mobileDataEnabled(): Boolean =
        Settings.Global.getInt(appContext.contentResolver, "mobile_data", 1) != 0

    /**
     * `svc nfc` is a silent no-op on current Android, so NFC is toggled through `cmd nfc` (which
     * really changes the hardware state). `svc nfc` is kept only as a fallback for old devices.
     */
    private suspend fun setNfc(on: Boolean): ActionOutcome {
        val modern = privileged.shell(if (on) "cmd nfc enable-nfc" else "cmd nfc disable-nfc")
        if (modern != null) return ActionOutcome.Success
        return svcToggle("nfc", on)
    }

    private suspend fun svcToggle(service: String, on: Boolean): ActionOutcome {
        val arg = if (on) "enable" else "disable"
        return if (privileged.shell("svc $service $arg") != null) ActionOutcome.Success
        else ActionOutcome.Failed("$service toggle needs Shizuku or root")
    }

    /**
     * Only the connectivity service can really switch the radios. Merely writing the
     * airplane-mode setting (all a WRITE_SECURE_SETTINGS app can do) flips the flag while leaving
     * Wi-Fi and the mobile connection up, an inconsistent half-state, so that path is not used.
     */
    private suspend fun airplane(on: Boolean): ActionOutcome =
        if (privileged.shell("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}") != null) ActionOutcome.Success
        else ActionOutcome.Failed("Airplane mode needs Shizuku or root on Android 11+ (apps can't switch the radios themselves)")

    private suspend fun reboot(mode: RebootMode): ActionOutcome {
        val command = when (mode) {
            RebootMode.NORMAL -> "reboot"
            RebootMode.RECOVERY -> "reboot recovery"
            RebootMode.BOOTLOADER -> "reboot bootloader"
            RebootMode.SOFT -> "setprop ctl.restart zygote"
        }
        return if (privileged.shell(command, timeoutSeconds = 5) != null) ActionOutcome.Success
        else ActionOutcome.Failed("Reboot needs Shizuku or root")
    }
}
