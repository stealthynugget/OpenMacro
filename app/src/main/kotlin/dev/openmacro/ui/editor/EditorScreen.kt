@file:OptIn(ExperimentalMaterial3Api::class)

package dev.openmacro.ui.editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openmacro.BuildConfig
import dev.openmacro.core.engine.permissions.Requirements
import dev.openmacro.core.model.ActionItem
import dev.openmacro.core.model.ConstraintMode
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.catalog.Catalog
import dev.openmacro.ui.catalog.AppEntry
import dev.openmacro.ui.catalog.EditorEnvironment
import dev.openmacro.ui.catalog.LocationStatus
import dev.openmacro.ui.catalog.PickKind
import dev.openmacro.ui.catalog.emoji
import dev.openmacro.ui.catalog.summary
import dev.openmacro.ui.components.EmojiBadge
import dev.openmacro.ui.components.SegmentedChoice
import dev.openmacro.ui.theme.LocalSectionPalette
import kotlinx.coroutines.launch

private sealed interface EditorDialog {
    data object PickTrigger : EditorDialog
    data object PickAction : EditorDialog
    data object PickConstraint : EditorDialog
    data class EditTrigger(val index: Int?, val spec: TriggerSpec) : EditorDialog
    data class EditAction(val index: Int?, val item: ActionItem) : EditorDialog
    data class EditConstraint(val index: Int?, val spec: ConstraintSpec) : EditorDialog
}

