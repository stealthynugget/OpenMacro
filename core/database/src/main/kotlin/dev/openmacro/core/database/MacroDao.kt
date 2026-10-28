package dev.openmacro.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MacroDao {

    @Transaction
    @Query("SELECT * FROM macros ORDER BY name COLLATE NOCASE, id")
    abstract fun observeAll(): Flow<List<MacroWithItems>>

    @Transaction
    @Query("SELECT * FROM macros WHERE enabled = 1 ORDER BY id")
    abstract fun observeEnabled(): Flow<List<MacroWithItems>>

    @Transaction
    @Query("SELECT * FROM macros WHERE id = :id")
    abstract suspend fun get(id: Long): MacroWithItems?

    @Query("SELECT * FROM macros WHERE id = :id")
    abstract suspend fun getRow(id: Long): MacroEntity?

    @Query("UPDATE macros SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query("UPDATE macros SET runCount = runCount + 1, lastRunAt = :at WHERE id = :id")
    abstract suspend fun recordRun(id: Long, at: Long)

    @Query("DELETE FROM macros WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /**
     * Inserts or updates [macro] and replaces all of its children, preserving list order through
     * `position`. Returns the macro id.
     */
    @Transaction
    open suspend fun save(
        macro: MacroEntity,
        triggers: List<TriggerEntity>,
        actions: List<ActionEntity>,
        constraints: List<ConstraintEntity>,
    ): Long {
        val id = if (macro.id == 0L) {
            insertMacro(macro)
        } else {
            updateMacro(macro)
            deleteTriggers(macro.id)
            deleteActions(macro.id)
            deleteConstraints(macro.id)
            macro.id
        }
        insertTriggers(triggers.map { it.copy(id = 0, macroId = id) })
        insertActions(actions.map { it.copy(id = 0, macroId = id) })
        insertConstraints(constraints.map { it.copy(id = 0, macroId = id) })
        return id
    }

    @Insert
    protected abstract suspend fun insertMacro(macro: MacroEntity): Long

    @Update
    protected abstract suspend fun updateMacro(macro: MacroEntity)

    @Insert
    protected abstract suspend fun insertTriggers(items: List<TriggerEntity>)

    @Insert
    protected abstract suspend fun insertActions(items: List<ActionEntity>)

    @Insert
    protected abstract suspend fun insertConstraints(items: List<ConstraintEntity>)

    @Query("DELETE FROM triggers WHERE macroId = :macroId")
    protected abstract suspend fun deleteTriggers(macroId: Long)

    @Query("DELETE FROM actions WHERE macroId = :macroId")
    protected abstract suspend fun deleteActions(macroId: Long)

    @Query("DELETE FROM constraints WHERE macroId = :macroId")
    protected abstract suspend fun deleteConstraints(macroId: Long)
}
