package dev.openmacro.core.engine.perform

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import dev.openmacro.core.engine.platform.Notifications
import dev.openmacro.core.model.ActionSpec as A
import dev.openmacro.core.model.IntentTarget

/** Apps, intents, notifications and phone actions. Returns null for specs handled elsewhere. */
internal class AppActions(private val app: Context) {

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? = when (spec) {
        is A.LaunchApp -> launchApp(spec.packageName)
        is A.OpenWebPage -> openUrl(ctx.render(spec.url))
        is A.LaunchHome -> launchHome()
        is A.OpenMaps -> openMaps(ctx.render(spec.query), spec.navigate)
        is A.DialNumber -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ctx.render(spec.number)}")))
        is A.LaunchAssistant -> startActivity(Intent(Intent.ACTION_VOICE_COMMAND))
        is A.OpenAppInfo ->
            if (spec.packageName.isBlank()) ActionOutcome.Failed("No app selected")
            else startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${spec.packageName}")))
        is A.UninstallApp ->
            if (spec.packageName.isBlank()) ActionOutcome.Failed("No app selected")
            else startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${spec.packageName}")))
        is A.SendIntent -> sendIntent(spec, ctx)
        is A.ShareText -> shareText(ctx.render(spec.text))
        is A.SetAlarm -> setAlarm(spec, ctx)
        is A.KillBackgroundApp -> killApp(spec.packageName)
        is A.ShowNotification -> Notifications.showMacroNotification(app, ctx.render(spec.title), ctx.render(spec.text))
        is A.ShowToast -> showToast(ctx.render(spec.text), spec.long)
        is A.MakeCall -> makeCall(ctx.render(spec.number))
        is A.SendSms -> sendSms(ctx.render(spec.number), ctx.render(spec.message))
        is A.AnswerCall -> answerCall()
        is A.EndCall -> endCall()
        is A.ClearNotifications -> clearNotifications(spec.packageName)
        is A.SetTimer -> setTimer(spec, ctx)
        is A.OpenCameraApp -> startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        is A.OpenSettings -> startActivity(Intent(settingsAction(spec.screen)))
        else -> null
    }

    private fun settingsAction(screen: dev.openmacro.core.model.SettingsScreen): String = when (screen) {
        dev.openmacro.core.model.SettingsScreen.MAIN -> android.provider.Settings.ACTION_SETTINGS
        dev.openmacro.core.model.SettingsScreen.WIFI -> android.provider.Settings.ACTION_WIFI_SETTINGS
        dev.openmacro.core.model.SettingsScreen.BLUETOOTH -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
        dev.openmacro.core.model.SettingsScreen.LOCATION -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
        dev.openmacro.core.model.SettingsScreen.DISPLAY -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
        dev.openmacro.core.model.SettingsScreen.SOUND -> android.provider.Settings.ACTION_SOUND_SETTINGS
        dev.openmacro.core.model.SettingsScreen.BATTERY -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
        dev.openmacro.core.model.SettingsScreen.APPS -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
        dev.openmacro.core.model.SettingsScreen.DATE -> android.provider.Settings.ACTION_DATE_SETTINGS
        dev.openmacro.core.model.SettingsScreen.SECURITY -> android.provider.Settings.ACTION_SECURITY_SETTINGS
        dev.openmacro.core.model.SettingsScreen.ACCESSIBILITY -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
    }

    private fun clearNotifications(packageName: String): ActionOutcome {
        val listener = dev.openmacro.core.engine.notifications.OpenMacroNotificationListener.instance
            ?: return ActionOutcome.Failed("Grant notification access to clear notifications")
        listener.clear(packageName)
        return ActionOutcome.Success
    }

    private fun startActivity(intent: Intent): ActionOutcome = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionOutcome.Success
    } catch (e: ActivityNotFoundException) {
        ActionOutcome.Failed("Nothing can handle that")
    }

    private fun launchApp(pkg: String): ActionOutcome {
        if (pkg.isBlank()) return ActionOutcome.Failed("No app selected")
        val intent = app.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionOutcome.Failed("$pkg is not installed or has no launcher")
        return startActivity(intent)
    }

    private fun openUrl(url: String): ActionOutcome {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return ActionOutcome.Failed("Invalid URL")
        return startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun launchHome(): ActionOutcome =
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    private fun openMaps(query: String, navigate: Boolean): ActionOutcome {
        if (query.isBlank()) return ActionOutcome.Failed("No place given")
        val encoded = Uri.encode(query)
        val uri = if (navigate) Uri.parse("google.navigation:q=$encoded") else Uri.parse("geo:0,0?q=$encoded")
        return startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun sendIntent(spec: A.SendIntent, ctx: ActionContext): ActionOutcome {
        val intent = Intent().apply {
            if (spec.action.isNotBlank()) action = ctx.render(spec.action)
            val data = ctx.render(spec.data)
            if (data.isNotBlank()) this.data = Uri.parse(data)
            if (spec.packageName.isNotBlank() && spec.className.isNotBlank()) {
                setClassName(ctx.render(spec.packageName), ctx.render(spec.className))
            } else if (spec.packageName.isNotBlank()) {
                `package` = ctx.render(spec.packageName)
            }
            parseExtras(ctx.render(spec.extras)).forEach { (k, v) -> putExtra(k, v) }
        }
        return try {
            when (spec.target) {
                IntentTarget.ACTIVITY -> startActivity(intent)
                IntentTarget.BROADCAST -> { app.sendBroadcast(intent); ActionOutcome.Success }
                IntentTarget.SERVICE -> { app.startService(intent); ActionOutcome.Success }
            }
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Intent failed")
        }
    }

    private fun shareText(text: String): ActionOutcome {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return startActivity(Intent.createChooser(intent, null))
    }

    private fun setAlarm(spec: A.SetAlarm, ctx: ActionContext): ActionOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, spec.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, spec.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val label = ctx.render(spec.label)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return startActivity(intent)
    }

    private fun setTimer(spec: A.SetTimer, ctx: ActionContext): ActionOutcome {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, spec.seconds.coerceAtLeast(1))
            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
        val label = ctx.render(spec.label)
        if (label.isNotBlank()) intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
        return startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun killApp(pkg: String): ActionOutcome {
        if (pkg.isBlank()) return ActionOutcome.Failed("No app selected")
        return try {
            app.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Not permitted to kill background apps")
        }
    }

    private fun showToast(text: String, long: Boolean): ActionOutcome {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(app, text, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
        }
        return ActionOutcome.Success
    }

    private fun makeCall(number: String): ActionOutcome {
        if (number.isBlank()) return ActionOutcome.Failed("No number")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        return try {
            startActivity(intent)
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Make calls permission is required")
        }
    }

    private fun sendSms(number: String, message: String): ActionOutcome {
        if (number.isBlank()) return ActionOutcome.Failed("No number")
        return try {
            val manager = app.getSystemService(android.telephony.SmsManager::class.java)
            manager.sendTextMessage(number, null, message, null, null)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Send SMS permission is required")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "SMS failed")
        }
    }

    private fun answerCall(): ActionOutcome {
        val telecom = app.getSystemService(android.telecom.TelecomManager::class.java)
        return try {
            telecom.acceptRingingCall()
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Answer calls permission is required")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Could not answer")
        }
    }

    @Suppress("DEPRECATION")
    private fun endCall(): ActionOutcome {
        val telecom = app.getSystemService(android.telecom.TelecomManager::class.java)
        return try {
            if (telecom.endCall()) ActionOutcome.Success else ActionOutcome.Failed("No call to end")
        } catch (e: SecurityException) {
            ActionOutcome.Failed("Answer calls permission is required")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Could not end call")
        }
    }

    private fun parseExtras(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim() to it[1].trim() }
}
