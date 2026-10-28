package dev.openmacro.core.engine

import dev.openmacro.core.engine.perform.ActionContext
import dev.openmacro.core.engine.perform.ActionOutcome
import dev.openmacro.core.engine.perform.AndroidActionDispatcher
import dev.openmacro.core.engine.perform.EngineActions
import dev.openmacro.core.engine.runtime.EngineEvalContext
import dev.openmacro.core.engine.runtime.GlobalVariables
import dev.openmacro.core.engine.runtime.MacroStateProvider
import dev.openmacro.core.engine.runtime.MagicResolver
import dev.openmacro.core.engine.runtime.RunScope
import dev.openmacro.core.engine.runtime.TriggerContext
import dev.openmacro.core.model.ActionItem
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.VarScope
import dev.openmacro.core.model.Variable
import dev.openmacro.core.model.catalog.BlockRole
import dev.openmacro.core.model.catalog.BlockStructure
import dev.openmacro.core.model.catalog.Catalog
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

enum class ExecutionMode { TRIGGERED, TEST }

data class ActionResult(val spec: ActionSpec, val outcome: ActionOutcome)

data class ExecutionReport(
    val macroId: Long,
    val results: List<ActionResult>,
    val rateLimited: Boolean = false,
) {
    val succeeded get() = results.count { it.outcome == ActionOutcome.Success }
    val skipped get() = results.count { it.outcome == ActionOutcome.Skipped }
    val failures get() = results.mapNotNull { (it.outcome as? ActionOutcome.Failed)?.reason }
}

/**
 * Interprets a macro's flat action list, honouring the If/Loop control-flow structure and each
 * action's own constraints. Invocations of one macro are serialised by a mutex; different macros
 * run concurrently.
 */
