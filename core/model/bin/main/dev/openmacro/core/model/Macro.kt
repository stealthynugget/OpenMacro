package dev.openmacro.core.model

import java.util.UUID

/**
 * A complete automation rule. [id] is 0 until the macro is first saved.
 *
 * Each item carries a `key` used only by the UI for stable list identity. It is regenerated on
 * load and never persisted, because a save rewrites all of a macro's child rows.
 */
data class Macro(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val constraintMode: ConstraintMode = ConstraintMode.ALL,
    val triggers: List<TriggerItem> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
    val constraints: List<ConstraintItem> = emptyList(),
    val localVariables: List<Variable> = emptyList(),
    val runCount: Long = 0,
    val lastRunAt: Long? = null,
)

data class TriggerItem(
    val spec: TriggerSpec,
    val enabled: Boolean = true,
    val key: String = newKey(),
)

/** An action plus its own constraints; the action is skipped unless all of them hold. */
data class ActionItem(
    val spec: ActionSpec,
    val constraints: List<ConstraintSpec> = emptyList(),
    val enabled: Boolean = true,
    val key: String = newKey(),
)

data class ConstraintItem(
    val spec: ConstraintSpec,
    val enabled: Boolean = true,
    val key: String = newKey(),
)

/** How a macro's own constraints combine: all must hold, or any one is enough. */
enum class ConstraintMode { ALL, ANY }

private fun newKey(): String = UUID.randomUUID().toString()
