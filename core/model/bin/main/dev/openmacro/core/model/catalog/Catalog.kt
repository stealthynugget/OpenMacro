package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.EvalContext
import dev.openmacro.core.model.EventSource
import dev.openmacro.core.model.SourceKey
import dev.openmacro.core.model.TriggerEvent
import dev.openmacro.core.model.TriggerSpec
import kotlin.reflect.KClass

/**
 * The single registry over every trigger, constraint and action definition. All behaviour and
 * all editor metadata is looked up here by spec class, so the engine and UI never switch on
 * concrete types themselves.
 */
object Catalog {

    val triggers: List<TriggerDef<*>> = TriggerCatalog.all
    val constraints: List<ConstraintDef<*>> = ConstraintCatalog.all
    val actions: List<ActionDef<*>> = ActionCatalog.all

    private val triggerByClass: Map<KClass<*>, TriggerDef<*>> = triggers.associateBy { it.type }
    private val constraintByClass: Map<KClass<*>, ConstraintDef<*>> = constraints.associateBy { it.type }
    private val actionByClass: Map<KClass<*>, ActionDef<*>> = actions.associateBy { it.type }

    init {
        // Fail fast at startup if a spec was added without registering it.
        check(triggerByClass.size == triggers.size) { "Duplicate trigger definition" }
        check(constraintByClass.size == constraints.size) { "Duplicate constraint definition" }
        check(actionByClass.size == actions.size) { "Duplicate action definition" }
    }

    // Definitions for the "can't read this any more" placeholders. They are deliberately not part of
    // the picker lists above, so a user can never add one, but every lookup still resolves.
    private val unsupportedTrigger = trigger<TriggerSpec.Unsupported>(
        Category.MACRO, "⚠️", "Unsupported trigger", "No longer available in this version",
        create = { TriggerSpec.Unsupported() },
        summary = { "Unsupported trigger (${it.originalType})" },
        source = { SourceKey(EventSource.INTERNAL) },
        matches = { _, _ -> false },
    )

    private val unsupportedConstraint = constraint<ConstraintSpec.Unsupported>(
        Category.MACRO, "⚠️", "Unsupported constraint", "No longer available in this version",
        create = { ConstraintSpec.Unsupported() },
        summary = { "Unsupported constraint (${it.originalType})" },
    ) { _, _ -> false }

    private val unsupportedAction = action<ActionSpec.Unsupported>(
        Category.MACRO, "⚠️", "Unsupported action", "No longer available in this version",
        create = { ActionSpec.Unsupported() },
        summary = { "Unsupported action (${it.originalType})" },
    )

    fun trigger(spec: TriggerSpec): TriggerDef<*> =
        triggerByClass[spec::class] ?: if (spec is TriggerSpec.Unsupported) unsupportedTrigger else error("No TriggerDef for ${spec::class.simpleName}")

    fun constraint(spec: ConstraintSpec): ConstraintDef<*> =
        constraintByClass[spec::class] ?: if (spec is ConstraintSpec.Unsupported) unsupportedConstraint else error("No ConstraintDef for ${spec::class.simpleName}")

    fun action(spec: ActionSpec): ActionDef<*> =
        actionByClass[spec::class] ?: if (spec is ActionSpec.Unsupported) unsupportedAction else error("No ActionDef for ${spec::class.simpleName}")

    fun matches(spec: TriggerSpec, event: TriggerEvent): Boolean = trigger(spec).matches(spec, event)

    fun evaluate(spec: ConstraintSpec, context: EvalContext): Boolean = constraint(spec).evaluate(spec, context)

    /** Catalog items for a picker, grouped and ordered by [Category], hiding network items offline. */
    fun triggersByCategory(includeNetwork: Boolean): Map<Category, List<TriggerDef<*>>> = group(triggers, includeNetwork)
    fun constraintsByCategory(includeNetwork: Boolean): Map<Category, List<ConstraintDef<*>>> = group(constraints, includeNetwork)
    fun actionsByCategory(includeNetwork: Boolean): Map<Category, List<ActionDef<*>>> = group(actions, includeNetwork)

    private fun <D : ItemDef<*, *>> group(items: List<D>, includeNetwork: Boolean): Map<Category, List<D>> =
        items.filter { includeNetwork || !it.needsNetwork }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
}
