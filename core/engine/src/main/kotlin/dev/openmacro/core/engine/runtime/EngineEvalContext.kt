package dev.openmacro.core.engine.runtime

import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.EvalContext
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.Variable
import java.time.ZonedDateTime

/**
 * Evaluation context for constraints and magic text. [scope] is null when evaluating a macro's
 * own constraints before a run begins (no local variables yet).
 */
class EngineEvalContext(
    override val device: DeviceProbe,
    override val currentMacroId: Long,
    private val scope: RunScope?,
    private val magic: MagicResolver,
    private val macroState: MacroStateProvider,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) : EvalContext {

    override fun now(): ZonedDateTime = clock()

    override fun variable(ref: VarRef): Variable? = magic.variable(ref, scope)

    override fun render(text: String): String = magic.render(text, scope)

    override fun isMacroEnabled(id: Long): Boolean = macroState.isMacroEnabled(id)

    override fun isMacroRunning(id: Long): Boolean = macroState.isMacroRunning(id)

    override fun lastRunAt(id: Long): Long? = macroState.lastRunAt(id)
}
