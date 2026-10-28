package dev.openmacro.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import dev.openmacro.core.model.catalog.Category
import dev.openmacro.core.model.catalog.Field
import dev.openmacro.core.model.catalog.ItemDef
import dev.openmacro.ui.catalog.EditorEnvironment
import dev.openmacro.ui.components.EmojiBadge

/** A grouped, searchable picker over catalog items, grouped by category. */
@Composable
fun <B : Any> CatalogPickerDialog(
    title: String,
    groups: Map<Category, List<ItemDef<B, *>>>,
    accent: Color,
    onPick: (ItemDef<B, *>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                groups.forEach { (category, defs) ->
                    item(key = "h-${category.name}") {
                        Text(
                            category.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(defs, key = { it.type.qualifiedName ?: it.title }) { def ->
                        ListItem(
                            headlineContent = { Text(def.title) },
                            supportingContent = { Text(def.description) },
                            leadingContent = { EmojiBadge(def.emoji, accent) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPick(def) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edits one spec via its catalog [fields], confirming with the edited value. */
@Composable
fun <B : Any> SpecConfigDialog(
    emoji: String,
    title: String,
    accent: Color,
    fields: List<Field<B>>,
    initial: B,
    env: EditorEnvironment,
    onDismiss: () -> Unit,
    extraContent: (@Composable (B, (B) -> Unit) -> Unit)? = null,
    onConfirm: (B) -> Unit,
) {
    var spec by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(emoji, accent)
                Spacer(Modifier.width(12.dp))
                Text(title)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (fields.none { it.visible(spec) }) {
                    Text("No options to configure.", style = MaterialTheme.typography.bodyMedium)
                }
                FieldList(fields, spec, env) { spec = it }
                extraContent?.invoke(spec) { spec = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(spec) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
