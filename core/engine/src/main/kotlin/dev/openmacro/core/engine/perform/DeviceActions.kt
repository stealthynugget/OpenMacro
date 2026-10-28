package dev.openmacro.core.engine.perform

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.net.toUri
import dev.openmacro.core.engine.platform.TorchController
import dev.openmacro.core.engine.platform.TtsSpeaker
import dev.openmacro.core.engine.platform.androidStream
import dev.openmacro.core.model.ActionSpec as A
import dev.openmacro.core.model.DndMode
import dev.openmacro.core.model.MediaCommand
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.SwitchMode
import dev.openmacro.core.model.VibrationPattern
import dev.openmacro.core.model.VolumeDirection
import dev.openmacro.core.model.WallpaperTarget
import kotlin.math.roundToInt

/** Device, audio and display actions. Returns null for specs handled elsewhere. */
internal class DeviceActions(private val app: Context) {
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val torch by lazy { TorchController(app) }
    private val tts by lazy { TtsSpeaker(app) }

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? = when (spec) {
        is A.Flashlight -> torch.apply(spec.mode)
        is A.FlashlightStrobe -> strobe(spec)
        is A.TakePhoto -> takePhoto(spec, ctx)
        is A.SetRingtone -> setRingtone(spec, ctx)
        is A.Vibrate -> vibrate(spec.pattern)
        is A.KeepAwake -> keepAwake(spec)
        is A.ScreenOn -> wakeScreen()
        is A.CarMode -> uiMode(spec.on)
        is A.GetLocation -> getLocation(spec, ctx)
        is A.ShowOverlay -> dev.openmacro.core.engine.platform.OverlayController.show(app, ctx.render(spec.text), spec.seconds, spec.position)
        is A.SetVolume -> setVolume(spec)
        is A.VolumeStep -> volumeStep(spec)
        is A.SetRingerMode -> setRinger(spec.mode)
        is A.DoNotDisturb -> setDnd(spec.mode)
        is A.MediaControl -> mediaControl(spec.command)
        is A.SpeakText -> tts.speak(ctx.render(spec.text), spec.waitToFinish)
        is A.PlaySound -> dev.openmacro.core.engine.platform.SoundPlayer.play(app, spec.sound, ctx.render(spec.uri), spec.waitToFinish)
        is A.SayTime -> tts.speak(sayTimeText(), waitToFinish = true)
        is A.StopSpeaking -> { tts.stop(); ActionOutcome.Success }
        is A.MuteMicrophone -> { audio.isMicrophoneMute = switchToTarget(spec.mode, audio.isMicrophoneMute); ActionOutcome.Success }
        is A.Speakerphone -> setSpeakerphone(spec.on)
        is A.SetBrightness -> setBrightness(spec)
        is A.ScreenTimeout -> writeSystem(Settings.System.SCREEN_OFF_TIMEOUT, (spec.seconds * 1000).toString())
        is A.SetAutoRotate -> writeSystem(Settings.System.ACCELEROMETER_ROTATION, if (switchToTarget(spec.mode, isAutoRotate())) "1" else "0")
        is A.SetScreenRotation -> setScreenRotation(spec.orientation)
        is A.FontScale -> writeSystem(Settings.System.FONT_SCALE, (spec.percent / 100f).toString())
        is A.SetWallpaper -> setWallpaper(ctx.render(spec.uri), spec.target)
        else -> null
    }

    private suspend fun strobe(spec: A.FlashlightStrobe): ActionOutcome {
        val interval = spec.intervalMs.coerceIn(20, 5000).toLong()
        try {
            repeat(spec.times.coerceIn(1, 500)) {
                if (torch.apply(SwitchMode.ON) is ActionOutcome.Failed) return ActionOutcome.Failed("No flash unit")
                kotlinx.coroutines.delay(interval)
                torch.apply(SwitchMode.OFF)
                kotlinx.coroutines.delay(interval)
            }
        } finally {
            torch.apply(SwitchMode.OFF) // never leave the torch stuck on
        }
        return ActionOutcome.Success
    }

