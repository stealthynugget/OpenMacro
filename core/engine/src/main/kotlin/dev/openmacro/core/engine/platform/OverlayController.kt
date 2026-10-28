package dev.openmacro.core.engine.platform

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import dev.openmacro.core.engine.perform.ActionOutcome
import dev.openmacro.core.model.OverlayPosition

/** Shows floating text over other apps using a WindowManager overlay, auto-removed after a delay. */
internal object OverlayController {

    fun show(context: Context, text: String, seconds: Int, position: OverlayPosition): ActionOutcome {
        if (!Settings.canDrawOverlays(context)) return ActionOutcome.Failed("Display-over-other-apps permission is required")
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        main.post {
            val wm = app.getSystemService(WindowManager::class.java)
            val view = TextView(app).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(200, 0, 0, 0))
                setPadding(36, 24, 36, 24)
                textSize = 16f
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = when (position) {
                    OverlayPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    OverlayPosition.CENTER -> Gravity.CENTER
                    OverlayPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                y = 120
            }
            try {
                wm.addView(view, params)
                main.postDelayed({ runCatching { wm.removeView(view) } }, seconds.coerceIn(1, 120) * 1000L)
            } catch (e: Exception) {
                // Window add can fail (e.g. permission revoked between check and add); ignore.
            }
        }
        return ActionOutcome.Success
    }
}
