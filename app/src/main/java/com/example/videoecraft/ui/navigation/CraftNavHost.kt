package com.example.videoecraft.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.videoecraft.ui.credits.CreditsScreen
import com.example.videoecraft.ui.editor.EditorScreen
import com.example.videoecraft.ui.editor.EditorViewModel
import com.example.videoecraft.ui.export.ExportScreen
import com.example.videoecraft.ui.export.ExportViewModel
import com.example.videoecraft.ui.help.HelpScreen
import com.example.videoecraft.ui.home.HomeScreen
import com.example.videoecraft.ui.home.HomeViewModel

/** Rutas de la app. */
object CraftRoutes {
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"
    const val HELP = "help"
    const val CREDITS = "credits"

    fun editor(projectId: Long) = "editor/$projectId"
    fun export(projectId: Long) = "export/$projectId"

    const val PROJECT_ID_ARG = "projectId"
}

/**
 * Grafo de navegación de VideoEcraft.
 *
 * Tres destinos lineales: **Home → Editor → Exportación**. Las transiciones
 * usan un slide horizontal porque refuerzan el modelo mental de "entrar en
 * el proyecto" y "salir de él", y duran 280 ms para no sentirse lentas en
 * un flujo de edición rápido.
 */
@UnstableApi
@Composable
fun CraftNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = CraftRoutes.HOME,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(280),
                initialOffsetX = { it / 4 },
            ) + fadeIn(tween(280))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(280),
                targetOffsetX = { -it / 6 },
            ) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(280),
                initialOffsetX = { -it / 6 },
            ) + fadeIn(tween(280))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(280),
                targetOffsetX = { it / 4 },
            ) + fadeOut(tween(200))
        },
    ) {
        // ---------------- Home ----------------
        composable(CraftRoutes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            HomeScreen(
                viewModel = viewModel,
                onOpenProject = { projectId ->
                    navController.navigate(CraftRoutes.editor(projectId))
                },
                onOpenHelp = { navController.navigate(CraftRoutes.HELP) },
                onOpenCredits = { navController.navigate(CraftRoutes.CREDITS) },
            )
        }

        // ---------------- Ayuda ----------------
        composable(CraftRoutes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        // ---------------- Créditos ----------------
        composable(CraftRoutes.CREDITS) {
            CreditsScreen(onBack = { navController.popBackStack() })
        }

        // ---------------- Editor ----------------
        composable(
            route = CraftRoutes.EDITOR,
            arguments = listOf(
                navArgument(CraftRoutes.PROJECT_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments
                ?.getLong(CraftRoutes.PROJECT_ID_ARG)
                ?: return@composable

            val viewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.factory(projectId),
            )
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onExport = { navController.navigate(CraftRoutes.export(projectId)) },
            )
        }

        // ---------------- Exportación ----------------
        composable(
            route = CraftRoutes.EXPORT,
            arguments = listOf(
                navArgument(CraftRoutes.PROJECT_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments
                ?.getLong(CraftRoutes.PROJECT_ID_ARG)
                ?: return@composable

            val viewModel: ExportViewModel = viewModel(
                factory = ExportViewModel.factory(projectId),
            )
            ExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFinished = {
                    // Vuelve a Home limpiando Editor y Export del back stack.
                    navController.popBackStack(CraftRoutes.HOME, inclusive = false)
                },
            )
        }
    }
}
