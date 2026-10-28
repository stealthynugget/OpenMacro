package dev.openmacro.core.engine.perform

import android.content.Context
import dev.openmacro.core.engine.runtime.RunScope
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.MacroRef
import dev.openmacro.core.model.SwitchMode
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.Variable

sealed interface ActionOutcome {
    data object Success : ActionOutcome
    data object Skipped : ActionOutcome
    data class Failed(val reason: String) : ActionOutcome
}

/** Engine-level effects an action can request (defined here, implemented by the engine). */
interface EngineActions {
    suspend fun runMacro(ref: MacroRef, from: RunScope)
    suspend fun setMacroEnabled(ref: MacroRef, mode: SwitchMode)
    suspend fun cancelMacro(ref: MacroRef)
    suspend fun setEngineEnabled(enabled: Boolean)
    fun log(message: String)
}

/** Everything a single action performer may use. One instance per action invocation. */
class ActionContext(
    val app: Context,
    val scope: RunScope,
    val device: DeviceProbe,
    val engine: EngineActions,
    private val renderer: (String, RunScope) -> String,
    private val readVar: (VarRef, RunScope) -> Variable?,
    private val writeVar: suspend (VarRef, Variable) -> Unit,
    private val removeVar: suspend (VarRef, RunScope) -> Unit,
) {
    fun render(text: String): String = renderer(text, scope)

    fun variable(ref: VarRef): Variable? = readVar(ref, scope)

    /** Writes a variable, normalising [raw] (after magic-text rendering) to [type]. */
    suspend fun setVariable(ref: VarRef, type: VarType, raw: String) {
        if (ref.name.isBlank()) return
        val rendered = render(raw)
        val normalised = dev.openmacro.core.model.normaliseValue(type, rendered) ?: rendered
        writeVar(ref, Variable(ref.name, type, normalised))
    }

    suspend fun setVariableValue(ref: VarRef, value: String) {
        if (ref.name.isBlank()) return
        val type = variable(ref)?.type ?: VarType.STRING
        writeVar(ref, Variable(ref.name, type, value))
    }

    suspend fun clearVariable(ref: VarRef) {
        if (ref.name.isNotBlank()) removeVar(ref, scope)
    }

    fun log(message: String) = engine.log(message)
}

/** Performs one non-control-flow action. */
fun interface ActionPerformer {
    suspend fun perform(spec: ActionSpec, ctx: ActionContext): ActionOutcome
}

internal fun switchToTarget(mode: SwitchMode, current: Boolean): Boolean = when (mode) {
    SwitchMode.ON -> true
    SwitchMode.OFF -> false
    SwitchMode.TOGGLE -> !current
}
