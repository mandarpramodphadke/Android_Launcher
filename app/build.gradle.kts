import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------------------------
// Local, non-committed configuration (local.properties / -P flags / environment variables).
// ---------------------------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun config(key: String, env: String = key.uppercase().replace('.', '_')): String? =
    (findProperty(key) as String?)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

// Firebase is optional: the launcher (and all on-device AI) works without it. Cloud backup is
// enabled automatically once app/google-services.json is present.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// OAuth 2.0 *Web* client ID used as the serverClientId for Sign in with Google.
val googleWebClientId = config("google.web.client.id").orEmpty()

// Optional path to a llama.cpp checkout. When unset, CMake uses the git submodule at
// app/src/main/cpp/llama.cpp and, failing that, downloads the pinned release tag.
val llamaCppDir = config("llama.cpp.dir")

// Optional GPU offload (arm64): the ggml Vulkan backend. On by default; `ggml.vulkan=false` builds a
// CPU-only library. Header checkouts can be supplied for offline builds, otherwise CMake fetches the
// pinned Vulkan-Headers / SPIRV-Headers tags.
val ggmlVulkan = config("ggml.vulkan")?.toBooleanStrictOrNull() ?: true

// Google Play build (see docs/PLAY_RELEASE.md). The Play app has its own, permanent application ID;
// the contact address appears in the privacy policy and receives AI-reply reports when no report
// form is configured; the report form is a Google Form "pre-filled link" (fully in-app reports).
val playApplicationId = config("play.application.id") ?: "io.github.mandarpramodphadke.aura"
val supportEmail = config("support.email") ?: "mandarphadke1434@gmail.com"
val reportFormUrl = config("report.form.url").orEmpty()

fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val vulkanHeadersDir = config("vulkan.headers.dir")
val spirvHeadersDir = config("spirv.headers.dir")

android {
    namespace = "com.ailauncher"
    // Compose 1.13 requires compiling against the Android 17 QPR (API 37.1). targetSdk stays at 36,
    // so runtime behaviour is unchanged.
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.ailauncher"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "SUPPORT_EMAIL", buildConfigString(supportEmail))
        buildConfigField("String", "REPORT_FORM_URL", buildConfigString(reportFormUrl))

        ndk {
            // arm64-v8a: real devices. x86_64: emulators (slow, but functional).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // llama.cpp is unusably slow at -O0, so native code is always built optimised,
                // even for the debug variant. Kotlin/Java debugging is unaffected.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DANDROID_STL=c++_shared"
                llamaCppDir?.let { arguments += "-DLLAMA_CPP_DIR=${file(it).absolutePath}" }
                arguments += "-DAILAUNCHER_VULKAN=${if (ggmlVulkan) "ON" else "OFF"}"
                vulkanHeadersDir?.let { arguments += "-DVULKAN_HEADERS_DIR=${file(it).absolutePath}" }
                spirvHeadersDir?.let { arguments += "-DSPIRV_HEADERS_DIR=${file(it).absolutePath}" }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    // full: installed directly from an APK, every feature. play: Google Play policy-safe build with
    // its own application ID, no broad storage permissions (file tools search folders the user picks)
    // and no cloud sign-in or backup. Shared code checks the BuildConfig flags below.
    //
    // New features are tried in the full app first (CLAUDE.md): each has a flag that is true in
    // full and false in play until it has been tested on a phone and checked against Play policy.
    // 1.4: TODO_WIDGETS (launcher-drawn to-do widgets), HOME_DRAG_AND_DROP (drag apps to move them
    // or make folders), SMART_FOLDERS (folders that fill themselves).
    // 1.5: SYSTEM_INFO (the System info page after the AI chat).
    // 1.6: CHAT_PANELS (chat and System info slide in as panels that follow the finger),
    // CHAT_CUSTOMIZATION (compact think button, chat background, assistant behaviour),
    // MORE_CALENDARS (choose device calendars, subscribe to calendar links, import .ics files).
    // 1.6.1: BETTER_ANSWERS (a system prompt that answers general questions, and get_device_info).
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_BUILD", "false")
            buildConfigField("boolean", "BROAD_FILE_ACCESS", "true")
            buildConfigField("boolean", "CLOUD_FEATURES", "true")
            buildConfigField("boolean", "TODO_WIDGETS", "true")
            buildConfigField("boolean", "HOME_DRAG_AND_DROP", "true")
            buildConfigField("boolean", "SMART_FOLDERS", "true")
            buildConfigField("boolean", "SYSTEM_INFO", "true")
            buildConfigField("boolean", "CHAT_PANELS", "true")
            buildConfigField("boolean", "CHAT_CUSTOMIZATION", "true")
            buildConfigField("boolean", "MORE_CALENDARS", "true")
            buildConfigField("boolean", "BETTER_ANSWERS", "true")
        }
        create("play") {
            dimension = "distribution"
            applicationId = playApplicationId
            buildConfigField("boolean", "PLAY_BUILD", "true")
            buildConfigField("boolean", "BROAD_FILE_ACCESS", "false")
            buildConfigField("boolean", "CLOUD_FEATURES", "false")
            // Not yet released on Google Play (see the comment above).
            buildConfigField("boolean", "TODO_WIDGETS", "false")
            buildConfigField("boolean", "HOME_DRAG_AND_DROP", "false")
            buildConfigField("boolean", "SMART_FOLDERS", "false")
            buildConfigField("boolean", "SYSTEM_INFO", "false")
            buildConfigField("boolean", "CHAT_PANELS", "false")
            buildConfigField("boolean", "CHAT_CUSTOMIZATION", "false")
            buildConfigField("boolean", "MORE_CALENDARS", "false")
            buildConfigField("boolean", "BETTER_ANSWERS", "false")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = config("release.store.file")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = config("release.store.password")
                keyAlias = config("release.key.alias")
                keyPassword = config("release.key.password")
            }
        }
    }

    buildTypes {
        debug {
            // No applicationIdSuffix on purpose: google-services.json and the Android OAuth client
            // used by Sign in with Google are both bound to a single package name.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // ggml discovers its CPU backend variants (armv8.0 … armv9.2) at runtime by scanning
            // ApplicationInfo.nativeLibraryDir and dlopen()-ing the best match for the SoC, so the
            // shared libraries must be extracted to disk on install.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric screenshot tests render Compose with the app's real resources.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

// Opt-in JNI integration test (LlamaNativeHostTest): pass a host build of the bridge and a GGUF.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("ailauncher.test.model").orNull?.let { systemProperty("ailauncher.test.model", it) }
    providers.gradleProperty("ailauncher.test.nativeDir").orNull?.let { systemProperty("java.library.path", it) }
}

// ---------------------------------------------------------------------------------------------
// Google Play guard: fail the Play build if a permission Google Play restricts for this kind of
// app reaches its merged manifest (for example through a library update).
// ---------------------------------------------------------------------------------------------
abstract class VerifyPlayManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val restrictedPermissions: ListProperty<String>

    /** Where AI-reply reports go: "form" (in-app), "email" (email draft) or "none". */
    @get:Input
    abstract val reportChannel: Property<String>

    @get:Input
    abstract val releaseBuild: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val found = restrictedPermissions.get().filter { manifest.contains("\"$it\"") }
        check(found.isEmpty()) { "The Google Play build must not declare: ${found.joinToString()}" }
        // Google Play's AI-generated content policy: users must be able to report AI replies in-app.
        if (releaseBuild.get()) {
            check(reportChannel.get() != "none") {
                "Set report.form.url (a Google Form pre-filled link, see docs/PLAY_RELEASE.md) or support.email " +
                    "in local.properties: Google Play requires in-app reporting of AI replies."
            }
            if (reportChannel.get() == "email") {
                logger.warn("Play build: AI-reply reports open an email draft. Set report.form.url to send them in-app (recommended).")
            }
        }
        report.get().asFile.writeText("No restricted permissions. AI-reply reports: ${reportChannel.get()}.\n")
    }
}

