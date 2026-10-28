package dev.openmacro.ui.variables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openmacro.core.database.GlobalVariableRepository
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.Variable
import dev.openmacro.core.model.normaliseValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manages the user's global variables (list, create/update, delete). */
class VariablesViewModel(private val repository: GlobalVariableRepository) : ViewModel() {

    val variables: StateFlow<List<Variable>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates or updates a variable, normalising the raw value for its type. Returns an error or null. */
    fun put(name: String, type: VarType, rawValue: String, onDone: (String?) -> Unit) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onDone("Name can't be empty")
            return
        }
        val value = normaliseValue(type, rawValue)
        if (value == null) {
            onDone("“$rawValue” isn't a valid ${type.name.lowercase()}")
            return
        }
        viewModelScope.launch {
            repository.put(Variable(cleanName, type, value))
            onDone(null)
        }
    }

    fun delete(name: String) {
        viewModelScope.launch { repository.delete(name) }
    }
}
