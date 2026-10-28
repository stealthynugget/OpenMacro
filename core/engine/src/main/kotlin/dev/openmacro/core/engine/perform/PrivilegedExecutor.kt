package dev.openmacro.core.engine.perform

import android.content.Context
import android.provider.Settings
import dev.openmacro.core.model.SettingNamespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs protected operations through the best available backend: first a direct framework write
 * when the app holds the permission, otherwise a root shell. (Shizuku is a planned third backend.)
 */
internal class PrivilegedExecutor(private val app: Context) {

    /** Runs [command] as root. Returns the trimmed output on success, or null on failure/timeout. */
    suspend fun root(command: String, timeoutSeconds: Int = 15): String? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = withTimeoutOrNull(timeoutSeconds * 1000L) { process.inputStream.bufferedReader().readText() }
            if (output == null) {
                process.destroy()
                return@withContext null
            }
            if (process.waitFor() == 0) output.trim() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Runs a privileged shell command through the best available backend: Shizuku (ADB-level, no
     * root) first, then root. Returns the trimmed output, or null if no backend succeeded.
     */
    suspend fun shell(command: String, timeoutSeconds: Int = 15): String? = withContext(Dispatchers.IO) {
        ShizukuBackend.run(command, timeoutSeconds * 1000L) ?: root(command, timeoutSeconds)
    }

    /** True if a root shell is available. */
    suspend fun hasRoot(): Boolean = root("id", timeoutSeconds = 5)?.contains("uid=0") == true

    /** True if Shizuku is running and OpenMacro is permitted to use it. */
    fun hasShizuku(): Boolean = ShizukuBackend.isGranted()

    /**
     * Writes a setting via the framework when permitted, else via `settings put` as root.
     * Returns a short error message on failure, or null on success.
     */
    suspend fun writeSetting(namespace: SettingNamespace, key: String, value: String): String? {
        if (key.isBlank()) return "No setting key"
        if (writeViaFramework(namespace, key, value)) return null
        val ns = namespace.name.lowercase()
        if (shell("settings put $ns $key $value") != null) return null
        return when (namespace) {
            SettingNamespace.SYSTEM ->
                "Android only lets apps change a fixed list of System settings (and needs the Modify system settings permission). Use Shizuku or root for other keys."
            else -> "Needs the WRITE_SECURE_SETTINGS permission, Shizuku, or root."
        }
    }

    /** True if the framework accepted the write. Any refusal (permission, allow-list) returns false. */
    private fun writeViaFramework(namespace: SettingNamespace, key: String, value: String): Boolean = try {
        val resolver = app.contentResolver
        when (namespace) {
            SettingNamespace.SYSTEM -> Settings.System.canWrite(app) && Settings.System.putString(resolver, key, value)
            SettingNamespace.SECURE -> Settings.Secure.putString(resolver, key, value)
            SettingNamespace.GLOBAL -> Settings.Global.putString(resolver, key, value)
        }
    } catch (e: Exception) {
        false
    }
}
