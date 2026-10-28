package dev.openmacro.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.openmacro.core.model.ActionItem
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.catalog.Catalog
import dev.openmacro.ui.catalog.EditorEnvironment
import dev.openmacro.ui.catalog.emoji
import dev.openmacro.ui.catalog.summary
import dev.openmacro.ui.components.EmojiBadge
import dev.openmacro.ui.theme.LocalSectionPalette

/** Edits an action's spec fields plus its own "only run if" constraints, returning the full item. */
@Composable
fun ActionEditDialog(initial: ActionItem, env: EditorEnvironment, onDismiss: () -> Unit, onConfirm: (ActionItem) -> Unit) {
    val palette = LocalSectionPalette.current
    val def = Catalog.action(initial.spec)
    var spec by remember(initial) { mutableStateOf(initial.spec) }
    var constraints by remember(initial) { mutableStateOf(initial.constraints) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int?, ConstraintSpec>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(def.emoji, palette.action)
                Spacer(Modifier.width(12.dp))
                Text(def.title)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldList(def.erasedFields, spec, env) { spec = it }

                HorizontalDivider()
                Text("Only run this action if…", style = MaterialTheme.typography.titleSmall, color = palette.constraint)
                if (constraints.isEmpty()) {
                    Text(
                        "Always runs. Add a condition to skip this step when it isn't met.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                constraints.forEachIndexed { index, constraint ->
                    ListItem(
                        headlineContent = { Text(constraint.summary()) },
                        leadingContent = { EmojiBadge(constraint.emoji(), palette.constraint) },
                        trailingContent = {
                            IconButton(onClick = { constraints = constraints.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove condition")
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
        },
        confirmButton = { TextButton(onClick = { onConfirm(initial.copy(spec = spec, constraints = constraints)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        ConstraintPickerDialog(env, onDismiss = { picking = false }) { picking = false; editing = null to it }
    }
    editing?.let { (index, constraint) ->
        ConstraintConfigDialog(constraint, env, onDismiss = { editing = null }) { updated ->
            constraints = if (index == null) constraints + updated else constraints.mapIndexed { i, c -> if (i == index) updated else c }
            editing = null
        }
    }
}
