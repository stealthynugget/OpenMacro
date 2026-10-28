@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.openmacro.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.VarScope
import dev.openmacro.core.model.catalog.BoolField
import dev.openmacro.core.model.catalog.Catalog
import dev.openmacro.core.model.catalog.ChoiceField
import dev.openmacro.core.model.catalog.ConditionsField
import dev.openmacro.core.model.catalog.Field
import dev.openmacro.core.model.catalog.InfoField
import dev.openmacro.core.model.catalog.IntField
import dev.openmacro.core.model.catalog.LocationField
import dev.openmacro.core.model.catalog.AppField
import dev.openmacro.core.model.catalog.MacroField
import dev.openmacro.core.model.catalog.MultiChoiceField
import dev.openmacro.core.model.catalog.TextField as CatalogTextField
import dev.openmacro.core.model.catalog.TimeField
import dev.openmacro.core.model.catalog.UriField
import dev.openmacro.core.model.catalog.UriKind
import dev.openmacro.core.model.catalog.VariableField
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.openmacro.ui.catalog.EditorEnvironment
import dev.openmacro.ui.catalog.LocationStatus
import dev.openmacro.ui.catalog.PickKind
import dev.openmacro.ui.catalog.emoji
import dev.openmacro.ui.catalog.summary
import dev.openmacro.ui.components.DayOfWeekPicker
import dev.openmacro.ui.components.EmojiBadge
import dev.openmacro.ui.components.IntSlider
import dev.openmacro.ui.components.SegmentedChoice
import dev.openmacro.ui.components.TimeButton
import dev.openmacro.ui.theme.LocalSectionPalette

/** Renders every visible field of a spec, emitting the edited spec on each change. */
@Composable
fun <B : Any> FieldList(fields: List<Field<B>>, spec: B, env: EditorEnvironment, onChange: (B) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        fields.filter { it.visible(spec) }.forEach { field ->
            FieldEditor(field, spec, env, onChange)
        }
    }
}

@Composable
private fun <B : Any> FieldEditor(field: Field<B>, spec: B, env: EditorEnvironment, onChange: (B) -> Unit) {
    when (field) {
        is InfoField -> Text(field.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        is ChoiceField -> {
            val selected = field.get(spec)
            if (field.options.size <= 3) {
                LabeledColumn(field.label) {
                    SegmentedChoice(field.options, selected, { field.optionLabel(it) }) { onChange(field.set(spec, it)) }
                }
            } else {
                LabeledColumn(field.label) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        field.options.forEach { option ->
                            FilterChip(
                                selected = option == selected,
                                onClick = { onChange(field.set(spec, option)) },
                                label = { Text(field.optionLabel(option)) },
                            )
                        }
                    }
                }
            }
        }

        is MultiChoiceField -> LabeledColumn(field.label) {
            val selected = field.get(spec)
            if (field.options == (1..7).toList()) {
                DayOfWeekPicker(selected) { onChange(field.set(spec, it)) }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = option in selected,
                            onClick = {
                                val next = if (option in selected) selected - option else selected + option
                                onChange(field.set(spec, next))
                            },
                            label = { Text(field.optionLabel(option)) },
                        )
                    }
                }
            }
        }

        is IntField -> {
            val value = field.get(spec)
            if (field.slider) {
                IntSlider(field.label, value, field.range, { onChange(field.set(spec, it)) }) { "$it${field.unit}" }
            } else {
                OutlinedTextField(
                    value = value.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { onChange(field.set(spec, it.coerceIn(field.range.first, field.range.last))) }
                    },
                    label = { Text(field.label + if (field.unit.isNotBlank()) " (${field.unit})" else "") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is CatalogTextField -> OutlinedTextField(
            value = field.get(spec),
            onValueChange = { onChange(field.set(spec, it)) },
            label = { Text(field.label) },
            placeholder = if (field.hint.isNotBlank()) ({ Text(field.hint) }) else null,
            supportingText = if (field.magicText) ({ Text("Supports {tokens} like {battery}, {lv=name}") }) else null,
            singleLine = !field.multiline,
            minLines = if (field.multiline) 2 else 1,
            modifier = Modifier.fillMaxWidth(),
        )

        is BoolField -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.label, modifier = Modifier.weight(1f))
            Switch(checked = field.get(spec), onCheckedChange = { onChange(field.set(spec, it)) })
        }

        is TimeField -> TimeButton(field.label, field.hour(spec), field.minute(spec)) { h, m -> onChange(field.set(spec, h, m)) }

        is VariableField -> VariablePicker(field.label, field.get(spec), env, field.optional) { onChange(field.set(spec, it)) }

        is MacroField -> MacroPicker(field.label, field.get(spec), env) { onChange(field.set(spec, it)) }

        is AppField -> AppPicker(field.label, field.get(spec), env, field.allowAny) { onChange(field.set(spec, it)) }

        is UriField -> UriPicker(field.label, field.kind, field.get(spec), env) { onChange(field.set(spec, it)) }

        is ConditionsField -> ConditionsEditor(field, spec, env, onChange)

        is LocationField -> LocationEditor(field, spec, env, onChange)
    }
}

