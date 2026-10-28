package dev.openmacro.core.model

/**
 * Rewrites every reference to another macro (by database id) inside a macro. Used by import so that
 * links like "Run macro X" or "when X finishes" still point at the right macro after restoring a
 * backup, where every macro receives a new id.
 */
object MacroRefs {

    fun remap(macro: Macro, map: (MacroRef) -> MacroRef): Macro = macro.copy(
        triggers = macro.triggers.map { it.copy(spec = trigger(it.spec, map)) },
        actions = macro.actions.map { item ->
            item.copy(spec = action(item.spec, map), constraints = item.constraints.map { constraint(it, map) })
        },
        constraints = macro.constraints.map { it.copy(spec = constraint(it.spec, map)) },
    )

    fun trigger(spec: TriggerSpec, map: (MacroRef) -> MacroRef): TriggerSpec = when (spec) {
        is TriggerSpec.MacroFinished -> spec.copy(macro = map(spec.macro))
        is TriggerSpec.MacroEnabledChanged -> spec.copy(macro = map(spec.macro))
        else -> spec
    }

    fun constraint(spec: ConstraintSpec, map: (MacroRef) -> MacroRef): ConstraintSpec = when (spec) {
        is ConstraintSpec.MacroEnabled -> spec.copy(macro = map(spec.macro))
        is ConstraintSpec.MacroRunning -> spec.copy(macro = map(spec.macro))
        is ConstraintSpec.Logic -> spec.copy(constraints = spec.constraints.map { constraint(it, map) })
        else -> spec
    }

    fun action(spec: ActionSpec, map: (MacroRef) -> MacroRef): ActionSpec = when (spec) {
        is ActionSpec.RunMacro -> spec.copy(macro = map(spec.macro))
        is ActionSpec.EnableMacro -> spec.copy(macro = map(spec.macro))
        is ActionSpec.CancelMacro -> spec.copy(macro = map(spec.macro))
        is ActionSpec.If -> spec.copy(conditions = spec.conditions.map { constraint(it, map) })
        is ActionSpec.ElseIf -> spec.copy(conditions = spec.conditions.map { constraint(it, map) })
        is ActionSpec.Loop -> spec.copy(conditions = spec.conditions.map { constraint(it, map) })
        else -> spec
    }
}
