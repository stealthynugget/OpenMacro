package dev.openmacro.core.engine.perform

import android.content.pm.PackageManager
import dev.openmacro.core.engine.EngineLog
import rikka.shizuku.Shizuku

/**
 * Runs shell commands at ADB/shell privilege through Shizuku, when the user has started Shizuku
 * (via ADB or root) and granted OpenMacro permission. No root needed. All on-device.
 *
 * The shell-command entry point is a hidden Shizuku API, so it is reached by reflection and
 * wrapped so that any absence (service not running, old Shizuku, API change) degrades to "null".
 */
internal object ShizukuBackend {

    /** True if the Shizuku service is running and reachable. */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (e: Throwable) {
        false
    }

    fun isGranted(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Fire-and-forget permission request; the result is delivered to the app's global listener. */
    fun requestPermission(requestCode: Int) {
        try {
            if (isAvailable() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            EngineLog.w("Shizuku permission request failed", e)
        }
    }

    /** Runs [command] via `sh -c`. Returns trimmed stdout+stderr on exit 0, or null on any failure. */
    fun run(command: String, timeoutMillis: Long = 15_000): String? {
        if (!isGranted()) return null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val finished = process.waitForMillis(timeoutMillis)
            if (!finished) {
                process.destroy()
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() == 0) output.trim() else null
        } catch (e: Throwable) {
            EngineLog.w("Shizuku command failed", e)
            null
        }
    }

    private fun Process.waitForMillis(millis: Long): Boolean {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            try {
                exitValue()
                return true
            } catch (e: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }
}