@Composable
private fun <B : Any> LocationEditor(field: LocationField<B>, spec: B, env: EditorEnvironment, onChange: (B) -> Unit) {
    LabeledColumn(field.label) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = field.lat(spec).toString(),
                onValueChange = { it.toDoubleOrNull()?.let { v -> onChange(field.setLatLng(spec, v, field.lng(spec))) } },
                label = { Text("Latitude") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = field.lng(spec).toString(),
                onValueChange = { it.toDoubleOrNull()?.let { v -> onChange(field.setLatLng(spec, field.lat(spec), v)) } },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (field.showRadius) {
            IntSlider("Radius", field.radius(spec), field.radiusRange, { onChange(field.setRadius(spec, it)) }) { "$it m" }
        }

        val context = LocalContext.current
        // The fix arrives asynchronously; apply it to the latest spec so edits made meanwhile survive.
        val latestSpec by rememberUpdatedState(spec)
        var status by remember { mutableStateOf<LocationStatus?>(null) }
        OutlinedButton(
            onClick = {
                env.requestLocation { s ->
                    status = s
                    if (s is LocationStatus.Found) onChange(field.setLatLng(latestSpec, s.latitude, s.longitude))
                }
            },
            enabled = status !is LocationStatus.Searching,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Use current location") }

        when (val s = status) {
            LocationStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Getting current location…", style = MaterialTheme.typography.bodySmall)
            }
            is LocationStatus.Found -> Text(
                "Location set ✓",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            is LocationStatus.Failed -> Column {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (s.offerSettings) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }) { Text("Open location settings") }
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun LabeledColumn(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun VariablePicker(label: String, current: VarRef, env: EditorEnvironment, optional: Boolean, onChange: (VarRef) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    LabeledColumn(label) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (current.name.isBlank()) (if (optional) "(none)" else "Choose variable") else current.toString())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (optional) DropdownMenuItem(text = { Text("(none)") }, onClick = { expanded = false; onChange(VarRef()) })
                env.variables.forEach { ref ->
                    DropdownMenuItem(text = { Text(ref.toString()) }, onClick = { expanded = false; onChange(ref) })
                }
                DropdownMenuItem(text = { Text("➕ New variable…") }, onClick = { expanded = false; creating = true })
            }
        }
    }
    if (creating) NewVariableDialog(onDismiss = { creating = false }) { creating = false; onChange(it) }
}

@Composable
private fun NewVariableDialog(onDismiss: () -> Unit, onCreate: (VarRef) -> Unit) {
    var name by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(VarScope.GLOBAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text("Name") }, singleLine = true)
                SegmentedChoice(listOf(VarScope.GLOBAL, VarScope.LOCAL), scope, { if (it == VarScope.GLOBAL) "Global" else "Local" }) { scope = it }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(VarRef(scope, name)) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MacroPicker(label: String, current: dev.openmacro.core.model.MacroRef, env: EditorEnvironment, onChange: (dev.openmacro.core.model.MacroRef) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LabeledColumn(label) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current.name.ifBlank { "Choose macro" })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (env.macros.isEmpty()) DropdownMenuItem(text = { Text("No other macros") }, onClick = { expanded = false })
                env.macros.forEach { m ->
                    DropdownMenuItem(text = { Text(m.name) }, onClick = { expanded = false; onChange(dev.openmacro.core.model.MacroRef(m.id, m.name)) })
                }
            }
        }
    }
}

