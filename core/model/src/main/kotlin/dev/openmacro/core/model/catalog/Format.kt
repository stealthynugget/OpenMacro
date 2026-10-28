package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.Comparison
import dev.openmacro.core.model.MacroRef
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.VarRef
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatTime(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(timeFormatter)

fun dayName(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun monthName(month: Int): String = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun formatDays(days: Set<Int>): String = when (days) {
    TriggerSpec.ALL_DAYS -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    emptySet<Int>() -> "Never"
    else -> days.sorted().joinToString(", ") { dayName(it) }
}

fun formatSet(values: Set<Int>, name: (Int) -> String): String =
    if (values.isEmpty()) "none" else values.sorted().joinToString(", ") { name(it) }

fun Comparison.symbol(): String = if (this == Comparison.AT_LEAST) "≥" else "≤"

fun MacroRef.display(): String = if (isSet) "“$name”" else "(choose macro)"

fun VarRef.display(): String = if (name.isBlank()) "(choose variable)" else toString()

fun String.quoted(empty: String = "…"): String = "“${ifBlank { empty }}”"

fun String.orAny(any: String = "any"): String = ifBlank { any }

fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 3600 == 0 -> "${seconds / 3600}h"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
