package dev.openmacro.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintMode
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.Variable

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val constraintMode: ConstraintMode,
    val localVariables: List<Variable> = emptyList(),
    val runCount: Long = 0,
    val lastRunAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "triggers",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macroId")],
)
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val position: Int,
    val enabled: Boolean,
    val spec: TriggerSpec,
)

@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macroId")],
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val position: Int,
    val enabled: Boolean,
    val spec: ActionSpec,
    /** Per-action gating; the action is skipped unless every constraint holds. */
    val constraints: List<ConstraintSpec>,
)

@Entity(
    tableName = "constraints",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macroId")],
)
data class ConstraintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val position: Int,
    val enabled: Boolean,
    val spec: ConstraintSpec,
)

/** Read model: a macro row together with all of its children. */
data class MacroWithItems(
    @Embedded val macro: MacroEntity,
    @Relation(parentColumn = "id", entityColumn = "macroId")
    val triggers: List<TriggerEntity>,
    @Relation(parentColumn = "id", entityColumn = "macroId")
    val actions: List<ActionEntity>,
    @Relation(parentColumn = "id", entityColumn = "macroId")
    val constraints: List<ConstraintEntity>,
)
