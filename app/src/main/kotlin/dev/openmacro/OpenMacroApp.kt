package dev.openmacro

import android.app.Application
import android.content.Context
import dev.openmacro.core.database.GlobalVariableRepository
import dev.openmacro.core.database.MacroRepository
import dev.openmacro.core.database.OpenMacroDatabase
import dev.openmacro.core.engine.EngineProvider
import dev.openmacro.core.engine.EngineSettings
import dev.openmacro.core.engine.MacroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { OpenMacroDatabase.build(appContext) }
    val repository by lazy { MacroRepository(database.macroDao()) }
    val globalVariables by lazy { GlobalVariableRepository(database.globalVariableDao()) }
    val settings by lazy { EngineSettings(appContext) }
    val engine by lazy { MacroEngine(appContext, repository, globalVariables, settings, scope) }
}

class OpenMacroApp : Application(), EngineProvider {
    val container by lazy { AppContainer(this) }

    override val engine: MacroEngine
        get() = container.engine

    override fun onCreate() {
        super.onCreate()
        // The dispatcher runs whenever the process does, so alarms and receivers that cold-start
        // the process (without the UI or the service) are still handled.
        engine.start()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as OpenMacroApp).container
