package dev.openmacro.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openmacro.core.database.GlobalVariableRepository
import dev.openmacro.core.database.MacroRepository
import dev.openmacro.core.engine.ExecutionReport
import dev.openmacro.core.engine.MacroEngine
import dev.openmacro.core.model.ActionItem
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.ConstraintItem
import dev.openmacro.core.model.ConstraintMode
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.Macro
import dev.openmacro.core.model.TriggerItem
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.VarScope
import dev.openmacro.ui.catalog.MacroEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val macro: Macro = Macro(),
    val loading: Boolean = false,
    val dirty: Boolean = false,
    val testing: Boolean = false,
    val message: String? = null,
) {
    val isNew: Boolean get() = macro.id == 0L
}

class EditorViewModel(
    private val macroId: Long,
    private val repository: MacroRepository,
    globalVariables: GlobalVariableRepository,
    private val engine: MacroEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState(loading = macroId != 0L))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Other macros, for Run/Enable/Cancel pickers. */
    val otherMacros: StateFlow<List<MacroEntry>> = repository.observeAll()
        .map { list -> list.filter { it.id != macroId }.map { MacroEntry(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val globalRefs = globalVariables.observeAll()
        .map { vars -> vars.map { VarRef(VarScope.GLOBAL, it.name) } }

    /** Global + this macro's local variables, for variable pickers. */
    val variableRefs: StateFlow<List<VarRef>> = combine(globalRefs, _state) { globals, st ->
        (st.macro.localVariables.map { VarRef(VarScope.LOCAL, it.name) } + globals).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (macroId != 0L) {
            viewModelScope.launch {
                val macro = repository.get(macroId)
                _state.update {
                    if (macro == null) it.copy(loading = false, message = "Macro not found") else it.copy(macro = macro, loading = false)
                }
            }
        }
    }

    private fun edit(transform: Macro.() -> Macro) = _state.update { it.copy(macro = it.macro.transform(), dirty = true) }

    fun setName(name: String) = edit { copy(name = name) }
    fun setDescription(description: String) = edit { copy(description = description) }
    fun setConstraintMode(mode: ConstraintMode) = edit { copy(constraintMode = mode) }

    fun putTrigger(index: Int?, spec: TriggerSpec) = edit {
        copy(triggers = if (index == null) triggers + TriggerItem(spec) else triggers.mapIndexed { i, t -> if (i == index) t.copy(spec = spec) else t })
    }

    fun removeTrigger(index: Int) = edit { copy(triggers = triggers.without(index)) }

    fun putAction(index: Int?, item: ActionItem) = edit {
        copy(actions = if (index == null) actions + item else actions.mapIndexed { i, a -> if (i == index) item else a })
    }

    fun removeAction(index: Int) = edit { copy(actions = actions.without(index)) }

    fun moveAction(index: Int, delta: Int) = edit {
        val target = index + delta
        if (target !in actions.indices) return@edit this
        copy(actions = actions.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun putConstraint(index: Int?, spec: ConstraintSpec) = edit {
        copy(constraints = if (index == null) constraints + ConstraintItem(spec) else constraints.mapIndexed { i, c -> if (i == index) c.copy(spec = spec) else c })
    }

    fun removeConstraint(index: Int) = edit { copy(constraints = constraints.without(index)) }

    fun save(onSaved: () -> Unit) {
        val macro = _state.value.macro
        viewModelScope.launch {
            repository.save(macro.copy(name = macro.name.trim().ifEmpty { "Untitled macro" }))
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.macro.id
        viewModelScope.launch {
            if (id != 0L) repository.delete(id)
            onDeleted()
        }
    }

    fun testActions() {
        val macro = _state.value.macro
        if (macro.actions.none { it.enabled }) {
            _state.update { it.copy(message = "Add an action to test") }
            return
        }
        _state.update { it.copy(testing = true) }
        viewModelScope.launch {
            val report = engine.testActions(macro).await()
            _state.update { it.copy(testing = false, message = report.describe()) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

private fun <T> List<T>.without(index: Int): List<T> = filterIndexed { i, _ -> i != index }

private fun ExecutionReport.describe(): String = buildString {
    append("Test run: $succeeded succeeded")
    if (skipped > 0) append(", $skipped skipped")
    if (failures.isNotEmpty()) append(", ${failures.size} failed — ${failures.first()}")
}
