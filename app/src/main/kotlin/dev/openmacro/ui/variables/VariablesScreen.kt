@file:OptIn(ExperimentalMaterial3Api::class)

package dev.openmacro.ui.variables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.Variable
import dev.openmacro.ui.components.SegmentedChoice
import kotlinx.coroutines.launch

@Composable
fun VariablesScreen(viewModel: VariablesViewModel, onClose: () -> Unit) {
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Variable?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global variables") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New variable") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (variables.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No global variables yet. They hold values your macros can read and write with {v=name}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
                items(variables, key = { it.name }) { variable ->
                    ListItem(
                        headlineContent = { Text(variable.name) },
                        supportingContent = {
                            Text(
                                "${variable.type.name.lowercase()} = ${variable.value.ifBlank { "(empty)" }}",
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.delete(variable.name) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${variable.name}")
                            }
                        },
                        modifier = Modifier.clickable { editing = variable },
                    )
                }
            }
        }
    }

    val dialogVariable = editing
    if (creating || dialogVariable != null) {
        VariableDialog(
            initial = dialogVariable,
            onDismiss = { creating = false; editing = null },
            onSave = { name, type, value ->
                viewModel.put(name, type, value) { error ->
                    if (error == null) {
                        creating = false
                        editing = null
                    } else {
                        scope.launch { snackbar.showSnackbar(error) }
                    }
                }
            },
        )
    }
}

@Composable
private fun VariableDialog(initial: Variable?, onDismiss: () -> Unit, onSave: (String, VarType, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: VarType.STRING) }
    var value by remember { mutableStateOf(initial?.value ?: "") }
    val isNew = initial == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New variable" else "Edit ${initial!!.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = isNew, // renaming would orphan references, so names are fixed after creation
                    modifier = Modifier.fillMaxWidth(),
                )
                SegmentedChoice(VarType.entries, type, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { type = it }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    singleLine = type != VarType.STRING,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, type, value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