@Composable
private fun AppPicker(label: String, current: String, env: EditorEnvironment, allowAny: Boolean, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LabeledColumn(label) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current.ifBlank { if (allowAny) "Any app" else "Choose app" })
        }
    }
    if (open) AppPickerDialog(env, allowAny, onDismiss = { open = false }) { open = false; onChange(it) }
}

@Composable
private fun AppPickerDialog(env: EditorEnvironment, allowAny: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps = remember { env.apps() }
    val filtered = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.width(8.dp))
                LazyColumn {
                    if (allowAny) item { ListItem(headlineContent = { Text("Any app") }, modifier = Modifier.clickable { onPick("") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
                    items(filtered) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.clickable { onPick(app.packageName) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UriPicker(label: String, kind: UriKind, current: String, env: EditorEnvironment, onChange: (String) -> Unit) {
    LabeledColumn(label) {
        OutlinedButton(
            onClick = { env.pickUri(if (kind == UriKind.SOUND) PickKind.SOUND else PickKind.IMAGE) { onChange(it) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (current.isBlank()) "Choose…" else current.substringAfterLast('/').ifBlank { "Selected" })
        }
    }
}

/** Nested list of conditions, with an optional all/any switch. Recurses for Logic constraints. */
@Composable
private fun <B : Any> ConditionsEditor(field: ConditionsField<B>, spec: B, env: EditorEnvironment, onChange: (B) -> Unit) {
    val palette = LocalSectionPalette.current
    val conditions = field.get(spec)
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int?, ConstraintSpec>?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(field.label, style = MaterialTheme.typography.labelLarge, color = palette.constraint)
            if (field.matchAll != null && conditions.size > 1) {
                SegmentedChoice(listOf(true, false), field.matchAll!!(spec), { if (it) "Match all" else "Match any" }) {
                    field.setMatchAll?.let { set -> onChange(set(spec, it)) }
                }
            }
            if (conditions.isEmpty()) {
                Text("No conditions — always true.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            conditions.forEachIndexed { index, constraint ->
                ListItem(
                    headlineContent = { Text(constraint.summary()) },
                    leadingContent = { EmojiBadge(constraint.emoji(), palette.constraint) },
                    trailingContent = {
                        IconButton(onClick = { onChange(field.set(spec, conditions.filterIndexed { i, _ -> i != index })) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { editing = index to constraint },
                )
            }
            TextButton(onClick = { picking = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = palette.constraint)
                Spacer(Modifier.width(8.dp))
                Text("Add condition", color = palette.constraint)
            }
        }
    }

    if (picking) {
        ConstraintPickerDialog(env, onDismiss = { picking = false }) { picking = false; editing = null to it }
    }
    editing?.let { (index, constraint) ->
        ConstraintConfigDialog(constraint, env, onDismiss = { editing = null }) { updated ->
            val next = if (index == null) conditions + updated else conditions.mapIndexed { i, c -> if (i == index) updated else c }
            onChange(field.set(spec, next))
            editing = null
        }
    }
}

/** Picker + config dialog for a nested constraint (used by ConditionsEditor). */
@Composable
fun ConstraintPickerDialog(env: EditorEnvironment, onDismiss: () -> Unit, onPick: (ConstraintSpec) -> Unit) {
    val palette = LocalSectionPalette.current
    CatalogPickerDialog(
        title = "Add condition",
        groups = Catalog.constraintsByCategory(env.includeNetwork),
        accent = palette.constraint,
        onPick = { onPick(it.create()) },
        onDismiss = onDismiss,
    )
}

@Composable
fun ConstraintConfigDialog(initial: ConstraintSpec, env: EditorEnvironment, onDismiss: () -> Unit, onConfirm: (ConstraintSpec) -> Unit) {
    val palette = LocalSectionPalette.current
    val def = Catalog.constraint(initial)
    SpecConfigDialog(
        emoji = def.emoji,
        title = def.title,
        accent = palette.constraint,
        fields = def.erasedFields,
        initial = initial,
        env = env,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
