package dev.openmacro.core.database

import dev.openmacro.core.model.ActionItem
import dev.openmacro.core.model.ConstraintItem
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.MacroBackup
import dev.openmacro.core.model.MacroRef
import dev.openmacro.core.model.MacroRefs
import dev.openmacro.core.model.SpecJson
import dev.openmacro.core.model.TriggerItem
import dev.openmacro.core.model.toExport
import dev.openmacro.core.model.toMacro
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Domain-level access to macros; hides the entity layout from the engine and UI. */
class MacroRepository(
    private val dao: MacroDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<Macro>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeEnabled(): Flow<List<Macro>> = dao.observeEnabled().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Macro? = dao.get(id)?.toDomain()

    /**
     * Saves the editable parts of [macro]. For an existing macro, the enabled flag and run
     * statistics are taken from the stored row, since they may have changed while it was being
     * edited (dashboard toggle, trigger firing).
     */
    suspend fun save(macro: Macro): Long {
        val now = clock()
        val existing = if (macro.id == 0L) null else dao.getRow(macro.id)
        val entity = MacroEntity(
            id = if (existing == null) 0 else macro.id,
            name = macro.name,
            description = macro.description,
            enabled = existing?.enabled ?: macro.enabled,
            constraintMode = macro.constraintMode,
            localVariables = macro.localVariables,
            runCount = existing?.runCount ?: 0,
            lastRunAt = existing?.lastRunAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return dao.save(
            macro = entity,
            triggers = macro.triggers.mapIndexed { i, t ->
                TriggerEntity(macroId = macro.id, position = i, enabled = t.enabled, spec = t.spec)
            },
            actions = macro.actions.mapIndexed { i, a ->
                ActionEntity(
                    macroId = macro.id,
                    position = i,
                    enabled = a.enabled,
                    spec = a.spec,
                    constraints = a.constraints,
                )
            },
            constraints = macro.constraints.mapIndexed { i, c ->
                ConstraintEntity(macroId = macro.id, position = i, enabled = c.enabled, spec = c.spec)
            },
        )
    }

    /** All macros as a backup JSON string. */
    suspend fun exportAll(): String {
        val macros = dao.observeAll().first().map { it.toDomain().toExport() }
        return SpecJson.encodeBackup(MacroBackup(macros = macros))
    }

    /**
     * Imports macros from a backup JSON string, inserting each as new. Returns the count imported.
     *
     * Macros that reference each other (Run macro, Cancel macro, "when X finishes", …) are
     * re-linked in a second pass: references are mapped from the backup's original ids to the new
     * ids, falling back to a name match against the existing macros.
     */
    suspend fun import(json: String): Int {
        val backup = SpecJson.decodeBackup(json)
        val newIds = backup.macros.map { save(it.toMacro()) }
        val idMap = backup.macros.zip(newIds).filter { (export, _) -> export.sourceId != 0L }
            .associate { (export, id) -> export.sourceId to id }
        val byName = dao.observeAll().first().associate { it.macro.name to it.macro.id }

        val remap: (MacroRef) -> MacroRef = { ref ->
            val target = idMap[ref.id] ?: byName[ref.name]
            if (target != null) MacroRef(target, ref.name) else ref
        }
        backup.macros.zip(newIds).forEach { (export, id) ->
            val original = export.toMacro().copy(id = id)
            val relinked = MacroRefs.remap(original, remap)
            if (relinked != original) save(relinked)
        }
        return backup.macros.size
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled, clock())

    suspend fun recordRun(id: Long) = dao.recordRun(id, clock())

    suspend fun delete(id: Long) = dao.delete(id)
}

internal fun MacroWithItems.toDomain(): Macro = Macro(
    id = macro.id,
    name = macro.name,
    description = macro.description,
    enabled = macro.enabled,
    constraintMode = macro.constraintMode,
    localVariables = macro.localVariables,
    triggers = triggers.sortedBy { it.position }.map { TriggerItem(spec = it.spec, enabled = it.enabled) },
    actions = actions.sortedBy { it.position }.map {
        ActionItem(spec = it.spec, constraints = it.constraints, enabled = it.enabled)
    },
    constraints = constraints.sortedBy { it.position }.map { ConstraintItem(spec = it.spec, enabled = it.enabled) },
    runCount = macro.runCount,
    lastRunAt = macro.lastRunAt,
)
