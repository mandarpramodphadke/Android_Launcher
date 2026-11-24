package com.ailauncher.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.presentation.navigation.HomeRoute
import com.ailauncher.presentation.navigation.LauncherNavHost
import com.ailauncher.presentation.navigation.OnboardingRoute
import com.ailauncher.presentation.theme.AiLauncherTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The HOME activity. It draws on a transparent window (`windowShowWallpaper`), so the system
 * renders the live/static wallpaper behind every Compose layer.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready ?: return@setContent // DataStore read: ~ms
            AiLauncherTheme(settings = ready.settings) {
                val start = remember { if (ready.settings.onboardingCompleted) HomeRoute else OnboardingRoute }
                LauncherNavHost(startDestination = start, homePresses = viewModel.homePresses)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: every press of the Home button while we are the launcher lands here.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }
}
