package dev.openmacro.core.model

import kotlinx.serialization.Serializable

enum class VarType { BOOLEAN, INTEGER, DECIMAL, STRING }

enum class VarScope { LOCAL, GLOBAL }

/** A named value. Values are stored as text and interpreted according to [type]. */
@Serializable
data class Variable(val name: String, val type: VarType, val value: String) {
    fun asBoolean(): Boolean = value.equals("true", ignoreCase = true)
    fun asNumber(): Double? = value.trim().toDoubleOrNull()
}

/** Reference to a variable from an action or constraint. */
@Serializable
data class VarRef(val scope: VarScope = VarScope.LOCAL, val name: String = "") {
    override fun toString(): String = if (scope == VarScope.GLOBAL) "{v=$name}" else "{lv=$name}"
}

/** Reference to another macro by its stable database id; [name] is a display fallback. */
@Serializable
data class MacroRef(val id: Long = 0, val name: String = "") {
    val isSet: Boolean get() = id != 0L
}

/** Normalises a raw value for [type], e.g. "1.0" -> "1" for integers. Returns null if invalid. */
fun normaliseValue(type: VarType, raw: String): String? = when (type) {
    VarType.BOOLEAN -> when (raw.trim().lowercase()) {
        "true", "1", "yes", "on" -> "true"
        "false", "0", "no", "off", "" -> "false"
        else -> null
    }
    VarType.INTEGER -> raw.trim().toDoubleOrNull()?.toLong()?.toString()
    VarType.DECIMAL -> raw.trim().toDoubleOrNull()?.toString()
    VarType.STRING -> raw
}