class MacroExecutor(
    private val scope: CoroutineScope,
    private val app: Context,
    private val dispatcher: AndroidActionDispatcher,
    private val device: DeviceProbe,
    private val magic: MagicResolver,
    private val globals: GlobalVariables,
    private val macroState: MacroStateProvider,
    private val engineActions: EngineActions,
    private val onRunRecorded: suspend (macroId: Long) -> Unit,
    private val rateLimiter: InvocationRateLimiter = InvocationRateLimiter(),
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val log: (String) -> Unit = {},
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val running = ConcurrentHashMap.newKeySet<Long>()

    fun isRunning(macroId: Long): Boolean = macroId in running

    /** Every queued-or-running invocation per macro, so [cancel] can stop them all. */
    private val invocations = ConcurrentHashMap<Long, MutableSet<Deferred<ExecutionReport>>>()

    fun execute(macro: Macro, mode: ExecutionMode, trigger: TriggerContext = TriggerContext.NONE): Deferred<ExecutionReport> {
        val job = scope.async {
            if (mode == ExecutionMode.TRIGGERED && !rateLimiter.tryAcquire(macro.id)) {
                log("Loop guard: '${macro.name}' exceeded its invocation rate; skipped")
                return@async ExecutionReport(macro.id, emptyList(), rateLimited = true)
            }
            locks.getOrPut(macro.id) { Mutex() }.withLock {
                running += macro.id
                try {
                    runActions(macro, mode, trigger)
                } finally {
                    running -= macro.id
                }
            }
        }
        val set = invocations.getOrPut(macro.id) { ConcurrentHashMap.newKeySet() }
        set += job
        job.invokeOnCompletion { set -= job }
        return job
    }

    /** Cancels every queued and running invocation of [macroId]; returns how many were cancelled. */
    fun cancel(macroId: Long): Int {
        val jobs = invocations[macroId]?.toList().orEmpty().filter { it.isActive }
        jobs.forEach { it.cancel() }
        return jobs.size
    }

    private suspend fun runActions(macro: Macro, mode: ExecutionMode, trigger: TriggerContext): ExecutionReport {
        log("Running '${macro.name}' ($mode, ${macro.actions.size} actions)")
        if (mode == ExecutionMode.TRIGGERED && macro.id != 0L) onRunRecorded(macro.id)

        val scopeState = RunScope(macro.id, macro.name, macro.localVariables, trigger)
        val ctx = buildContext(scopeState)
        val items = macro.actions
        val structure = BlockStructure.analyze(items.map { it.spec })
        if (!structure.isValid) structure.errors.forEach { log("Structure: $it") }

        val results = mutableListOf<ActionResult>()
        val loopRemaining = HashMap<Int, Int>()
        val branchTaken = HashMap<Int, Boolean>() // keyed by the block's endIf index
        var i = 0
        var steps = 0
        loop@ while (i in items.indices) {
            if (++steps > MAX_STEPS) {
                log("Step limit reached in '${macro.name}'")
                break
            }
            val item = items[i]
            if (!item.enabled) { i++; continue }
            val spec = item.spec
            when (Catalog.action(spec).block) {
                BlockRole.IF_START -> i = enterIf(spec, i, structure, branchTaken, ctx, scopeState)
                BlockRole.ELSE_IF -> i = enterElseIf(spec, i, structure, branchTaken, ctx, scopeState)
                BlockRole.ELSE -> i = enterElse(i, structure, branchTaken)
                BlockRole.IF_END -> { structure.endIf.getOrNull(i); branchTaken.remove(i); i++ }
                BlockRole.LOOP_START -> i = enterLoop(spec, i, structure, loopRemaining, ctx, scopeState)
                BlockRole.LOOP_END -> i = structure.loopPartner[i].let { if (it >= 0) it else i + 1 }
                BlockRole.LOOP_JUMP -> i = loopJump(spec, i, structure, loopRemaining)
                BlockRole.NONE -> {
                    if (spec is ActionSpec.StopMacro) { log("Stop macro"); break@loop }
                    if (spec is ActionSpec.Wait) {
                        val lo = spec.seconds.coerceAtLeast(0)
                        val seconds = if (spec.maxSeconds > lo) (lo..spec.maxSeconds).random() else lo
                        delay(seconds * 1000L)
                        results += ActionResult(spec, ActionOutcome.Success)
                        i++
                        continue@loop
                    }
                    results += runAction(item, ctx, scopeState)
                    i++
                }
            }
        }
        return ExecutionReport(macro.id, results)
    }

    private suspend fun runAction(item: ActionItem, ctx: ActionContext, scopeState: RunScope): ActionResult {
        val spec = item.spec
        val outcome = if (!conditionsMet(item.constraints, true, scopeState)) {
            ActionOutcome.Skipped
        } else {
            try {
                dispatcher.perform(spec, ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionOutcome.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        if (outcome is ActionOutcome.Failed) log("  ${spec::class.simpleName} failed: ${outcome.reason}")
        return ActionResult(spec, outcome)
    }

    // region Control flow

    private fun enterIf(
        spec: ActionSpec,
        i: Int,
        s: BlockStructure,
        branchTaken: HashMap<Int, Boolean>,
        ctx: ActionContext,
        scopeState: RunScope,
    ): Int {
        val if0 = spec as ActionSpec.If
        val end = s.endIf.getOrNull(i) ?: return i + 1
        return if (conditionsMet(if0.conditions, if0.matchAll, scopeState)) {
            branchTaken[end] = true
            i + 1
        } else {
            s.nextBranch[i].let { if (it >= 0) it else i + 1 }
        }
    }

    private fun enterElseIf(
        spec: ActionSpec,
        i: Int,
        s: BlockStructure,
        branchTaken: HashMap<Int, Boolean>,
        ctx: ActionContext,
        scopeState: RunScope,
    ): Int {
        val end = s.endIf.getOrNull(i) ?: return i + 1
        if (branchTaken[end] == true) return end // a previous branch ran; skip to End If
        val elseIf = spec as ActionSpec.ElseIf
        return if (conditionsMet(elseIf.conditions, elseIf.matchAll, scopeState)) {
            branchTaken[end] = true
            i + 1
        } else {
            s.nextBranch[i].let { if (it >= 0) it else end }
        }
    }

    private fun enterElse(i: Int, s: BlockStructure, branchTaken: HashMap<Int, Boolean>): Int {
        val end = s.endIf.getOrNull(i) ?: return i + 1
        return if (branchTaken[end] == true) end else i + 1
    }

    private fun enterLoop(
        spec: ActionSpec,
        i: Int,
        s: BlockStructure,
        loopRemaining: HashMap<Int, Int>,
        ctx: ActionContext,
        scopeState: RunScope,
    ): Int {
        val loop = spec as ActionSpec.Loop
        val end = s.loopPartner[i]
        val exit = if (end >= 0) end + 1 else i + 1
        return when (loop.mode) {
            dev.openmacro.core.model.LoopMode.COUNT -> {
                val remaining = loopRemaining.getOrPut(i) { loop.count.coerceAtLeast(0) }
                if (remaining > 0) { loopRemaining[i] = remaining - 1; i + 1 } else { loopRemaining.remove(i); exit }
            }
            dev.openmacro.core.model.LoopMode.WHILE ->
                if (conditionsMet(loop.conditions, loop.matchAll, scopeState)) i + 1 else exit
        }
    }

    private fun loopJump(spec: ActionSpec, i: Int, s: BlockStructure, loopRemaining: HashMap<Int, Int>): Int {
        val start = s.enclosingLoop.getOrNull(i) ?: return i + 1
        if (start < 0) return i + 1
        val end = s.loopPartner[start]
        return when (spec) {
            is ActionSpec.BreakLoop -> { loopRemaining.remove(start); if (end >= 0) end + 1 else i + 1 }
            is ActionSpec.ContinueLoop -> if (end >= 0) end else i + 1
            else -> i + 1
        }
    }

    // endregion

    private fun conditionsMet(conditions: List<ConstraintSpec>, matchAll: Boolean, scopeState: RunScope): Boolean {
        if (conditions.isEmpty()) return true
        val ctx = EngineEvalContext(device, scopeState.macroId, scopeState, magic, macroState, clock)
        return if (matchAll) conditions.all { Catalog.evaluate(it, ctx) } else conditions.any { Catalog.evaluate(it, ctx) }
    }

    private fun buildContext(scopeState: RunScope): ActionContext = ActionContext(
        app = app,
        scope = scopeState,
        device = device,
        engine = engineActions,
        renderer = { text, s -> magic.render(text, s) },
        readVar = { ref, s -> magic.variable(ref, s) },
        writeVar = { ref, variable -> writeVariable(ref, variable, scopeState) },
        removeVar = { ref, s -> removeVariable(ref, s) },
    )

    private suspend fun writeVariable(ref: VarRef, variable: Variable, scopeState: RunScope) {
        when (ref.scope) {
            VarScope.LOCAL -> scopeState.setLocal(variable)
            VarScope.GLOBAL -> globals.put(variable)
        }
    }

    private suspend fun removeVariable(ref: VarRef, scopeState: RunScope) {
        when (ref.scope) {
            VarScope.LOCAL -> scopeState.clearLocal(ref.name)
            VarScope.GLOBAL -> globals.delete(ref.name)
        }
    }

    private companion object {
        const val MAX_STEPS = 100_000
    }
}
