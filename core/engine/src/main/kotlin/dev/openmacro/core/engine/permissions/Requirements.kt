package dev.openmacro.core.engine.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.catalog.Catalog

/** Bridges the model's [Requirement] set (derived from the catalog) to Android permission state. */
object Requirements {

    /** Requirements of the given macros' triggers and actions, plus the engine's own notifications. */
    fun neededBy(macros: List<Macro>): Set<Requirement> = buildSet {
        add(Requirement.NOTIFICATIONS)
        macros.forEach { macro ->
            macro.triggers.forEach { addAll(Catalog.trigger(it.spec).requirementsOf(it.spec)) }
            macro.actions.forEach { action ->
                addAll(Catalog.action(action.spec).requirementsOf(action.spec))
                action.constraints.forEach { addAll(Catalog.constraint(it).requirementsOf(it)) }
            }
            macro.constraints.forEach { addAll(Catalog.constraint(it.spec).requirementsOf(it.spec)) }
        }
    }

    fun requirementsOfMacro(macro: Macro): Set<Requirement> = neededBy(listOf(macro)) - Requirement.NOTIFICATIONS

    fun isGranted(context: Context, requirement: Requirement): Boolean = when (requirement) {
        Requirement.EXACT_ALARMS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Requirement.DND_ACCESS -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Requirement.WRITE_SETTINGS -> Settings.System.canWrite(context)
        Requirement.OVERLAY -> Settings.canDrawOverlays(context)
        Requirement.ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        Requirement.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
        Requirement.NOTIFICATION_ACCESS ->
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Requirement.SECURE_SETTINGS ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED ||
                dev.openmacro.core.engine.perform.ShizukuBackend.isGranted()
        else -> runtimePermission(requirement)?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true
    }

    /** The runtime permission to request in-app, or null if this requirement is a settings toggle. */
    fun runtimePermission(requirement: Requirement): String? = when (requirement) {
        Requirement.NOTIFICATIONS -> onApi(Build.VERSION_CODES.TIRAMISU, Manifest.permission.POST_NOTIFICATIONS)
        Requirement.BLUETOOTH_CONNECT -> onApi(Build.VERSION_CODES.S, Manifest.permission.BLUETOOTH_CONNECT)
        Requirement.PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
        Requirement.CALL_LOG -> Manifest.permission.READ_CALL_LOG
        Requirement.CALL_PHONE -> Manifest.permission.CALL_PHONE
        Requirement.ANSWER_CALLS -> onApi(Build.VERSION_CODES.O, Manifest.permission.ANSWER_PHONE_CALLS)
        Requirement.SEND_SMS -> Manifest.permission.SEND_SMS
        Requirement.RECEIVE_SMS -> Manifest.permission.RECEIVE_SMS
        Requirement.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        Requirement.BACKGROUND_LOCATION -> onApi(Build.VERSION_CODES.Q, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        Requirement.ACTIVITY_RECOGNITION -> onApi(Build.VERSION_CODES.Q, Manifest.permission.ACTIVITY_RECOGNITION)
        Requirement.CAMERA -> Manifest.permission.CAMERA
        else -> null
    }

    fun settingsIntent(context: Context, requirement: Requirement): Intent = when (requirement) {
        Requirement.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(context))
        } else {
            appDetails(context)
        }
        Requirement.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Requirement.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        Requirement.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        Requirement.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(context))
        Requirement.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(context))
        Requirement.ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri(context))
        } else {
            appDetails(context)
        }
        else -> appDetails(context)
    }

    /** True if Shizuku is running (so the UI can offer to request its permission in-app). */
    fun isShizukuAvailable(): Boolean = dev.openmacro.core.engine.perform.ShizukuBackend.isAvailable()

    /** Requests Shizuku permission (shows Shizuku's own dialog). Safe to call when unavailable. */
    fun requestShizukuPermission() = dev.openmacro.core.engine.perform.ShizukuBackend.requestPermission(requestCode = 4210)

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.substringBefore('/') == context.packageName }
    }

    private fun onApi(min: Int, permission: String): String? = if (Build.VERSION.SDK_INT >= min) permission else null
    private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")
    private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(context))
}