    private fun setRingtone(spec: A.SetRingtone, ctx: ActionContext): ActionOutcome {
        if (spec.uri.isBlank()) return ActionOutcome.Failed("No sound selected")
        if (!Settings.System.canWrite(app)) return ActionOutcome.Failed("Modify system settings permission is required")
        val type = when (spec.type) {
            dev.openmacro.core.model.RingtoneType.RINGTONE -> android.media.RingtoneManager.TYPE_RINGTONE
            dev.openmacro.core.model.RingtoneType.NOTIFICATION -> android.media.RingtoneManager.TYPE_NOTIFICATION
            dev.openmacro.core.model.RingtoneType.ALARM -> android.media.RingtoneManager.TYPE_ALARM
        }
        return try {
            android.media.RingtoneManager.setActualDefaultRingtoneUri(app, type, android.net.Uri.parse(spec.uri))
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Could not set ringtone")
        }
    }

    private fun vibrate(pattern: VibrationPattern): ActionOutcome {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return ActionOutcome.Failed("Device has no vibrator")
        val effect = when (pattern) {
            VibrationPattern.SHORT -> VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
            VibrationPattern.LONG -> VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE)
            VibrationPattern.DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 150, 120, 150), -1)
            VibrationPattern.HEARTBEAT -> VibrationEffect.createWaveform(longArrayOf(0, 90, 110, 90, 600, 90, 110, 90), -1)
        }
        vibrator.vibrate(effect)
        return ActionOutcome.Success
    }

    private val wakeLocks = ArrayDeque<PowerManager.WakeLock>()

    @Suppress("DEPRECATION")
    private fun keepAwake(spec: A.KeepAwake): ActionOutcome {
        val pm = app.getSystemService(PowerManager::class.java)
        if (!spec.on) {
            while (wakeLocks.isNotEmpty()) wakeLocks.removeFirst().takeIf { it.isHeld }?.release()
            return ActionOutcome.Success
        }
        val lock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "OpenMacro:keepAwake")
        lock.acquire(spec.minutes.coerceIn(1, 240) * 60_000L)
        wakeLocks.addLast(lock)
        return ActionOutcome.Success
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(): ActionOutcome {
        val pm = app.getSystemService(PowerManager::class.java)
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "OpenMacro:screenOn",
        )
        lock.acquire(5_000L)
        return ActionOutcome.Success
    }

    private suspend fun takePhoto(spec: A.TakePhoto, ctx: ActionContext): ActionOutcome {
        if (!dev.openmacro.core.engine.platform.CameraCapture.hasPermission(app)) {
            return ActionOutcome.Failed("Camera permission is required")
        }
        val dest = ctx.render(spec.destination)
        // Prefer the camera foreground service (works in background); fall back to a direct capture.
        val id = java.util.UUID.randomUUID().toString()
        val deferred = dev.openmacro.core.engine.service.CameraRequests.register(id)
        val path = try {
            androidx.core.content.ContextCompat.startForegroundService(
                app,
                dev.openmacro.core.engine.service.CameraCaptureService.intent(app, id, spec.frontCamera, dest),
            )
            kotlinx.coroutines.withTimeoutOrNull(20_000) { deferred.await() }
        } catch (e: Exception) {
            dev.openmacro.core.engine.service.CameraRequests.complete(id, null)
            dev.openmacro.core.engine.platform.CameraCapture.takePhoto(app, spec.frontCamera, dest)
        } ?: return ActionOutcome.Failed("Photo capture failed (try with the screen on)")

        if (spec.savedPath.name.isNotBlank()) ctx.setVariableValue(spec.savedPath, path)
        return ActionOutcome.Success
    }

    private suspend fun getLocation(spec: A.GetLocation, ctx: ActionContext): ActionOutcome {
        if (!dev.openmacro.core.engine.platform.LocationFetcher.hasPermission(app)) {
            return ActionOutcome.Failed("Location permission is required")
        }
        val location = dev.openmacro.core.engine.platform.LocationFetcher.current(app)
            ?: return ActionOutcome.Failed("Couldn't get a location fix (is location on?)")
        ctx.setVariableValue(spec.latitude, location.latitude.toString())
        ctx.setVariableValue(spec.longitude, location.longitude.toString())
        return ActionOutcome.Success
    }

    private fun uiMode(on: Boolean): ActionOutcome {
        val uiModeManager = app.getSystemService(android.app.UiModeManager::class.java)
        return try {
            if (on) uiModeManager.enableCarMode(0) else uiModeManager.disableCarMode(0)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Car mode not permitted on this device")
        }
    }

    private fun setVolume(spec: A.SetVolume): ActionOutcome {
        val stream = spec.stream.androidStream()
        val max = audio.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
        val index = min + ((max - min) * spec.percent.coerceIn(0, 100) / 100.0).roundToInt()
        return try {
            audio.setStreamVolume(stream, index, 0)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Do Not Disturb access is required for this stream")
        }
    }

    private fun volumeStep(spec: A.VolumeStep): ActionOutcome {
        val stream = spec.stream.androidStream()
        val flags = if (spec.showUi) AudioManager.FLAG_SHOW_UI else 0
        val direction = when (spec.direction) {
            VolumeDirection.UP -> AudioManager.ADJUST_RAISE
            VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
            VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
            VolumeDirection.UNMUTE -> AudioManager.ADJUST_UNMUTE
        }
        return try {
            audio.adjustStreamVolume(stream, direction, flags)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Do Not Disturb access is required for this stream")
        }
    }

    private fun setRinger(mode: RingerMode): ActionOutcome {
        if (mode == RingerMode.SILENT && !notifications.isNotificationPolicyAccessGranted) {
            return ActionOutcome.Failed("Do Not Disturb access is required for silent mode")
        }
        return try {
            audio.ringerMode = when (mode) {
                RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Do Not Disturb access is required to change ringer mode")
        }
    }

    private fun setDnd(mode: DndMode): ActionOutcome {
        if (!notifications.isNotificationPolicyAccessGranted) return ActionOutcome.Failed("Do Not Disturb access is required")
        notifications.setInterruptionFilter(
            when (mode) {
                DndMode.ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
                DndMode.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                DndMode.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                DndMode.NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
            },
        )
        return ActionOutcome.Success
    }

    private fun mediaControl(command: MediaCommand): ActionOutcome {
        val key = when (command) {
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return ActionOutcome.Success
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphone(on: Boolean): ActionOutcome {
        audio.isSpeakerphoneOn = on
        return ActionOutcome.Success
    }

    private fun setBrightness(spec: A.SetBrightness): ActionOutcome {
        val mode = if (spec.auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        val ok = writeSystem(Settings.System.SCREEN_BRIGHTNESS_MODE, mode.toString())
        if (ok is ActionOutcome.Failed) return ok
        if (spec.auto) return ActionOutcome.Success
        val value = (spec.percent.coerceIn(0, 100) * 255 / 100)
        return writeSystem(Settings.System.SCREEN_BRIGHTNESS, value.toString())
    }

    private fun setWallpaper(uri: String, target: WallpaperTarget): ActionOutcome {
        if (uri.isBlank()) return ActionOutcome.Failed("No image selected")
        return try {
            val wm = WallpaperManager.getInstance(app)
            app.contentResolver.openInputStream(uri.toUri()).use { input ->
                if (input == null) return ActionOutcome.Failed("Can't read image")
                val which = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wm.setStream(input, null, true, which)
            }
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Wallpaper failed")
        }
    }

    private fun setScreenRotation(orientation: dev.openmacro.core.model.ScreenRotation): ActionOutcome {
        if (orientation == dev.openmacro.core.model.ScreenRotation.AUTO) {
            return writeSystem(Settings.System.ACCELEROMETER_ROTATION, "1")
        }
        val disable = writeSystem(Settings.System.ACCELEROMETER_ROTATION, "0")
        if (disable is ActionOutcome.Failed) return disable
        val rotation = when (orientation) {
            dev.openmacro.core.model.ScreenRotation.PORTRAIT -> android.view.Surface.ROTATION_0
            dev.openmacro.core.model.ScreenRotation.LANDSCAPE -> android.view.Surface.ROTATION_90
            dev.openmacro.core.model.ScreenRotation.REVERSE_PORTRAIT -> android.view.Surface.ROTATION_180
            dev.openmacro.core.model.ScreenRotation.REVERSE_LANDSCAPE -> android.view.Surface.ROTATION_270
            dev.openmacro.core.model.ScreenRotation.AUTO -> android.view.Surface.ROTATION_0
        }
        return writeSystem(Settings.System.USER_ROTATION, rotation.toString())
    }

    private fun isAutoRotate(): Boolean =
        Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0

    private fun writeSystem(key: String, value: String): ActionOutcome =
        if (!Settings.System.canWrite(app)) {
            ActionOutcome.Failed("Modify system settings permission is required")
        } else if (Settings.System.putString(app.contentResolver, key, value)) {
            ActionOutcome.Success
        } else {
            ActionOutcome.Failed("Could not write setting $key")
        }

    private fun sayTimeText(): String {
        val now = java.time.LocalTime.now()
        return "The time is ${java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(now)}"
    }
}
