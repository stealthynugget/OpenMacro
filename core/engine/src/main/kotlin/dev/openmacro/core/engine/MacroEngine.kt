package dev.openmacro.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import dev.openmacro.core.database.GlobalVariableRepository
import dev.openmacro.core.database.MacroRepository
import dev.openmacro.core.engine.alarm.AlarmScheduler
import dev.openmacro.core.engine.perform.AndroidActionDispatcher
import dev.openmacro.core.engine.perform.EngineActions
import dev.openmacro.core.engine.platform.AndroidDeviceProbe
import dev.openmacro.core.engine.platform.Notifications
import dev.openmacro.core.engine.runtime.EngineEvalContext
import dev.openmacro.core.engine.runtime.GlobalVariables
import dev.openmacro.core.engine.runtime.MacroStateProvider
import dev.openmacro.core.engine.runtime.MagicResolver
import dev.openmacro.core.engine.runtime.RunScope
import dev.openmacro.core.engine.runtime.TriggerContext
import dev.openmacro.core.engine.work.EngineMaintenanceWorker
import dev.openmacro.core.model.ConstraintMode
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.MacroRef
import dev.openmacro.core.model.SourceKey
import dev.openmacro.core.model.SwitchMode
import dev.openmacro.core.model.TriggerEvent
import dev.openmacro.core.model.catalog.Catalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

interface EngineProvider {
    val engine: MacroEngine
}

val Context.engine: MacroEngine
    get() = (applicationContext as EngineProvider).engine

/**
 * Reactive dispatcher: receives [TriggerEvent]s, matches them against enabled macros via the
 * [Catalog], checks macro-level constraints and runs matching macros through the [MacroExecutor].
 */
