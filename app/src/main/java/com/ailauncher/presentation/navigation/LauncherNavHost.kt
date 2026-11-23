package com.ailauncher.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ailauncher.presentation.chat.AiChatScreen
import com.ailauncher.presentation.drawer.AppDrawerScreen
import com.ailauncher.presentation.home.LauncherHomeScreen
import com.ailauncher.presentation.model.ModelDownloadScreen
import com.ailauncher.presentation.onboarding.OnboardingScreen
import com.ailauncher.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data class ModelSetupRoute(val firstRun: Boolean = false)

@Serializable data object HomeRoute

@Serializable data object DrawerRoute

@Serializable data class ChatRoute(val prompt: String? = null)

@Serializable data object SettingsRoute

@Composable
fun LauncherNavHost(
    startDestination: Any,
    homePresses: Flow<Unit>,
    navController: NavHostController = rememberNavController(),
) {
    // Pressing Home while the drawer, chat or settings is open returns to the home screen.
    LaunchedEffect(navController) {
        homePresses.collect { navController.popBackStack<HomeRoute>(inclusive = false) }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(ModelSetupRoute(firstRun = true)) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<ModelSetupRoute> { entry ->
            val route = entry.toRoute<ModelSetupRoute>()
            ModelDownloadScreen(
                firstRun = route.firstRun,
                onDone = {
                    if (route.firstRun) {
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable<HomeRoute> {
            LauncherHomeScreen(
                onOpenDrawer = { navController.navigate(DrawerRoute) { launchSingleTop = true } },
                onOpenChat = { prompt -> navController.navigate(ChatRoute(prompt)) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }

        composable<DrawerRoute>(
            enterTransition = { slideInVertically(tween(280)) { it / 2 } + fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(160)) },
            popExitTransition = { slideOutVertically(tween(240)) { it / 2 } + fadeOut(tween(200)) },
        ) {
            AppDrawerScreen(
                onClose = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }

        composable<ChatRoute>(
            enterTransition = slideFromEnd,
            popExitTransition = slideToEnd,
        ) {
            AiChatScreen(
                onBack = { navController.popBackStack() },
                onOpenModelSetup = { navController.navigate(ModelSetupRoute()) },
            )
        }

        composable<SettingsRoute>(
            enterTransition = slideFromEnd,
            popExitTransition = slideToEnd,
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenModelSetup = { navController.navigate(ModelSetupRoute()) },
            )
        }
    }
}

private val slideFromEnd: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(260)) { it } + fadeIn(tween(200))
}

private val slideToEnd: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
}
