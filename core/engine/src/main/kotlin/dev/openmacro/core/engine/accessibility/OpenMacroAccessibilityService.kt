package dev.openmacro.core.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.engine.engine
import dev.openmacro.core.model.TriggerEvent
import dev.openmacro.core.model.UiButton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Opt-in accessibility service. It performs UI automation on request (taps, swipes, global
 * buttons, text input, screen reading) and reports the foreground app to the engine. It does
 * nothing until the user enables it in system Accessibility settings, and never transmits data.
 */
class OpenMacroAccessibilityService : AccessibilityService() {

    private var lastForeground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        EngineLog.i("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore our own windows
        if (pkg == lastForeground) return
        lastForeground = pkg
        foregroundApp = pkg
        runCatching { engine.submit(TriggerEvent.AppForegroundChanged(pkg)) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        EngineLog.i("Accessibility service disconnected")
        super.onDestroy()
    }

    // region Automation

    fun pressButton(button: UiButton): Boolean {
        val action = when (button) {
            UiButton.BACK -> GLOBAL_ACTION_BACK
            UiButton.HOME -> GLOBAL_ACTION_HOME
            UiButton.RECENTS -> GLOBAL_ACTION_RECENTS
            UiButton.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            UiButton.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            UiButton.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN
            UiButton.SCREENSHOT -> GLOBAL_ACTION_TAKE_SCREENSHOT
            UiButton.POWER_DIALOG -> GLOBAL_ACTION_POWER_DIALOG
            UiButton.SPLIT_SCREEN -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
        }
        return performGlobalAction(action)
    }

    suspend fun tap(x: Int, y: Int, longPress: Boolean): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val duration = if (longPress) 600L else 50L
        return dispatch(GestureDescription.StrokeDescription(path, 0, duration))
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatch(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(10, 60_000).toLong()))
    }

    private suspend fun dispatch(stroke: GestureDescription.StrokeDescription): Boolean {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    /** Clicks (or long-clicks) the first clickable node whose text/description contains [text]. */
    fun tapElement(text: String, longPress: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val needle = text.trim()
        val match = findNode(root) { node ->
            val label = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            needle.isNotEmpty() && label.contains(needle, ignoreCase = true)
        } ?: return false
        val clickable = generateSequence(match) { it.parent }.firstOrNull { it.isClickable || it.isLongClickable } ?: match
        val action = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        return clickable.performAction(action)
    }

    /** Sets [text] on the focused editable node (or the first editable node found). */
    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: findNode(root) { it.isEditable }
            ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        collectText(root, out)
        return out.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out)
    }

    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            findNode(node.getChild(i), predicate)?.let { return it }
        }
        return null
    }

    // endregion

    companion object {
        @Volatile
        var instance: OpenMacroAccessibilityService? = null
            private set

        /** Package of the most recent foreground app seen while connected, or null. */
        @Volatile
        var foregroundApp: String? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
