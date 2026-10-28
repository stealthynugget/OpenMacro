package dev.openmacro.core.engine

import android.util.Log

/** Local-only logging (logcat). Nothing is ever persisted off-device or transmitted. */
internal object EngineLog {
    const val TAG = "OpenMacro"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }
}
