package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.EvalContext
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.SourceKey
import dev.openmacro.core.model.TriggerEvent
import dev.openmacro.core.model.TriggerSpec
import java.time.ZonedDateTime
import kotlin.reflect.KClass

enum class Category(val title: String) {
    BATTERY_POWER("Battery & power"),
    CONNECTIVITY("Connectivity"),
    DEVICE("Device"),
    DATE_TIME("Date & time"),
    PHONE_SMS("Call & SMS"),
    APPS("Apps & intents"),
    AUDIO_MEDIA("Audio & media"),
    DISPLAY("Screen & display"),
    NOTIFICATIONS("Notifications"),
    FILES("Files"),
    LOGIC("Conditions & loops"),
    VARIABLES("Variables & text"),
    MACRO("OpenMacro"),
    NETWORK("Web & network"),
    UI_AUTOMATION("UI automation"),
    SYSTEM("System & root"),
}

/** Role of an action in the control-flow structure. */
enum class BlockRole { NONE, IF_START, ELSE_IF, ELSE, IF_END, LOOP_START, LOOP_END, LOOP_JUMP }

/** Shared metadata for every catalog item. [B] is the hierarchy root, [S] the concrete spec. */
sealed class ItemDef<B : Any, S : B>(
    val type: KClass<S>,
    val category: Category,
    val emoji: String,
    val title: String,
    val description: String,
    val create: () -> S,
    private val summary: (S) -> String,
    val fields: List<Field<S>>,
    private val requirements: (S) -> Set<Requirement>,
    /** Only offered in the online flavor. */
    val needsNetwork: Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    fun summarize(spec: B): String = summary(spec as S)

    @Suppress("UNCHECKED_CAST")
    fun requirementsOf(spec: B): Set<Requirement> = requirements(spec as S)

    /** Fields typed against the hierarchy root, for generic rendering. */
    @Suppress("UNCHECKED_CAST")
    val erasedFields: List<Field<B>> get() = fields as List<Field<B>>
}

class TriggerDef<S : TriggerSpec>(
    type: KClass<S>,
    category: Category,
    emoji: String,
    title: String,
    description: String,
    create: () -> S,
    summary: (S) -> String,
    fields: List<Field<S>>,
    requirements: (S) -> Set<Requirement>,
    needsNetwork: Boolean,
    private val sourceFn: (S) -> SourceKey,
    private val matchesFn: (S, TriggerEvent) -> Boolean,
    private val scheduleFn: ((S, ZonedDateTime) -> ZonedDateTime?)?,
) : ItemDef<TriggerSpec, S>(type, category, emoji, title, description, create, summary, fields, requirements, needsNetwork) {
    @Suppress("UNCHECKED_CAST")
    fun sourceOf(spec: TriggerSpec): SourceKey = sourceFn(spec as S)

    @Suppress("UNCHECKED_CAST")
    fun matches(spec: TriggerSpec, event: TriggerEvent): Boolean = matchesFn(spec as S, event)

    /** Next alarm-driven occurrence strictly after [now], or null if not alarm-driven / never. */
    @Suppress("UNCHECKED_CAST")
    fun nextOccurrence(spec: TriggerSpec, now: ZonedDateTime): ZonedDateTime? = scheduleFn?.invoke(spec as S, now)
}

class ConstraintDef<S : ConstraintSpec>(
    type: KClass<S>,
    category: Category,
    emoji: String,
    title: String,
    description: String,
    create: () -> S,
    summary: (S) -> String,
    fields: List<Field<S>>,
    requirements: (S) -> Set<Requirement>,
    private val evaluateFn: (S, EvalContext) -> Boolean,
) : ItemDef<ConstraintSpec, S>(type, category, emoji, title, description, create, summary, fields, requirements, needsNetwork = false) {
    @Suppress("UNCHECKED_CAST")
    fun evaluate(spec: ConstraintSpec, context: EvalContext): Boolean = evaluateFn(spec as S, context)
}

class ActionDef<S : ActionSpec>(
    type: KClass<S>,
    category: Category,
    emoji: String,
    title: String,
    description: String,
    create: () -> S,
    summary: (S) -> String,
    fields: List<Field<S>>,
    requirements: (S) -> Set<Requirement>,
    needsNetwork: Boolean,
    val block: BlockRole,
) : ItemDef<ActionSpec, S>(type, category, emoji, title, description, create, summary, fields, requirements, needsNetwork)

// region Builders

private val none: (Any?) -> Set<Requirement> = { emptySet() }

inline fun <reified S : TriggerSpec> trigger(
    category: Category,
    emoji: String,
    title: String,
    description: String,
    noinline create: () -> S,
    noinline summary: (S) -> String,
    fields: List<Field<S>> = emptyList(),
    noinline source: (S) -> SourceKey,
    noinline matches: (S, TriggerEvent) -> Boolean,
    noinline requirements: (S) -> Set<Requirement> = { emptySet() },
    noinline schedule: ((S, ZonedDateTime) -> ZonedDateTime?)? = null,
    needsNetwork: Boolean = false,
) = TriggerDef(S::class, category, emoji, title, description, create, summary, fields, requirements, needsNetwork, source, matches, schedule)

inline fun <reified S : ConstraintSpec> constraint(
    category: Category,
    emoji: String,
    title: String,
    description: String,
    noinline create: () -> S,
    noinline summary: (S) -> String,
    fields: List<Field<S>> = emptyList(),
    noinline requirements: (S) -> Set<Requirement> = { emptySet() },
    noinline evaluate: (S, EvalContext) -> Boolean,
) = ConstraintDef(S::class, category, emoji, title, description, create, summary, fields, requirements, evaluate)

inline fun <reified S : ActionSpec> action(
    category: Category,
    emoji: String,
    title: String,
    description: String,
    noinline create: () -> S,
    noinline summary: (S) -> String,
    fields: List<Field<S>> = emptyList(),
    noinline requirements: (S) -> Set<Requirement> = { emptySet() },
    needsNetwork: Boolean = false,
    block: BlockRole = BlockRole.NONE,
) = ActionDef(S::class, category, emoji, title, description, create, summary, fields, requirements, needsNetwork, block)

// endregion
