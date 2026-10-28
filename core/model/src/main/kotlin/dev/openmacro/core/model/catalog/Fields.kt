package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.MacroRef
import dev.openmacro.core.model.VarRef

/**
 * Declarative description of one editable property of a spec. The app renders each kind
 * generically, so adding a catalog item never needs bespoke UI code.
 */
sealed class Field<S>(val label: String, val visible: (S) -> Boolean)

class ChoiceField<S>(
    label: String,
    val options: List<Any>,
    val optionLabel: (Any) -> String,
    val get: (S) -> Any,
    val set: (S, Any) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

/** Multi-select chips over a fixed set of ints (days of week, months, …). */
class MultiChoiceField<S>(
    label: String,
    val options: List<Int>,
    val optionLabel: (Int) -> String,
    val get: (S) -> Set<Int>,
    val set: (S, Set<Int>) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

class IntField<S>(
    label: String,
    val range: IntRange,
    val unit: String,
    val get: (S) -> Int,
    val set: (S, Int) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible) {
    /** Wide ranges are better typed than dragged. */
    val slider: Boolean get() = range.last - range.first <= 200
}

class TextField<S>(
    label: String,
    val get: (S) -> String,
    val set: (S, String) -> S,
    val multiline: Boolean,
    val magicText: Boolean,
    val hint: String,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

class BoolField<S>(
    label: String,
    val get: (S) -> Boolean,
    val set: (S, Boolean) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

class TimeField<S>(
    label: String,
    val hour: (S) -> Int,
    val minute: (S) -> Int,
    val set: (S, Int, Int) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

/** A nested list of constraints, optionally with an all/any switch. */
class ConditionsField<S>(
    label: String,
    val get: (S) -> List<ConstraintSpec>,
    val set: (S, List<ConstraintSpec>) -> S,
    val matchAll: ((S) -> Boolean)?,
    val setMatchAll: ((S, Boolean) -> S)?,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

class VariableField<S>(
    label: String,
    val get: (S) -> VarRef,
    val set: (S, VarRef) -> S,
    val optional: Boolean,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

class MacroField<S>(
    label: String,
    val get: (S) -> MacroRef,
    val set: (S, MacroRef) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

/** An installed app's package name; blank means "any app" where [allowAny]. */
class AppField<S>(
    label: String,
    val get: (S) -> String,
    val set: (S, String) -> S,
    val allowAny: Boolean,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

/** A content URI picked through the system picker (sound or image), persisted across reboots. */
class UriField<S>(
    label: String,
    val kind: UriKind,
    val get: (S) -> String,
    val set: (S, String) -> S,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

enum class UriKind { SOUND, IMAGE }

/** Latitude/longitude/radius for a geofence, with a "use current location" affordance. */
class LocationField<S>(
    label: String,
    val lat: (S) -> Double,
    val lng: (S) -> Double,
    val radius: (S) -> Int,
    val radiusRange: IntRange,
    val setLatLng: (S, Double, Double) -> S,
    val setRadius: (S, Int) -> S,
    val showRadius: Boolean,
    visible: (S) -> Boolean,
) : Field<S>(label, visible)

/** Static explanatory text. */
class InfoField<S>(text: String, visible: (S) -> Boolean) : Field<S>(text, visible)

// region Builders

private val always: (Any?) -> Boolean = { true }

@Suppress("UNCHECKED_CAST")
fun <S, E> choice(
    label: String,
    options: List<E>,
    get: (S) -> E,
    set: (S, E) -> S,
    labelOf: (E) -> String = { humanize(it) },
    visible: (S) -> Boolean = always,
): Field<S> = ChoiceField(label, options as List<Any>, { labelOf(it as E) }, { get(it) as Any }, { s, v -> set(s, v as E) }, visible)

fun <S> toggle(
    label: String,
    onLabel: String,
    offLabel: String,
    get: (S) -> Boolean,
    set: (S, Boolean) -> S,
    visible: (S) -> Boolean = always,
): Field<S> = choice(label, listOf(true, false), get, set, { if (it) onLabel else offLabel }, visible)

fun <S> multiChoice(
    label: String,
    options: List<Int>,
    labelOf: (Int) -> String,
    get: (S) -> Set<Int>,
    set: (S, Set<Int>) -> S,
    visible: (S) -> Boolean = always,
): Field<S> = MultiChoiceField(label, options, labelOf, get, set, visible)

fun <S> number(
    label: String,
    range: IntRange,
    get: (S) -> Int,
    set: (S, Int) -> S,
    unit: String = "",
    visible: (S) -> Boolean = always,
): Field<S> = IntField(label, range, unit, get, set, visible)

fun <S> text(
    label: String,
    get: (S) -> String,
    set: (S, String) -> S,
    multiline: Boolean = false,
    magicText: Boolean = true,
    hint: String = "",
    visible: (S) -> Boolean = always,
): Field<S> = TextField(label, get, set, multiline, magicText, hint, visible)

fun <S> switch(label: String, get: (S) -> Boolean, set: (S, Boolean) -> S, visible: (S) -> Boolean = always): Field<S> =
    BoolField(label, get, set, visible)

fun <S> time(label: String, hour: (S) -> Int, minute: (S) -> Int, set: (S, Int, Int) -> S, visible: (S) -> Boolean = always): Field<S> =
    TimeField(label, hour, minute, set, visible)

fun <S> conditions(
    label: String,
    get: (S) -> List<ConstraintSpec>,
    set: (S, List<ConstraintSpec>) -> S,
    matchAll: ((S) -> Boolean)? = null,
    setMatchAll: ((S, Boolean) -> S)? = null,
    visible: (S) -> Boolean = always,
): Field<S> = ConditionsField(label, get, set, matchAll, setMatchAll, visible)

fun <S> variable(label: String, get: (S) -> VarRef, set: (S, VarRef) -> S, optional: Boolean = false, visible: (S) -> Boolean = always): Field<S> =
    VariableField(label, get, set, optional, visible)

fun <S> macro(label: String, get: (S) -> MacroRef, set: (S, MacroRef) -> S, visible: (S) -> Boolean = always): Field<S> =
    MacroField(label, get, set, visible)

fun <S> app(label: String, get: (S) -> String, set: (S, String) -> S, allowAny: Boolean = false, visible: (S) -> Boolean = always): Field<S> =
    AppField(label, get, set, allowAny, visible)

fun <S> uri(label: String, kind: UriKind, get: (S) -> String, set: (S, String) -> S, visible: (S) -> Boolean = always): Field<S> =
    UriField(label, kind, get, set, visible)

fun <S> location(
    label: String,
    lat: (S) -> Double,
    lng: (S) -> Double,
    radius: (S) -> Int = { 0 },
    setLatLng: (S, Double, Double) -> S,
    setRadius: (S, Int) -> S = { s, _ -> s },
    radiusRange: IntRange = 50..5000,
    showRadius: Boolean = true,
    visible: (S) -> Boolean = always,
): Field<S> = LocationField(label, lat, lng, radius, radiusRange, setLatLng, setRadius, showRadius, visible)

fun <S> info(text: String, visible: (S) -> Boolean = always): Field<S> = InfoField(text, visible)

/** `DEVICE_CONNECTED` -> "Device connected". */
fun humanize(value: Any?): String = when (value) {
    is Enum<*> -> value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    is Boolean -> if (value) "Yes" else "No"
    else -> value.toString()
}

// endregion
