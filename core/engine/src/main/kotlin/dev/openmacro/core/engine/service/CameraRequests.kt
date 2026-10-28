package dev.openmacro.core.engine.service

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/** Bridges a photo request from an action to the short-lived camera foreground service and back. */
internal object CameraRequests {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    fun register(id: String): CompletableDeferred<String?> =
        CompletableDeferred<String?>().also { pending[id] = it }

    /** Completes the request with the saved file path (or null on failure) and clears it. */
    fun complete(id: String, path: String?) {
        pending.remove(id)?.complete(path)
    }
}
