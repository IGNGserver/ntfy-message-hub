package top.lvziwang.ntfyhub.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.lvziwang.ntfyhub.ui.screen.HomeScreen
import top.lvziwang.ntfyhub.ui.screen.LoginScreen
import top.lvziwang.ntfyhub.ui.screen.ServerSetupScreen
import top.lvziwang.ntfyhub.ui.screen.SettingsScreen
import top.lvziwang.ntfyhub.ui.theme.HubTheme
import top.lvziwang.ntfyhub.ui.viewmodel.AppViewModel
import top.lvziwang.ntfyhub.ui.viewmodel.AppViewModelFactory
import top.lvziwang.ntfyhub.ui.viewmodel.Route

@Composable
fun NtfyHubApp() {
    val context = LocalContext.current
    val container = remember { AppContainer(context) }
    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container))
    val appState by viewModel.state.collectAsState()
    val darkTheme = when (appState.settings.themeMode) {
        top.lvziwang.ntfyhub.model.ThemeMode.LIGHT -> false
        top.lvziwang.ntfyhub.model.ThemeMode.DARK -> true
        top.lvziwang.ntfyhub.model.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    HubTheme(
        darkTheme = darkTheme,
        dynamicColor = appState.settings.useDynamicColor,
        themePreset = appState.settings.themePreset
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            key(appState.startDestination.route) {
                AppNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    startDestination = appState.startDestination.route
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        animatedComposable(Route.Loading.route) {
            LoadingScreen()
        }
        animatedComposable(Route.ServerSetup.route) {
            ServerSetupScreen(viewModel = viewModel)
        }
        animatedComposable(Route.Login.route) {
            LoginScreen(viewModel = viewModel)
        }
        animatedComposable(Route.Home.route) {
            HomeScreen(viewModel = viewModel, onOpenSettings = {
                navController.navigate(Route.Settings.route)
            })
        }
        animatedComposable(Route.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun androidx.navigation.NavGraphBuilder.animatedComposable(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(280),
                initialOffsetX = { it / 5 }
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(240),
                targetOffsetX = { -it / 8 }
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(260),
                initialOffsetX = { -it / 6 }
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(240),
                targetOffsetX = { it / 5 }
            ) + fadeOut(animationSpec = tween(180))
        },
        content = content
    )
}
