package com.ailauncher.presentation.drawer

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.LaunchRequest
import com.ailauncher.presentation.components.rememberLaunchRequestFactory

/** Outcome of asking to open Private Space. */
sealed interface PrivateSpaceAuth {
    data object Granted : PrivateSpaceAuth
    /** The device has no screen lock, so there is nothing to protect the space with. */
    data object NoScreenLock : PrivateSpaceAuth
    data class Failed(val message: String) : PrivateSpaceAuth
}

/**
 * Unlocks Private Space with the platform [BiometricPrompt]: fingerprint/face, or the device
 * PIN/pattern/password as a fallback. Nothing is stored: the space locks again when it closes.
 */
fun authenticatePrivateSpace(context: Context, onResult: (PrivateSpaceAuth) -> Unit) {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    if (keyguard?.isDeviceSecure != true) {
        onResult(PrivateSpaceAuth.NoScreenLock)
        return
    }
    val builder = BiometricPrompt.Builder(context)
        .setTitle("Private Space")
        .setSubtitle("Unlock to see your hidden apps")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    val prompt = runCatching { builder.build() }.getOrElse {
        onResult(PrivateSpaceAuth.Failed("Private Space can't be unlocked on this device"))
        return
    }
    prompt.authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(PrivateSpaceAuth.Granted)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelling the prompt is not worth a message.
                val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                if (!cancelled) onResult(PrivateSpaceAuth.Failed(errString.toString()))
            }
        },
    )
}

/** Hidden apps, shown only after [authenticatePrivateSpace]. Long-press an app to take it out. */
@Composable
fun PrivateSpaceSheet(
    apps: List<AppInfo>,
    columns: Int,
    unprotected: Boolean,
    onLaunch: (LaunchRequest) -> Unit,
    onUnhide: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val launchFactory = rememberLaunchRequestFactory()
    var unhiding by remember { mutableStateOf<AppInfo?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (unprotected) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Private Space", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Hidden from the drawer, search, suggestions and the assistant. Long-press an app to move it out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (unprotected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        "Set a screen lock (PIN, pattern or password) to protect Private Space with your fingerprint or face.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (apps.isEmpty()) {
                Text(
                    "No hidden apps. Long-press any app and choose \"Move to Private Space\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                ) {
                    items(apps, key = { it.componentKey }) { app ->
                        AppGridTile(
                            app = app,
                            onClick = { bounds -> onLaunch(launchFactory(app, bounds)) },
                            onLongClick = { unhiding = app },
                            iconSize = 48.dp,
                        )
                    }
                }
            }
        }
    }
    unhiding?.let { app ->
        AlertDialog(
            onDismissRequest = { unhiding = null },
            icon = { Icon(Icons.Rounded.LockOpen, null, Modifier.size(24.dp)) },
            title = { Text("Move ${app.label} out?") },
            text = { Text("It will show up in the app drawer, search and suggestions again.") },
            confirmButton = {
                TextButton(onClick = {
                    onUnhide(app)
                    unhiding = null
                }) { Text("Move out") }
            },
            dismissButton = { TextButton(onClick = { unhiding = null }) { Text("Cancel") } },
        )
    }
}
