package dev.openmacro.core.engine.runtime

/** Snapshot of other macros' runtime state, consulted by constraints. Supplied by the engine. */
interface MacroStateProvider {
    fun isMacroEnabled(id: Long): Boolean
    fun isMacroRunning(id: Long): Boolean
    fun lastRunAt(id: Long): Long?
}
