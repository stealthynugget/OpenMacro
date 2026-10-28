package dev.openmacro.core.engine.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Captures a single still photo with Camera2 and writes it to a file — no preview UI. Returns the
 * saved file path, or null on failure/timeout. Android restricts camera access from the
 * background, so this is most reliable with the screen on.
 */
internal object CameraCapture {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by hasPermission() at the call site
    suspend fun takePhoto(context: Context, frontCamera: Boolean, destination: String): String? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = pickCamera(manager, frontCamera) ?: return null
        val file = resolveFile(destination)
        file.parentFile?.mkdirs()

        val thread = HandlerThread("openmacro-camera").apply { start() }
        val handler = Handler(thread.looper)
        var camera: CameraDevice? = null
        var reader: ImageReader? = null
        try {
            val size = largestJpegSize(manager, cameraId)
            reader = ImageReader.newInstance(size.first, size.second, ImageFormat.JPEG, 1)
            val rotation = manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            return withTimeoutOrNull(15_000) {
                camera = openCamera(manager, cameraId, handler) ?: return@withTimeoutOrNull null
                val saved = captureToFile(camera!!, reader!!, rotation, file, handler)
                if (saved) file.absolutePath else null
            }
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { camera?.close() }
            runCatching { reader?.close() }
            thread.quitSafely()
        }
    }

    private suspend fun openCamera(manager: CameraManager, id: String, handler: Handler): CameraDevice? =
        suspendCancellableCoroutine { cont ->
            @SuppressLint("MissingPermission")
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { if (cont.isActive) cont.resume(device) }
                override fun onDisconnected(device: CameraDevice) { device.close(); if (cont.isActive) cont.resume(null) }
                override fun onError(device: CameraDevice, error: Int) { device.close(); if (cont.isActive) cont.resume(null) }
            }, handler)
        }

    private suspend fun captureToFile(camera: CameraDevice, reader: ImageReader, rotation: Int, file: File, handler: Handler): Boolean =
        suspendCancellableCoroutine { cont ->
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    file.writeBytes(bytes)
                    if (cont.isActive) cont.resume(true)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                } finally {
                    image.close()
                }
            }, handler)

            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.JPEG_ORIENTATION, rotation)
                    }.build()
                    session.capture(request, null, handler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (cont.isActive) cont.resume(false)
                }
            }, handler)
        }

    private fun pickCamera(manager: CameraManager, front: Boolean): String? {
        val wanted = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wanted
        } ?: manager.cameraIdList.firstOrNull()
    }

    private fun largestJpegSize(manager: CameraManager, id: String): Pair<Int, Int> {
        val config = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = config?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
        return (size?.width ?: 1280) to (size?.height ?: 720)
    }

    /** A directory destination gets a timestamped filename; a file path is used as-is. */
    private fun resolveFile(destination: String): File {
        val f = File(destination)
        val looksLikeFile = f.extension.isNotEmpty()
        return if (looksLikeFile) f else File(f, "IMG_${System.currentTimeMillis()}.jpg")
    }
}
