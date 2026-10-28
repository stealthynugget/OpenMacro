package dev.openmacro.core.engine.service

import android.os.Build
import android.os.FileObserver
import dev.openmacro.core.model.FileEvent
import dev.openmacro.core.model.TriggerEvent
import java.io.File

/**
 * Watches [path] with [FileObserver] and reports create/modify/delete events.
 *
 * - If the path doesn't exist yet, the nearest existing ancestor is watched until it appears
 *   (a plain FileObserver on a missing path silently does nothing).
 * - If the watched folder is deleted, watching resumes from the ancestor so a re-created folder is
 *   picked up again.
 * - Bursts of the same event (one write produces several kernel events) are coalesced.
 */
internal object FileEventSource {

    private const val DEBOUNCE_MILLIS = 700L

    private const val ARM_MASK = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or
        FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

    private const val WAIT_MASK = FileObserver.CREATE or FileObserver.MOVED_TO

    fun open(path: String, emit: (TriggerEvent) -> Unit): Subscription {
        val target = File(path)
        val lock = Any()
        var active: FileObserver? = null
        var closed = false
        val lastEmit = HashMap<FileEvent, Long>()

        fun send(type: FileEvent) {
            val now = System.currentTimeMillis()
            synchronized(lock) {
                val last = lastEmit[type] ?: 0L
                if (now - last < DEBOUNCE_MILLIS) return
                lastEmit[type] = now
            }
            emit(TriggerEvent.FileChangedEvent(path, type))
        }

        lateinit var rewatch: () -> Unit

        fun observe(file: File, mask: Int, onEvent: (Int) -> Unit): FileObserver {
            val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(file, mask) {
                    override fun onEvent(event: Int, p: String?) = onEvent(event and ALL_EVENTS)
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(file.path, mask) {
                    override fun onEvent(event: Int, p: String?) = onEvent(event and ALL_EVENTS)
                }
            }
            observer.startWatching()
            return observer
        }

        rewatch = {
            synchronized(lock) {
                active?.stopWatching()
                active = null
                if (!closed) {
                    if (target.exists()) {
                        active = observe(target, ARM_MASK) { event ->
                            when (event) {
                                FileObserver.CREATE, FileObserver.MOVED_TO -> send(FileEvent.CREATED)
                                FileObserver.MODIFY -> send(FileEvent.MODIFIED)
                                FileObserver.DELETE, FileObserver.MOVED_FROM -> send(FileEvent.DELETED)
                                FileObserver.DELETE_SELF, FileObserver.MOVE_SELF -> {
                                    send(FileEvent.DELETED)
                                    rewatch() // wait for the folder to come back
                                }
                            }
                        }
                    } else {
                        // Wait on the nearest existing ancestor; re-evaluate whenever something appears in it.
                        val ancestor = generateSequence(target.parentFile) { it.parentFile }.firstOrNull { it.exists() }
                        if (ancestor != null) {
                            active = observe(ancestor, WAIT_MASK) { rewatch() }
                        }
                    }
                }
            }
        }

        rewatch()
        return Subscription {
            synchronized(lock) {
                closed = true
                active?.stopWatching()
                active = null
            }
        }
    }
}
