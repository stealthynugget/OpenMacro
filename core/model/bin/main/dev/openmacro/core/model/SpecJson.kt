package dev.openmacro.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Single JSON codec for every persisted spec, so storage and any future export agree. */
object SpecJson {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val constraintList = ListSerializer(ConstraintSpec.serializer())
    private val variableList = ListSerializer(Variable.serializer())

    fun encodeVariables(variables: List<Variable>): String = json.encodeToString(variableList, variables)
    fun decodeVariables(raw: String): List<Variable> = json.decodeFromString(variableList, raw)

    private val prettyJson = Json(json) { prettyPrint = true }

    fun encodeBackup(backup: MacroBackup): String = prettyJson.encodeToString(MacroBackup.serializer(), backup)
    fun decodeBackup(raw: String): MacroBackup = json.decodeFromString(MacroBackup.serializer(), raw)

    fun encode(spec: TriggerSpec): String = json.encodeToString(TriggerSpec.serializer(), spec)
    fun encode(spec: ActionSpec): String = json.encodeToString(ActionSpec.serializer(), spec)
    fun encode(spec: ConstraintSpec): String = json.encodeToString(ConstraintSpec.serializer(), spec)
    fun encodeConstraints(specs: List<ConstraintSpec>): String = json.encodeToString(constraintList, specs)

    // Persisted specs are decoded leniently: a spec this version can't read (removed or renamed in
    // an update, or damaged) becomes an inert `Unsupported` placeholder instead of throwing. One
    // unreadable row must never take down the whole app, which reads every macro at startup.

    fun decodeTrigger(raw: String): TriggerSpec =
        lenient(raw, { json.decodeFromString(TriggerSpec.serializer(), it) }) { type, text -> TriggerSpec.Unsupported(type, text) }

    fun decodeAction(raw: String): ActionSpec =
        lenient(raw, { json.decodeFromString(ActionSpec.serializer(), it) }) { type, text -> ActionSpec.Unsupported(type, text) }

    fun decodeConstraint(raw: String): ConstraintSpec =
        lenient(raw, { json.decodeFromString(ConstraintSpec.serializer(), it) }) { type, text -> ConstraintSpec.Unsupported(type, text) }

    /** Decodes element by element so one bad constraint doesn't discard its siblings. */
    fun decodeConstraints(raw: String): List<ConstraintSpec> {
        val array = try {
            json.parseToJsonElement(raw).jsonArray
        } catch (e: Exception) {
            return emptyList()
        }
        return array.map { decodeConstraint(it.toString()) }
    }

    private fun <T> lenient(raw: String, decode: (String) -> T, placeholder: (type: String, raw: String) -> T): T =
        try {
            decode(raw)
        } catch (e: SerializationException) {
            placeholder(originalType(raw), raw)
        } catch (e: IllegalArgumentException) {
            placeholder(originalType(raw), raw)
        }

    private fun originalType(raw: String): String = try {
        json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
