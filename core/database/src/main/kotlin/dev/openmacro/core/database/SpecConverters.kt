package dev.openmacro.core.database

import androidx.room.TypeConverter
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.SpecJson
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.Variable

/** Stores the polymorphic spec hierarchies as discriminated JSON text columns. */
class SpecConverters {
    @TypeConverter
    fun triggerToJson(spec: TriggerSpec): String = SpecJson.encode(spec)

    @TypeConverter
    fun jsonToTrigger(raw: String): TriggerSpec = SpecJson.decodeTrigger(raw)

    @TypeConverter
    fun actionToJson(spec: ActionSpec): String = SpecJson.encode(spec)

    @TypeConverter
    fun jsonToAction(raw: String): ActionSpec = SpecJson.decodeAction(raw)

    @TypeConverter
    fun constraintToJson(spec: ConstraintSpec): String = SpecJson.encode(spec)

    @TypeConverter
    fun jsonToConstraint(raw: String): ConstraintSpec = SpecJson.decodeConstraint(raw)

    @TypeConverter
    fun constraintsToJson(specs: List<ConstraintSpec>): String = SpecJson.encodeConstraints(specs)

    @TypeConverter
    fun jsonToConstraints(raw: String): List<ConstraintSpec> = SpecJson.decodeConstraints(raw)

    @TypeConverter
    fun variablesToJson(variables: List<Variable>): String = SpecJson.encodeVariables(variables)

    @TypeConverter
    fun jsonToVariables(raw: String): List<Variable> = SpecJson.decodeVariables(raw)
}
