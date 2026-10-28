package dev.openmacro.ui.catalog

import androidx.compose.ui.graphics.Color
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.catalog.Catalog
import dev.openmacro.ui.theme.SectionPalette

fun TriggerSpec.emoji(): String = Catalog.trigger(this).emoji
fun TriggerSpec.title(): String = Catalog.trigger(this).title
fun TriggerSpec.summary(): String = Catalog.trigger(this).summarize(this)

fun ActionSpec.emoji(): String = Catalog.action(this).emoji
fun ActionSpec.title(): String = Catalog.action(this).title
fun ActionSpec.summary(): String = Catalog.action(this).summarize(this)

fun ConstraintSpec.emoji(): String = Catalog.constraint(this).emoji
fun ConstraintSpec.title(): String = Catalog.constraint(this).title
fun ConstraintSpec.summary(): String = Catalog.constraint(this).summarize(this)

/** Section accent for a rule element. */
enum class Section { TRIGGER, ACTION, CONSTRAINT }

fun SectionPalette.of(section: Section): Color = when (section) {
    Section.TRIGGER -> trigger
    Section.ACTION -> action
    Section.CONSTRAINT -> constraint
}