@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val macros by viewModel.otherMacros.collectAsStateWithLifecycle()
    val variables by viewModel.variableRefs.collectAsStateWithLifecycle()
    val palette = LocalSectionPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // URI pickers for sound / image fields.
    var pendingUri by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingUri?.invoke(it.toString())
        }
        pendingUri = null
    }
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let { data ->
            @Suppress("DEPRECATION")
            data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        uri?.let { pendingUri?.invoke(it.toString()) }
        pendingUri = null
    }

    // Location lookups report inline in the field (a snackbar would sit behind the modal dialog).
    var pendingLocation by remember { mutableStateOf<((LocationStatus) -> Unit)?>(null) }
    fun locate(onStatus: (LocationStatus) -> Unit) {
        fetchCurrentLocation(
            context,
            onStart = { onStatus(LocationStatus.Searching) },
            onFailure = { message, offerSettings -> onStatus(LocationStatus.Failed(message, offerSettings)) },
            onSuccess = { lat, lng -> onStatus(LocationStatus.Found(lat, lng)) },
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cb = pendingLocation ?: return@rememberLauncherForActivityResult
        pendingLocation = null
        if (granted) locate(cb) else cb(LocationStatus.Failed("Location permission denied", offerSettings = false))
    }

    val env = remember(macros, variables) {
        EditorEnvironment(
            variables = variables,
            macros = macros,
            apps = { installedApps(context) },
            includeNetwork = BuildConfig.NETWORK,
            pickUri = { kind, onPicked ->
                pendingUri = onPicked
                when (kind) {
                    PickKind.IMAGE -> imageLauncher.launch(arrayOf("image/*"))
                    PickKind.SOUND -> soundLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL),
                    )
                }
            },
            requestLocation = { onStatus ->
                if (hasLocationPermission(context)) {
                    locate(onStatus)
                } else {
                    pendingLocation = onStatus
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun ensure(requirements: Set<Requirement>) {
        val missing = requirements.filterNot { Requirements.isGranted(context, it) }
        missing.firstNotNullOfOrNull { Requirements.runtimePermission(it) }?.let { permissionLauncher.launch(it) }
        missing.firstOrNull { Requirements.runtimePermission(it) == null }?.let { requirement ->
            scope.launch {
                val result = snackbar.showSnackbar("Needs “${requirement.title}”", actionLabel = "Grant")
                if (result == SnackbarResult.ActionPerformed) {
                    runCatching { context.startActivity(Requirements.settingsIntent(context, requirement)) }
                }
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val requestClose: () -> Unit = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New macro" else "Edit macro") },
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!state.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete macro") }
                    IconButton(onClick = { viewModel.save(onClose) }, enabled = !state.loading) { Icon(Icons.Filled.Check, contentDescription = "Save") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(onClick = viewModel::testActions, enabled = !state.testing && !state.loading, modifier = Modifier.weight(1f)) {
                        if (state.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Test actions")
                    }
                    Button(onClick = { viewModel.save(onClose) }, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            EditorContent(
                macro = state.macro,
                padding = padding,
                onName = viewModel::setName,
                onDescription = viewModel::setDescription,
                onConstraintMode = viewModel::setConstraintMode,
                onOpen = { dialog = it },
                onRemoveTrigger = viewModel::removeTrigger,
                onRemoveAction = viewModel::removeAction,
                onMoveAction = viewModel::moveAction,
                onRemoveConstraint = viewModel::removeConstraint,
            )
        }
    }

    when (val d = dialog) {
        EditorDialog.PickTrigger -> CatalogPickerDialog(
            title = "Add trigger",
            groups = Catalog.triggersByCategory(env.includeNetwork),
            accent = palette.trigger,
            onPick = { dialog = EditorDialog.EditTrigger(null, it.create()) },
            onDismiss = { dialog = null },
        )
        EditorDialog.PickAction -> CatalogPickerDialog(
            title = "Add action",
            groups = Catalog.actionsByCategory(env.includeNetwork),
            accent = palette.action,
            onPick = { dialog = EditorDialog.EditAction(null, ActionItem(it.create())) },
            onDismiss = { dialog = null },
        )
        EditorDialog.PickConstraint -> CatalogPickerDialog(
            title = "Add constraint",
            groups = Catalog.constraintsByCategory(env.includeNetwork),
            accent = palette.constraint,
            onPick = { dialog = EditorDialog.EditConstraint(null, it.create()) },
            onDismiss = { dialog = null },
        )
        is EditorDialog.EditTrigger -> {
            val def = Catalog.trigger(d.spec)
            SpecConfigDialog(def.emoji, def.title, palette.trigger, def.erasedFields, d.spec, env, { dialog = null }) {
                viewModel.putTrigger(d.index, it)
                dialog = null
                ensure(def.requirementsOf(it))
            }
        }
        is EditorDialog.EditAction -> ActionEditDialog(d.item, env, onDismiss = { dialog = null }) { item ->
            viewModel.putAction(d.index, item)
            dialog = null
            ensure(Catalog.action(item.spec).requirementsOf(item.spec))
        }
        is EditorDialog.EditConstraint -> {
            val def = Catalog.constraint(d.spec)
            SpecConfigDialog(def.emoji, def.title, palette.constraint, def.erasedFields, d.spec, env, { dialog = null }) {
                viewModel.putConstraint(d.index, it)
                dialog = null
            }
        }
        null -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete macro?") },
            text = { Text("“${state.macro.name}” will be permanently removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onClose) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun EditorContent(
    macro: Macro,
    padding: PaddingValues,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onConstraintMode: (ConstraintMode) -> Unit,
    onOpen: (EditorDialog) -> Unit,
    onRemoveTrigger: (Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onMoveAction: (index: Int, delta: Int) -> Unit,
    onRemoveConstraint: (Int) -> Unit,
) {
    val palette = LocalSectionPalette.current

    val triggers: @Composable (Modifier) -> Unit = { modifier ->
        SectionCard("Triggers", palette.trigger, macro.triggers.size, "Nothing starts this macro yet.", "Add trigger", { onOpen(EditorDialog.PickTrigger) }, modifier) {
            macro.triggers.forEachIndexed { index, item ->
                ItemRow(item.spec.emoji(), item.spec.summary(), palette.trigger, { onOpen(EditorDialog.EditTrigger(index, item.spec)) }, { onRemoveTrigger(index) })
            }
            if (macro.triggers.size > 1) Hint("Any one trigger starts the macro.")
        }
    }
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        SectionCard("Actions", palette.action, macro.actions.size, "Add the steps to run, in order.", "Add action", { onOpen(EditorDialog.PickAction) }, modifier) {
            macro.actions.forEachIndexed { index, item ->
                ItemRow(
                    emoji = item.spec.emoji(),
                    title = "${index + 1}. ${item.spec.summary()}",
                    subtitle = item.constraints.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Only if: ") { it.summary() },
                    accent = palette.action,
                    onClick = { onOpen(EditorDialog.EditAction(index, item)) },
                    onDelete = { onRemoveAction(index) },
                    onMoveUp = if (index > 0) ({ onMoveAction(index, -1) }) else null,
                    onMoveDown = if (index < macro.actions.lastIndex) ({ onMoveAction(index, 1) }) else null,
                )
            }
        }
    }
    val constraints: @Composable (Modifier) -> Unit = { modifier ->
        SectionCard("Constraints", palette.constraint, macro.constraints.size, "Optional. Conditions checked when a trigger fires.", "Add constraint", { onOpen(EditorDialog.PickConstraint) }, modifier) {
            if (macro.constraints.size > 1) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedChoice(ConstraintMode.entries, macro.constraintMode, { if (it == ConstraintMode.ALL) "Match all" else "Match any" }, onConstraintMode)
                }
            }
            macro.constraints.forEachIndexed { index, item ->
                ItemRow(item.spec.emoji(), item.spec.summary(), palette.constraint, { onOpen(EditorDialog.EditConstraint(index, item.spec)) }, { onRemoveConstraint(index) })
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(macro.name, onName, label = { Text("Macro name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(macro.description, onDescription, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    triggers(Modifier.weight(1f)); actions(Modifier.weight(1f)); constraints(Modifier.weight(1f))
                }
            } else {
                triggers(Modifier.fillMaxWidth()); actions(Modifier.fillMaxWidth()); constraints(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color,
    count: Int,
    emptyText: String,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth().background(accent).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (count > 0) Text("$count", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        if (count == 0) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        content()
        TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text(addLabel, color = accent)
        }
    }
}

@Composable
private fun ItemRow(
    emoji: String,
    title: String,
    accent: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    subtitle: String? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { EmojiBadge(emoji, accent) },
        trailingContent = {
            Row {
                if (onMoveUp != null || onMoveDown != null) {
                    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Resolves the current position: a recent cached fix if one exists, otherwise a fresh single fix
 * (20 s timeout). Reports why it failed so the user isn't left with silent 0.0 coordinates.
 */
@SuppressLint("MissingPermission") // callers check hasLocationPermission() first
private fun fetchCurrentLocation(
    context: Context,
    onStart: () -> Unit,
    onFailure: (message: String, offerSettings: Boolean) -> Unit,
    onSuccess: (Double, Double) -> Unit,
) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (!LocationManagerCompat.isLocationEnabled(manager)) return onFailure("Location is turned off", true)

    val providers = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < 10 * 60_000L } // only trust fixes under 10 minutes old
        .maxByOrNull { it.time }
        ?.let { return onSuccess(it.latitude, it.longitude) }

    val provider = providers.firstOrNull() ?: return onFailure("No location provider is available", true)
    onStart()

    val main = Handler(Looper.getMainLooper())
    var done = false
    fun finish(location: Location?) {
        if (done) return
        done = true
        if (location != null) onSuccess(location.latitude, location.longitude)
        else onFailure("Couldn't get a location fix. Try again near a window or outdoors.", false)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val signal = CancellationSignal()
        manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { finish(it) }
        main.postDelayed({ if (!done) { signal.cancel(); finish(null) } }, 20_000L)
    } else {
        val listener = LocationListener { finish(it) }
        @Suppress("DEPRECATION")
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        main.postDelayed({ if (!done) { manager.removeUpdates(listener); finish(null) } }, 20_000L)
    }
}

private fun installedApps(context: android.content.Context): List<AppEntry> {
    val pm = context.packageManager
    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launch, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
