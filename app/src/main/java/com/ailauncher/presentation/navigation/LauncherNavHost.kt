package com.ailauncher.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ailauncher.domain.action.LauncherDestination
import com.ailauncher.presentation.HomePress
import com.ailauncher.presentation.chat.AiChatScreen
import com.ailauncher.presentation.components.LocalNavAnimatedVisibilityScope
import com.ailauncher.presentation.components.LocalSharedTransitionScope
import com.ailauncher.presentation.home.LauncherHomeScreen
import com.ailauncher.presentation.model.ModelDownloadScreen
import com.ailauncher.presentation.onboarding.OnboardingScreen
import com.ailauncher.presentation.planner.PlannerScreen
import com.ailauncher.presentation.planner.PlannerTab
import com.ailauncher.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data class ModelSetupRoute(val firstRun: Boolean = false)

/** Home pages, dock, command bar and the app drawer (an overlay of the home screen). */
@Serializable data object HomeRoute

@Serializable data class ChatRoute(val prompt: String? = null)

@Serializable data object SettingsRoute

/** To-dos & Daily Planner. [tab] is a [PlannerTab] name. */
@Serializable data class PlannerRoute(val tab: String = PlannerTab.PLAN.name)

@Composable
fun LauncherNavHost(
    startDestination: Any,
    homePresses: Flow<HomePress>,
    shares: Flow<Unit> = emptyFlow(),
    onShareHandled: () -> Unit = {},
    destinations: Flow<LauncherDestination> = emptyFlow(),
    navController: NavHostController = rememberNavController(),
) {
    // A folder the assistant opened ("open my Work folder"): shown by the home screen.
    var folderRequest by rememberSaveable { mutableStateOf<Long?>(null) }

    // Pressing Home while chat, settings or the planner is open returns to the home screen.
    LaunchedEffect(navController) {
        homePresses.collect { navController.popBackStack<HomeRoute>(inclusive = false) }
    }
    LaunchedEffect(navController) {
        destinations.collect { destination ->
            when (destination) {
                is LauncherDestination.Folder -> {
                    folderRequest = destination.folderId
                    navController.popBackStack<HomeRoute>(inclusive = false)
                }
                LauncherDestination.Planner -> navController.navigate(PlannerRoute(PlannerTab.PLAN.name)) { launchSingleTop = true }
                LauncherDestination.Todos -> navController.navigate(PlannerRoute(PlannerTab.TODOS.name)) { launchSingleTop = true }
                LauncherDestination.Home -> navController.popBackStack<HomeRoute>(inclusive = false)
            }
        }
    }
    // Text or a PDF shared to the launcher opens in the assistant.
    LaunchedEffect(navController) {
        shares.collect {
            onShareHandled()
            if (navController.currentBackStackEntry?.destination?.route?.contains("ChatRoute") != true) {
                navController.navigate(ChatRoute()) { launchSingleTop = true }
            }
        }
    }

    // Shared bounds: the home AI command bar morphs into the chat input bar and back.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
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

                composable<HomeRoute>(
                    // Home recedes slightly while another screen springs in over it.
                    exitTransition = { fadeOut(tween(180)) + scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.96f) },
                    popEnterTransition = { fadeIn(tween(220)) + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f) },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        LauncherHomeScreen(
                            homePresses = homePresses,
                            folderRequest = folderRequest,
                            onFolderRequestHandled = { folderRequest = null },
                            onOpenChat = { prompt -> navController.navigate(ChatRoute(prompt)) { launchSingleTop = true } },
                            onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                            onOpenPlanner = { navController.navigate(PlannerRoute()) { launchSingleTop = true } },
                        )
                    }
                }

                composable<ChatRoute>(
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        AiChatScreen(
                            onBack = { navController.popBackStack() },
                            onOpenModelSetup = { navController.navigate(ModelSetupRoute()) },
                            onOpenPlanner = { tab -> navController.navigate(PlannerRoute(tab.name)) { launchSingleTop = true } },
                        )
                    }
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

                composable<PlannerRoute>(
                    enterTransition = slideFromEnd,
                    popExitTransition = slideToEnd,
                ) { entry ->
                    val route = entry.toRoute<PlannerRoute>()
                    PlannerScreen(
                        initialTab = PlannerTab.entries.firstOrNull { it.name == route.tab } ?: PlannerTab.PLAN,
                        onBack = { navController.popBackStack() },
                        onAskAssistant = { prompt -> navController.navigate(ChatRoute(prompt)) { launchSingleTop = true } },
                    )
                }
            }
        }
    }
}

private val slideSpring = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

private val slideFromEnd: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpring) { it } + fadeIn(tween(200))
}

private val slideToEnd: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)) { it } + fadeOut(tween(200))
}
