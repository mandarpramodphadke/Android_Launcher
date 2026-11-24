package com.ailauncher.presentation

import android.appwidget.AppWidgetManager
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
import androidx.lifecycle.lifecycleScope
import com.ailauncher.data.share.SharedContentReader
import com.ailauncher.data.widgets.LauncherWidgetHost
import com.ailauncher.presentation.chat.SharedContentStore
import com.ailauncher.presentation.navigation.HomeRoute
import com.ailauncher.presentation.navigation.LauncherNavHost
import com.ailauncher.presentation.navigation.OnboardingRoute
import com.ailauncher.presentation.theme.AiLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The HOME activity. It draws on a transparent window (`windowShowWallpaper`), so the system
 * renders the live/static wallpaper behind every Compose layer. It is also the share target for
 * text and PDFs, which open in the on-device assistant.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var widgetHost: LauncherWidgetHost
    @Inject lateinit var sharedContentReader: SharedContentReader
    @Inject lateinit var sharedContentStore: SharedContentStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready ?: return@setContent // DataStore read: ~ms
            AiLauncherTheme(settings = ready.settings) {
                val start = remember { if (ready.settings.onboardingCompleted) HomeRoute else OnboardingRoute }
                LauncherNavHost(
                    startDestination = start,
                    homePresses = viewModel.homePresses,
                    shares = viewModel.shares,
                    onShareHandled = viewModel::onShareHandled,
                    destinations = viewModel.destinations,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets only receive updates while the host listens.
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: every press of the Home button while we are the launcher lands here.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Launcher3's rule: we were already in front (and not just brought there from an app).
            val alreadyOnHome = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
            viewModel.onHomePressed(alreadyOnHome)
        } else {
            handleShare(intent)
        }
    }

    /** Widget configuration activities report back here (AppWidgetHost API). */
    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult delivers results to onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        widgetHost.onConfigureResult(requestCode, appWidgetId, resultCode == RESULT_OK)
    }

    private fun handleShare(intent: Intent?) {
        if (!sharedContentReader.isShare(intent)) return
        val share = intent ?: return
        lifecycleScope.launch {
            val payload = sharedContentReader.read(share) ?: return@launch
            sharedContentStore.offer(payload)
            viewModel.onShared()
        }
    }
}