// The privacy policy has one source, docs/privacy-policy.md: GitHub Pages publishes it for the
// Play listing and every build shows the same text in Settings ▸ Privacy policy.
abstract class CopyLegalDocsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val privacyPolicy: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val legal = outputDir.get().asFile.resolve("legal").apply { mkdirs() }
        privacyPolicy.get().asFile.copyTo(legal.resolve("privacy-policy.md"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val copyLegal = tasks.register<CopyLegalDocsTask>("copy${suffix}LegalDocs") {
            privacyPolicy.set(rootProject.layout.projectDirectory.file("docs/privacy-policy.md"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyLegal, CopyLegalDocsTask::outputDir)
    }
    onVariants(selector().withFlavor("distribution" to "play")) { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyPlayManifestTask>("verify${suffix}PlayPolicy") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            restrictedPermissions.set(
                listOf(
                    "android.permission.MANAGE_EXTERNAL_STORAGE",
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.QUERY_ALL_PACKAGES",
                    "android.permission.REQUEST_INSTALL_PACKAGES",
                    "android.permission.READ_SMS",
                    "android.permission.SEND_SMS",
                    "android.permission.READ_CALL_LOG",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.USE_EXACT_ALARM",
                ),
            )
            reportChannel.set(
                when {
                    reportFormUrl.startsWith("https://docs.google.com/forms/") -> "form"
                    supportEmail.isNotBlank() -> "email"
                    else -> "none"
                },
            )
            releaseBuild.set(variant.buildType == "release")
            report.set(layout.buildDirectory.file("outputs/play-policy/${variant.name}.txt"))
        }
        tasks.matching { it.name == "assemble$suffix" || it.name == "bundle$suffix" }.configureEach { dependsOn(verify) }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core / lifecycle / navigation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material.components)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Persistence & background work
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Google Sign-In (Credential Manager) + optional Firebase cloud backup
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Kotlin, networking, images
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
