package dev.openmacro.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.openmacro.AppContainer
import dev.openmacro.ui.dashboard.DashboardScreen
import dev.openmacro.ui.dashboard.DashboardViewModel
import dev.openmacro.ui.editor.EditorScreen
import dev.openmacro.ui.editor.EditorViewModel
import dev.openmacro.ui.variables.VariablesScreen
import dev.openmacro.ui.variables.VariablesViewModel

private const val DASHBOARD = "dashboard"
private const val EDITOR = "editor/{id}"
private const val VARIABLES = "variables"
private fun editorRoute(id: Long) = "editor/$id"

@Composable
fun OpenMacroNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = DASHBOARD) {
        composable(DASHBOARD) {
            val vm = viewModel { DashboardViewModel(container.repository, container.settings) }
            DashboardScreen(
                viewModel = vm,
                onOpenMacro = { nav.navigate(editorRoute(it)) },
                onCreateMacro = { nav.navigate(editorRoute(0)) },
                onOpenVariables = { nav.navigate(VARIABLES) },
            )
        }
        composable(VARIABLES) {
            val vm = viewModel { VariablesViewModel(container.globalVariables) }
            VariablesScreen(viewModel = vm, onClose = { nav.popBackStack() })
        }
        composable(EDITOR, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm = viewModel { EditorViewModel(id, container.repository, container.globalVariables, container.engine) }
            EditorScreen(viewModel = vm, onClose = { nav.popBackStack() })
        }
    }
}
