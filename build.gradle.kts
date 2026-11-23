// Top-level build file. Plugins are resolved once here and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere (AGP 9 has built-in Kotlin); declared only to pin the Kotlin compiler version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
