package dev.openmacro.core.model

import kotlinx.serialization.Serializable

/**
 * Serialisable form of a macro for local backup/restore and sharing. Runtime fields (database id,
 * run count, timestamps, UI keys) are intentionally omitted so an imported macro starts fresh.
 */
@Serializable
data class MacroExport(
    /** The macro's id in the source database; lets import re-link macros that reference each other. */
    val sourceId: Long = 0,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val constraintMode: ConstraintMode = ConstraintMode.ALL,
    val triggers: List<TriggerExport> = emptyList(),
    val actions: List<ActionExport> = emptyList(),
    val constraints: List<ConstraintExport> = emptyList(),
    val localVariables: List<Variable> = emptyList(),
)

@Serializable
data class TriggerExport(val spec: TriggerSpec, val enabled: Boolean = true)

@Serializable
data class ActionExport(val spec: ActionSpec, val constraints: List<ConstraintSpec> = emptyList(), val enabled: Boolean = true)

@Serializable
data class ConstraintExport(val spec: ConstraintSpec, val enabled: Boolean = true)

/** A backup file: a versioned bundle of macros. */
@Serializable
data class MacroBackup(val format: String = "openmacro", val version: Int = 1, val macros: List<MacroExport>)

fun Macro.toExport(): MacroExport = MacroExport(
    sourceId = id,
    name = name,
    description = description,
    enabled = enabled,
    constraintMode = constraintMode,
    triggers = triggers.map { TriggerExport(it.spec, it.enabled) },
    actions = actions.map { ActionExport(it.spec, it.constraints, it.enabled) },
    constraints = constraints.map { ConstraintExport(it.spec, it.enabled) },
    localVariables = localVariables,
)

/** Rebuilds a domain macro from an export; [id] stays 0 so it is inserted as a new macro. */
fun MacroExport.toMacro(): Macro = Macro(
    name = name,
    description = description,
    enabled = enabled,
    constraintMode = constraintMode,
    triggers = triggers.map { TriggerItem(it.spec, it.enabled) },
    actions = actions.map { ActionItem(it.spec, it.constraints, it.enabled) },
    constraints = constraints.map { ConstraintItem(it.spec, it.enabled) },
    localVariables = localVariables,
)