class MacroEngine(
    private val context: Context,
    private val repository: MacroRepository,
    globalVariableRepository: GlobalVariableRepository,
    val settings: EngineSettings,
    private val scope: CoroutineScope,
) : EngineActions, MacroStateProvider {

    val globals = GlobalVariables(globalVariableRepository)
    private val deviceProbe = AndroidDeviceProbe(context)
    private val magic = MagicResolver(deviceProbe, globals)
    private val alarmScheduler = AlarmScheduler(context)

    private val executor = MacroExecutor(
        scope = scope,
        app = context.applicationContext,
        dispatcher = AndroidActionDispatcher(context.applicationContext, deviceProbe),
        device = deviceProbe,
        magic = magic,
        globals = globals,
        macroState = this,
        engineActions = this,
        onRunRecorded = repository::recordRun,
        log = EngineLog::i,
    )

    private val events = MutableSharedFlow<TriggerEvent>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val allMacros: StateFlow<List<Macro>> =
        repository.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val activeMacros: Flow<List<Macro>> =
        combine(allMacros, settings.engineEnabled) { macros, on -> if (on) macros.filter { it.enabled } else emptyList() }

    /** Concrete system subscriptions needed by active macros; drives the daemon's receivers. */
    val requiredSources: Flow<Set<SourceKey>> = activeMacros
        .map { macros ->
            macros.flatMap { m -> m.triggers.filter { it.enabled }.map { Catalog.trigger(it.spec).sourceOf(it.spec) } }.toSet()
        }
        .distinctUntilChanged()

    val activeMacroCount: Flow<Int> = activeMacros.map { it.size }.distinctUntilChanged()

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Notifications.ensureChannels(context)
        EngineMaintenanceWorker.schedule(context)
        scope.launch { globals.warmUp() }
        scope.launch { events.collect { dispatch(it) } }
        scope.launch { globals.changes.collect { submit(TriggerEvent.VariableChanged(it.name, it.value)) } }
        scope.launch {
            activeMacros
                .map { macros -> macros.flatMap { it.triggers.filter { t -> t.enabled }.map { t -> t.spec } }
                    .mapNotNull { Catalog.trigger(it).nextOccurrence(it, ZonedDateTime.now())?.let { true } } }
                .distinctUntilChanged()
                .collect { rescheduleAlarms() }
        }
        EngineLog.i("Engine started")
    }

    fun submit(event: TriggerEvent) {
        EngineLog.i("Event $event")
        events.tryEmit(event)
    }

    /** Matches [event] against active macros and starts every match. Returns how many started. */
    suspend fun dispatch(event: TriggerEvent): Int {
        var count = 0
        for (macro in activeMacros.first()) {
            val matching = macro.triggers.firstOrNull { it.enabled && Catalog.matches(it.spec, event) } ?: continue
            if (!macroConstraintsMet(macro)) {
                EngineLog.i("'${macro.name}' matched but its constraints are not met")
                continue
            }
            runMacro(macro, TriggerContext(event, Catalog.trigger(matching.spec).title))
            count++
        }
        return count
    }

    private fun macroConstraintsMet(macro: Macro): Boolean {
        val active = macro.constraints.filter { it.enabled }
        if (active.isEmpty()) return true
        val ctx = EngineEvalContext(deviceProbe, macro.id, null, magic, this)
        return when (macro.constraintMode) {
            ConstraintMode.ALL -> active.all { Catalog.evaluate(it.spec, ctx) }
            ConstraintMode.ANY -> active.any { Catalog.evaluate(it.spec, ctx) }
        }
    }

    private fun runMacro(macro: Macro, trigger: TriggerContext) {
        scope.launch {
            executor.execute(macro, ExecutionMode.TRIGGERED, trigger).await()
            if (macro.id != 0L) submit(TriggerEvent.MacroFinished(macro.id))
        }
    }

    fun testActions(macro: Macro): Deferred<ExecutionReport> = executor.execute(macro, ExecutionMode.TEST)

    // region EngineActions

    override suspend fun runMacro(ref: MacroRef, from: RunScope) {
        val macro = repository.get(ref.id) ?: return
        try {
            executor.execute(macro, ExecutionMode.TRIGGERED, TriggerContext(null, "Run by '${from.macroName}'")).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The child macro was cancelled; that must not cancel the macro that started it,
            // but if the caller itself was cancelled this rethrows.
            currentCoroutineContext().ensureActive()
            return
        }
        submit(TriggerEvent.MacroFinished(macro.id))
    }

    override suspend fun setMacroEnabled(ref: MacroRef, mode: SwitchMode) {
        if (!ref.isSet) return
        val current = allMacros.value.firstOrNull { it.id == ref.id }?.enabled ?: return
        val target = when (mode) {
            SwitchMode.ON -> true
            SwitchMode.OFF -> false
            SwitchMode.TOGGLE -> !current
        }
        repository.setEnabled(ref.id, target)
        submit(TriggerEvent.MacroEnabledChanged(ref.id, target))
    }

    override suspend fun cancelMacro(ref: MacroRef) {
        if (!ref.isSet) return
        val cancelled = executor.cancel(ref.id)
        EngineLog.i("Cancelled $cancelled running invocation(s) of macro ${ref.id}")
    }

    override suspend fun setEngineEnabled(enabled: Boolean) = settings.setEngineEnabled(enabled)

    override fun log(message: String) = EngineLog.i(message)

    // endregion

    // region MacroStateProvider

    override fun isMacroEnabled(id: Long): Boolean = allMacros.value.firstOrNull { it.id == id }?.enabled ?: false
    override fun isMacroRunning(id: Long): Boolean = executor.isRunning(id)
    override fun lastRunAt(id: Long): Long? = allMacros.value.firstOrNull { it.id == id }?.lastRunAt

    // endregion

    suspend fun rescheduleAlarms() {
        val now = ZonedDateTime.now()
        val next = activeMacros.first()
            .flatMap { it.triggers.filter { t -> t.enabled }.map { t -> t.spec } }
            .mapNotNull { Catalog.trigger(it).nextOccurrence(it, now) }
            .minOrNull()
        alarmScheduler.schedule(next)
    }

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExact()

    /** Handles the single engine alarm; dispatches the scheduled minute's tick, then re-arms. */
    suspend fun onAlarm(fireAtMillis: Long) {
        val zone = ZoneId.systemDefault()
        val instant = Instant.ofEpochMilli(fireAtMillis).atZone(zone)
        dispatch(
            TriggerEvent.TimeTick(
                hour = instant.hour,
                minute = instant.minute,
                dayOfWeek = instant.dayOfWeek.value,
                epochMinute = instant.toEpochSecond() / 60,
            ),
        )
        rescheduleAlarms()
    }

    fun handleBroadcast(pending: BroadcastReceiver.PendingResult, block: suspend MacroEngine.() -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                EngineLog.w("Broadcast handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
