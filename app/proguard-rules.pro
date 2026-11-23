# =============================================================================================
# R8 rules for AI Launcher. Most libraries (Room, Hilt, OkHttp, Firebase, Coil, kotlinx.*) ship
# their own consumer rules; only the things R8 cannot see statically are listed here.
# =============================================================================================

# ---- JNI bridge to llama.cpp -----------------------------------------------------------------
# Native methods are bound by symbol name (Java_com_ailauncher_..._LlamaNative_*), and the callback
# interfaces are invoked from C++ via GetMethodID, which R8 cannot trace.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.ailauncher.data.llm.LlamaNative { *; }
-keep interface com.ailauncher.data.llm.NativeTokenCallback { *; }
-keep interface com.ailauncher.data.llm.NativeProgressCallback { *; }
-keep class * implements com.ailauncher.data.llm.NativeTokenCallback { *; }
-keep class * implements com.ailauncher.data.llm.NativeProgressCallback { *; }

# ---- Credential Manager: the Play Services provider is discovered reflectively ---------------
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
    *;
}

# ---- Type-safe Navigation routes are looked up by their serial names --------------------------
-keep @kotlinx.serialization.Serializable class com.ailauncher.presentation.navigation.** { *; }

# ---- Accessibility service / device admin are instantiated by the framework -----------------
-keep class com.ailauncher.data.system.LauncherAccessibilityService { *; }
-keep class com.ailauncher.data.system.LockScreenAdminReceiver { *; }
