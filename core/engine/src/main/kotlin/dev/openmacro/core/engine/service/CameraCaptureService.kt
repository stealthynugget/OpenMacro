package dev.openmacro.core.engine.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.engine.platform.CameraCapture
import dev.openmacro.core.engine.platform.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs as a camera-type foreground service for the moment it takes to capture one photo, so capture
 * works even when OpenMacro isn't the foreground app. Stops itself as soon as the photo is saved.
 */
class CameraCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        val front = intent?.getBooleanExtra(EXTRA_FRONT, false) ?: false
        val destination = intent?.getStringExtra(EXTRA_DEST).orEmpty()
        if (id == null || destination.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        Notifications.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, Notifications.cameraNotification(this), type)

        scope.launch {
            val path = runCatching { CameraCapture.takePhoto(this@CameraCaptureService, front, destination) }
                .getOrNull()
            CameraRequests.complete(id, path)
            EngineLog.i("Camera capture ${if (path != null) "saved $path" else "failed"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 3
        private const val EXTRA_ID = "id"
        private const val EXTRA_FRONT = "front"
        private const val EXTRA_DEST = "dest"

        fun intent(context: Context, id: String, front: Boolean, destination: String): Intent =
            Intent(context, CameraCaptureService::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_FRONT, front)
                .putExtra(EXTRA_DEST, destination)
    }
}
