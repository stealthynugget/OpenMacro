package dev.openmacro.core.engine.platform

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import dev.openmacro.core.engine.perform.ActionOutcome
import dev.openmacro.core.model.SwitchMode
import java.util.concurrent.ConcurrentHashMap

/** Process-wide torch on/off state, kept current by a single registered callback. */
internal object TorchState {
    private val on = ConcurrentHashMap<String, Boolean>()
    private var registered = false

    @Synchronized
    fun ensureCallback(cameras: CameraManager) {
        if (registered) return
        registered = true
        cameras.registerTorchCallback(
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    on[cameraId] = enabled
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    on[cameraId] = false
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    fun isOn(cameras: CameraManager): Boolean {
        ensureCallback(cameras)
        return on.values.any { it }
    }

    fun isOn(cameraId: String): Boolean = on[cameraId] == true
}

/** Flashlight control via [CameraManager.setTorchMode], which needs no CAMERA permission. */
internal class TorchController(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)

    private val torchCameraId: String? by lazy {
        val withFlash = cameras.cameraIdList.filter {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }

    init {
        TorchState.ensureCallback(cameras)
    }

    fun apply(mode: SwitchMode): ActionOutcome {
        val id = torchCameraId ?: return ActionOutcome.Failed("No camera with a flash unit")
        val target = when (mode) {
            SwitchMode.ON -> true
            SwitchMode.OFF -> false
            SwitchMode.TOGGLE -> !TorchState.isOn(id)
        }
        return try {
            cameras.setTorchMode(id, target)
            ActionOutcome.Success
        } catch (e: CameraAccessException) {
            ActionOutcome.Failed("Flash unavailable (camera in use?)")
        }
    }
}
