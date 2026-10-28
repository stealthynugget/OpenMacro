package dev.openmacro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.openmacro.core.engine.service.EngineService
import dev.openmacro.ui.OpenMacroNavHost
import dev.openmacro.ui.theme.OpenMacroTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer
        // Keep the daemon in line with the master switch. Collecting on every resume also
        // re-delivers a start command, which refreshes permission-dependent event sources after
        // the user returns from a permission dialog or the settings app.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.settings.engineEnabled.collect { enabled ->
                    if (enabled) EngineService.start(this@MainActivity) else EngineService.stop(this@MainActivity)
                }
            }
        }

        setContent {
            OpenMacroTheme {
                OpenMacroNavHost(container)
            }
        }
    }
}
