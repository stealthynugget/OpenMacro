package dev.openmacro.core.engine.runtime

import dev.openmacro.core.model.VarScope
import dev.openmacro.core.model.Variable
import java.util.concurrent.ConcurrentHashMap

/** Per-macro-run state: the macro's local variables plus the trigger that started it. */
class RunScope(
    val macroId: Long,
    val macroName: String,
    initialLocals: List<Variable>,
    val trigger: TriggerContext,
) {
    private val locals = ConcurrentHashMap<String, Variable>().apply {
        initialLocals.forEach { put(it.name, it) }
    }

    fun local(name: String): Variable? = locals[name]

    fun setLocal(variable: Variable) {
        locals[variable.name] = variable
    }

    fun clearLocal(name: String) {
        locals.remove(name)
    }

    fun localScope(scope: VarScope): Boolean = scope == VarScope.LOCAL
}
