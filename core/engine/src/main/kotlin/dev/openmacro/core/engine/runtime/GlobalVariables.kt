package dev.openmacro.core.engine.runtime

import dev.openmacro.core.database.GlobalVariableRepository
import dev.openmacro.core.model.Variable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Cached, persisted global variables. Reads hit the in-memory cache so constraints stay
 * synchronous; writes persist and emit a change so variable-changed triggers can fire.
 */
class GlobalVariables(private val repository: GlobalVariableRepository) {
    private val cache = ConcurrentHashMap<String, Variable>()
    private val writeLock = Mutex()
    private val _changes = MutableSharedFlow<Variable>(extraBufferCapacity = 32)
    val changes: SharedFlow<Variable> = _changes.asSharedFlow()

    suspend fun warmUp() {
        repository.getAll().forEach { cache[it.name] = it }
    }

    fun get(name: String): Variable? = cache[name]

    fun snapshot(): List<Variable> = cache.values.sortedBy { it.name.lowercase() }

    suspend fun put(variable: Variable) {
        writeLock.withLock {
            val previous = cache[variable.name]
            cache[variable.name] = variable
            repository.put(variable)
            if (previous?.value != variable.value) _changes.emit(variable)
        }
    }

    suspend fun delete(name: String) {
        writeLock.withLock {
            cache.remove(name)
            repository.delete(name)
        }
    }
}
