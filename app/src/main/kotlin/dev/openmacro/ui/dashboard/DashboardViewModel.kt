package dev.openmacro.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openmacro.core.database.MacroRepository
import dev.openmacro.core.engine.EngineSettings
import dev.openmacro.core.engine.service.EngineService
import dev.openmacro.core.model.Macro
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val macros: List<Macro> = emptyList(),
    val loaded: Boolean = false,
    val engineEnabled: Boolean = true,
    val serviceRunning: Boolean = false,
)

class DashboardViewModel(
    private val repository: MacroRepository,
    private val settings: EngineSettings,
) : ViewModel() {

    val state: StateFlow<DashboardState> = combine(
        repository.observeAll(),
        settings.engineEnabled,
        EngineService.running,
    ) { macros, engineEnabled, running ->
        DashboardState(macros = macros, loaded = true, engineEnabled = engineEnabled, serviceRunning = running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setMacroEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setEngineEnabled(enabled) }
    }

    /** Produces the backup JSON, then hands it to [onReady] for writing to a chosen file. */
    fun exportMacros(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(repository.exportAll()) }
    }

    fun importMacros(json: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val count = repository.import(json)
                "Imported $count macro${if (count == 1) "" else "s"}"
            } catch (e: Exception) {
                "Import failed: not a valid OpenMacro backup"
            }
            onResult(message)
        }
    }
}
