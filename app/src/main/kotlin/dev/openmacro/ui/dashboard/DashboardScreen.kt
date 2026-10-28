@file:OptIn(ExperimentalMaterial3Api::class)

package dev.openmacro.ui.dashboard

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openmacro.core.engine.permissions.Requirements
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.Requirement
import dev.openmacro.ui.catalog.emoji
import dev.openmacro.ui.catalog.summary
import dev.openmacro.ui.theme.LocalSectionPalette

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenMacro: (Long) -> Unit,
    onCreateMacro: () -> Unit,
    onOpenVariables: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Grants can change outside the app (settings screens), so re-check on every resume.
    var grantCheck by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        grantCheck++
        onPauseOrDispose {}
    }
    val missing = remember(state.macros, grantCheck) {
        Requirements.neededBy(state.macros.filter { it.enabled }).filterNot { Requirements.isGranted(context, it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        grantCheck++
    }
    val grant: (Requirement) -> Unit = { requirement ->
        val permission = Requirements.runtimePermission(requirement)
        when {
            permission != null -> permissionLauncher.launch(permission)
            // Secure settings: prefer Shizuku's in-app permission dialog when Shizuku is running.
            requirement == Requirement.SECURE_SETTINGS && Requirements.isShizukuAvailable() -> {
                Requirements.requestShizukuPermission()
                grantCheck++
            }
            else -> try {
                context.startActivity(Requirements.settingsIntent(context, requirement))
            } catch (_: ActivityNotFoundException) {
                // No settings screen on this build; the banner stays visible.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (dev.openmacro.BuildConfig.NETWORK) "OpenMacro Online" else "OpenMacro")
                        Text(
                            statusLine(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    BackupMenu(viewModel, snackbar, onOpenVariables)
                    Switch(
                        checked = state.engineEnabled,
                        onCheckedChange = viewModel::setEngineEnabled,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateMacro,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New macro") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (missing.isNotEmpty()) {
                item(key = "requirements") { RequirementsBanner(missing, grant) }
            }
            when {
                !state.loaded -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.macros.isEmpty() -> item(key = "empty") { EmptyState(onCreateMacro) }
                else -> items(state.macros, key = { it.id }) { macro ->
                    MacroCard(
                        macro = macro,
                        onClick = { onOpenMacro(macro.id) },
                        onToggle = { viewModel.setMacroEnabled(macro.id, it) },
                    )
                }
            }
            item(key = "privacy") { PrivacyFooter() }
        }
    }
}

private fun statusLine(state: DashboardState): String {
    val active = state.macros.count { it.enabled }
    return when {
        !state.engineEnabled -> "Engine paused"
        state.serviceRunning -> "Engine running · $active active"
        else -> "Engine starting…"
    }
}

@Composable
private fun BackupMenu(viewModel: DashboardViewModel, snackbar: SnackbarHostState, onOpenVariables: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.isSuccess
            scope.launch { snackbar.showSnackbar(if (ok) "Macros exported" else "Export failed") }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (json == null) {
                scope.launch { snackbar.showSnackbar("Could not read file") }
            } else {
                viewModel.importMacros(json) { message -> scope.launch { snackbar.showSnackbar(message) } }
            }
        }
    }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Backup menu")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Variables…") },
            onClick = { expanded = false; onOpenVariables() },
        )
        DropdownMenuItem(
            text = { Text("Export macros…") },
            onClick = {
                expanded = false
                viewModel.exportMacros { json ->
                    pendingExport = json
                    createFile.launch("openmacro-backup.json")
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Import macros…") },
            onClick = {
                expanded = false
                openFile.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
        )
    }
}

@Composable
private fun MacroCard(macro: Macro, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalSectionPalette.current
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(macro.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val firstTrigger = macro.triggers.firstOrNull()
                Text(
                    text = when {
                        firstTrigger == null -> "No trigger"
                        macro.triggers.size == 1 -> "${firstTrigger.spec.emoji()} ${firstTrigger.spec.summary()}"
                        else -> "${firstTrigger.spec.emoji()} ${firstTrigger.spec.summary()} +${macro.triggers.size - 1}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.trigger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${macro.actions.size} action${if (macro.actions.size == 1) "" else "s"} · " +
                        "${macro.constraints.size} constraint${if (macro.constraints.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = runInfo(macro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = macro.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun runInfo(macro: Macro): String {
    val last = macro.lastRunAt ?: return "Never run"
    val ago = DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return "Ran ${macro.runCount}× · last $ago"
}

@Composable
private fun RequirementsBanner(missing: List<Requirement>, onGrant: (Requirement) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Your macros need access", style = MaterialTheme.typography.titleSmall)
            }
            missing.forEach { requirement ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(requirement.title, style = MaterialTheme.typography.bodyLarge)
                        Text(requirement.rationale, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onGrant(requirement) }) { Text("Grant") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⚡", style = MaterialTheme.typography.displayMedium)
        Text("No macros yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "A macro pairs triggers (when) with actions (what), optionally gated by constraints (only if).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCreate) { Text("Create your first macro") }
    }
}

@Composable
private fun PrivacyFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            if (dev.openmacro.BuildConfig.NETWORK) {
                "Online build · network only for actions you configure · no telemetry"
            } else {
                "Fully offline · no network permission · data stays on this device"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
