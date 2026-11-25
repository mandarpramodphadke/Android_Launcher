package com.ailauncher.presentation.legal

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ailauncher.BuildConfig
import com.ailauncher.presentation.components.AppListItem
import com.ailauncher.presentation.components.MarkdownText
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings ▸ Privacy policy: the same text as the published web page (docs/privacy-policy.md). */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val policy by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) { readAsset(context, "legal/privacy-policy.md") }?.let(::withContact)
    }
    LegalScaffold("Privacy policy", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            MarkdownText(policy ?: "", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The contact address comes from the build (support.email) when the text still has the placeholder. */
private fun withContact(policy: String): String = policy.replace(
    "{{CONTACT_EMAIL}}",
    BuildConfig.SUPPORT_EMAIL.ifBlank { "the developer contact listed on the app's Google Play page" },
)

private data class Component(val name: String, val by: String, val license: String, val asset: String)

private val COMPONENTS = listOf(
    Component("llama.cpp and ggml", "The ggml authors", "MIT License", "legal/licenses/MIT-ggml.txt"),
    Component("Qwen3 models (downloaded, not bundled)", "Qwen team, Alibaba Cloud", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Vulkan-Headers", "The Khronos Group", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("SPIRV-Headers", "The Khronos Group", "MIT-style Khronos licence", "legal/licenses/SPIRV-Headers.txt"),
    Component("Android Jetpack (AndroidX, Compose, Material 3, Room, WorkManager, DataStore, Navigation)", "The Android Open Source Project", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Material Symbols and Icons", "Google", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o.", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Dagger and Hilt", "The Dagger authors", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("OkHttp and Okio", "Square, Inc.", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Coil", "Coil contributors", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
    Component("Credential Manager, Google Identity Services, Firebase SDKs", "Google", "Apache License 2.0", "legal/licenses/Apache-2.0.txt"),
)

/** Settings ▸ Open-source licences. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<Component?>(null) }
    LegalScaffold("Open-source licences", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "AI Launcher ${BuildConfig.VERSION_NAME} is built with these open-source projects. Tap one to read its licence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(COMPONENTS, key = { it.name }) { component ->
                AppListItem(
                    headlineContent = { Text(component.name) },
                    supportingContent = { Text("${component.by} · ${component.license}") },
                    onClick = { open = component },
                )
            }
        }
    }
    open?.let { component ->
        val text by produceState<String?>(null, component) { value = withContext(Dispatchers.IO) { readAsset(context, component.asset) } }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(component.license) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun LegalScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    ThemedSystemBarIcons()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        content = content,
    )
}

private fun readAsset(context: Context, path: String): String? =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
