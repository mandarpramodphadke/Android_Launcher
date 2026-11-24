# AI Launcher

An offline-first Android home-screen launcher with a private on-device assistant. **Qwen3-1.7B**
(GGUF, Q4_K_M) runs locally through **llama.cpp** via JNI. It sorts your apps into smart categories,
sets alarms, timers and reminders, opens apps, toggles device settings and answers questions.
Inference never uses the network.

Built with Kotlin, Jetpack Compose (Material 3), MVVM + Clean Architecture, Hilt, Room, DataStore,
WorkManager, Navigation Compose, Coil, Credential Manager (Google Sign-In) and optional Firebase.

---

## Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Getting started](#getting-started)
4. [Google Sign-In & Firebase (optional)](#google-sign-in--firebase-optional)
5. [The on-device AI engine](#the-on-device-ai-engine)
6. [Tools & prompting](#tools--prompting)
7. [Permissions](#permissions)
8. [Testing](#testing)
9. [Verification performed](#verification-performed)
10. [Known limitations](#known-limitations)
11. [Deliverables map](#deliverables-map)

---

## Features

### Launcher
- **Real HOME app**: `MAIN`/`HOME`/`DEFAULT` filter, `singleTask` + `stateNotNeeded`, and a transparent
  `Theme.Material3.DayNight.NoActionBar` window with `windowShowWallpaper`, so the live/static
  wallpaper renders natively behind Compose. Settings has a `RoleManager.ROLE_HOME` request button.
- **Home screen**: minimalist clock and date, a next-alarm/reminder pill, a 5-slot dock, and the
  **AI Command Bar**. The bar doubles as instant fuzzy app search and offers voice input when an
  offline recognizer exists.
- **Readable on any wallpaper**: text colour follows `WallpaperColors` (or a Light/Dark override)
  and is never relied on alone:
  - every word on the wallpaper carries a halo (dark text) or drop shadow (light text)
  - chips get a backing of the opposite polarity
  - optional top and bottom gradients shade the regions that hold text
  
  So a wallpaper that is light overall but dark behind the clock no longer hides it. Status-bar
  icon colour is applied when a screen *resumes*, never "restored" when one is disposed, which
  removes the race that left dark icons over dark screens after navigation.
- **Gestures**: swipe up opens the drawer, swipe down opens notifications, swipe left opens the AI
  chat, double-tap locks the screen, long-press opens the home menu.
  - Double-tap lock prefers `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`, which keeps biometric
    unlock working. It falls back to `DevicePolicyManager.lockNow()`.
- **App drawer**:
  - an **A–Z** grid or list with a draggable letter fast-scroller
  - an **AI Smart Categories** view with a live "AI is organizing…" banner
  - typo-tolerant fuzzy search (prefix, acronym, subsequence, edit distance, Unicode-aware)
  - pull down at the top to close
- **Long-press menu**: App info, Uninstall, Pin to / Remove from dock (with reordering), Hide,
  Rename, and Change category (a manual override the AI never replaces).
- **Live package sync**: a runtime `BroadcastReceiver` (`PACKAGE_ADDED/REMOVED/CHANGED/REPLACED`)
  plus `LauncherApps.Callback`, debounced into one incremental Room sync. New installs trigger
  incremental AI categorization.

### Themes & minimal mode
Settings → Appearance has a live-preview theme picker:

| Theme | Look |
|---|---|
| **Material You** | Colours from your wallpaper (Android 12+; Classic colours on 10/11). |
| **Classic** | Indigo and violet accents, light or dark. |
| **AMOLED** | True-black surfaces (dark-only); keeps wallpaper accents on Android 12+. |
| **Colourful** | Vibrant multi-hue palette, gradient clock, colour-coded drawer categories. |
| **Neon** | Deep-night surfaces, glowing cyan clock, gradient outlines, neon category colours (dark-only). |
| **Mono** | Black, white and greys, with a hairline clock. |

Other appearance options:
- **Light or dark**: System, Light or Dark (disabled for the dark-only themes).
- **App icons**: Original, Themed or Greyscale. Themed tints Android 13 monochrome icons with the
  theme; apps without one are shown in greyscale.
- **Home screen text**: Auto, Light or Dark.
- **Text shade**: the readability gradients.

**Minimal mode** turns the home screen into words: a thin clock, favourites as large text (typing
filters them into search results), and an underlined command bar. The drawer becomes text-only
lists.

Every scheme sets all Material roles explicitly. `ColorSchemeContrastTest` enforces WCAG AA in
each theme: 4.5:1 for every text/background pair, 3:1 for icons and outlines. It also covers the
category colours, and the home chips over black, grey and white wallpaper.

### On-device AI
- **Model onboarding**:
  - a WorkManager foreground (`dataSync`) download from Hugging Face, pinned to a commit
  - progress, speed and ETA, with pause/resume (HTTP `Range`) and SHA-256 verification
  - optional import of a local `.gguf` file, validated by its magic bytes
  - a "Skip — use the launcher without AI" path that keeps the launcher usable
- **Dual-mode Qwen3**:
  - *Fast Action* (`enable_thinking=false` + `/no_think`; T 0.7, top-p 0.8, top-k 20)
  - *Reasoning* (`enable_thinking=true`; T 0.6, top-p 0.95, top-k 20), with `<think>` content shown
    in a collapsible **Thought process** accordion
- **AI chat**:
  - streaming Markdown replies and persistent history in Room
  - quick-action chips ("Set alarm for 7 AM", "Organize my apps", "Summarize my day", …)
  - inline **confirmation cards** for every tool with side effects
  - a live RAM/status chip with an auto-unload countdown
- **Smart categorization**:
  1. the app's own `android:appCategory`
  2. package/keyword heuristics, for an instant first pass
  3. Qwen3 in batches with a **GBNF grammar** that only admits
     `{"<packageName>": "<Category>"}` for the batch's exact package names

  Categories are persisted per package and computed once per install.
- **Resource protection**:
  - lazy loading and memory-mapped weights
  - a q8_0 KV cache (half the RAM of f16)
  - an **auto-unload watchdog** (3 min by default, configurable) and `onTrimMemory` unloading
  - a free-RAM check before loading
  - thermal and battery-saver thread throttling
  - foreground requests pre-empt background categorization

### Account & backup
- **Google Sign-In** with Credential Manager + `GetGoogleIdOption`: previously authorised accounts
  are offered silently first, then the full account picker. The ID token is optionally exchanged
  with Firebase Auth.
- **Guest mode** ("Continue offline") never requires the network or an account.
- **Encrypted backup** of settings, dock, hidden apps, aliases and AI categories:
  - Encryption: PBKDF2-HMAC-SHA256 (600k iterations) feeding AES-256-GCM, keyed by your passphrase.
  - Destinations: a local file (Storage Access Framework) or Firestore at
    `users/{uid}/backups/latest`, which stores ciphertext only (see `firestore.rules`).

---

## Architecture

```
presentation/          Jetpack Compose + ViewModels (MVVM)
  home/ drawer/ chat/ model/ onboarding/ settings/ components/ navigation/ theme/
        │  observes StateFlow / calls use cases & repositories
        ▼
domain/                Pure Kotlin: no Android framework types
  model/               AppInfo, ChatMessage, ToolInvocation, EngineState, LlmException, …
  repository/          AppRepository, ChatRepository, ModelRepository, … (interfaces)
  ai/                  LlmEngine (interface), Qwen3PromptBuilder, ModelOutputParser,
                       ToolDefinitions, ToolRouter, QuickCommandParser, SystemPrompts
  usecase/             ChatAgent (agent loop), CategorizeAppsUseCase, HeuristicCategorizer
  action/              ActionExecutor (interface)
        ▲  implemented by
        │
data/                  Android implementations
  llm/                 LocalLlmEngine, LlamaNative (JNI), DeviceResources
  local/               Room: entities, DAOs, AppDatabase
  preferences/         DataStore settings & model config
  packages/            PackageRepository, PackageChangeReceiver, Coil AppIconFetcher
  auth/ backup/ chat/ reminders/ download/ categorize/ actions/ system/
        │
        ▼ JNI
app/src/main/cpp/      llama_jni.cpp (JNI bridge) → llm_engine.cpp (C++ engine) → llama.cpp b12651
```

**Agent loop (`ChatAgent`)**:

```
user text ─► QuickCommandParser ──match──► tool card (no LLM; works without the model)
    │
    └─► Qwen3PromptBuilder (ChatML + <tools>) ─► LocalLlmEngine.generate() ── stream ──►
        ModelOutputParser ─► answer │ <think> │ <tool_call> ─► ToolRouter (validate & normalise)
            ├─ read-only tool (get_today_agenda): run, feed <tool_response>, generate again
            └─ side-effect tool: confirmation card ─► ActionExecutor ─► Android APIs
```

---

## Getting started

### Requirements
- Android Studio (latest stable) or the command line with **JDK 17+**.
- Android SDK packages: `platforms;android-37.1`, `ndk;29.0.14206865` (NDK r29), `cmake;3.31.6`.
  Android Studio and AGP install them automatically when their licences are accepted.
- An arm64 phone with **6 GB+ RAM** recommended. The model needs about 1.4 GB of free RAM while
  loaded.

### Clone & build

```bash
git clone --recurse-submodules <this-repo-url>
```

If you already cloned without `--recurse-submodules`, fetch the pinned llama.cpp:

```bash
git submodule update --init --depth 1
```

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and pick **AI Launcher**, or use *Settings ▸ Make AI Launcher your home app*.

The first native build compiles llama.cpp, seven arm64 CPU variants and the x86_64 emulator
build, which takes several minutes. Later builds are incremental. Native code is always compiled
optimised, even for debug builds, because llama.cpp is unusably slow at `-O0`.

#### Where llama.cpp comes from (in order)
1. `llama.cpp.dir=/path/to/llama.cpp` in `local.properties` (handy for llama.cpp development)
2. the git submodule at `app/src/main/cpp/llama.cpp` (pinned to tag **b12651**)
3. if neither exists (e.g. a "Download ZIP" checkout), CMake downloads the `b12651` tarball

#### `local.properties` keys (all optional, never committed)

```properties
sdk.dir=/path/to/Android/sdk
google.web.client.id=1234567890-abc.apps.googleusercontent.com
llama.cpp.dir=/path/to/llama.cpp
release.store.file=/path/to/release.jks
release.store.password=…
release.key.alias=…
release.key.password=…
```

The same keys can be passed as `-P` Gradle properties or environment variables
(`GOOGLE_WEB_CLIENT_ID`, …).

---

## Google Sign-In & Firebase (optional)

The launcher and all AI features work without either; the sign-in button is hidden when it is not
configured.

1. **OAuth clients** (Google Cloud Console ▸ APIs & Services ▸ Credentials):
   - a **Web application** client: put its client ID in `google.web.client.id`. Credential Manager
     needs it as the `serverClientId`.
   - an **Android** client for package `com.ailauncher` with your signing certificate's SHA-1
     (`./gradlew signingReport`).
2. **Firebase** (for cloud backup):
   - Create a project and add the Android app `com.ailauncher`.
   - Download `google-services.json` into `app/`. The Google Services plugin is applied
     automatically when this file exists, and `default_web_client_id` is then used if
     `google.web.client.id` is not set.
   - Enable **Authentication ▸ Google** and create a **Cloud Firestore** database.
   - Deploy the rules (shown below).

```bash
firebase deploy --only firestore:rules
```

---

## The on-device AI engine

| | |
|---|---|
| Model | Qwen3-1.7B, Q4_K_M, 1,107,409,472 bytes, SHA-256 `b139949c…1897` |
| Source | `huggingface.co/unsloth/Qwen3-1.7B-GGUF`, pinned to commit `d7f544ee`. The official `Qwen/Qwen3-1.7B-GGUF` repo publishes Q8_0 only. |
| Location | `filesDir/models/qwen3-1.7b-q4_k_m.gguf`. Excluded from cloud backup; included in device-to-device transfer. |
| Runtime | llama.cpp **b12651**, CPU only, using the `llama_batch_ext` / `llama_process` API |

**Runtime CPU dispatch.** arm64 builds use `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`: ggml ships
seven CPU kernels, from armv8.0 through armv8.2 (dotprod/fp16) and armv8.6 (i8mm) to armv9
(SVE2/SME). It picks the fastest one the SoC supports at runtime, so newer phones get the fast
kernels and older ones never hit an illegal instruction inside the launcher process.

AGP only packages `SHARED` libraries, so `CMakeLists.txt` builds ggml's `MODULE` backends as
`SHARED`. `jniLibs.useLegacyPackaging = true` extracts them to `nativeLibraryDir`, where ggml scans
for them. Without both, the variants are compiled but never shipped.

**`llm_engine.cpp` features**:
- **Prefix KV caching.** The longest common token prefix with the previous request is reused.
  System prompts are kept free of the clock so the ~1k-token tools block is evaluated once, and
  `ChatAgent.warmUp()` pre-fills it when the chat opens.
- **Grammar-constrained sampling** with rejection sampling: the expensive full-vocabulary grammar
  check runs only when needed.
- **UTF-8-safe streaming.** Multi-byte characters are never split across callbacks.
- **Cancellation** through the llama abort callback, so even a long prompt evaluation stops
  promptly, plus KV bookkeeping that survives an aborted batch.

**`LocalLlmEngine` (Kotlin)**:
- All native calls run on one 16 MB-stack inference thread; coroutine cancellation maps to
  `stopGeneration()`.
- An idle watchdog unloads the model after N minutes, shown as a countdown in the chat status sheet.
- `onTrimMemory` triggers an unload.
- Loading is refused when free RAM is insufficient.
- Threads (default 4 performance cores) are throttled at `THERMAL_STATUS_MODERATE` and in battery
  saver.
- Background work is refused when the device is hot.

---

## Tools & prompting

`Qwen3PromptBuilder` is a byte-exact Kotlin port of the Jinja chat template embedded in the GGUF.
This includes Python-style `tojson` separators, `reasoning_content` handling across multi-step tool
use, and grouped `<tool_response>` turns. It is verified against fixtures rendered with jinja2.

| Tool | Arguments | Android API |
|---|---|---|
| `set_alarm` | `hour`, `minute`, `label?`, `days?` | `AlarmClock.ACTION_SET_ALARM` + `EXTRA_SKIP_UI` |
| `set_timer` | `seconds`, `label?` | `AlarmClock.ACTION_SET_TIMER` |
| `create_reminder` | `title`, `datetime` (ISO) or `in_minutes` → `timestampMillis` | `CalendarContract.Events` + `Reminders` when a writable calendar exists; otherwise Room + exact `AlarmManager` + notification, re-armed after reboot |
| `launch_app` | `appName` | fuzzy match → `getLaunchIntentForPackage()` |
| `toggle_setting` | `wifi` / `bluetooth` / `flashlight` / `dnd`, `state?` | `CameraManager.setTorchMode`, `Settings.Panel`, notification policy |
| `sort_apps` | none | background categorization (WorkManager) |
| `get_today_agenda` | none (read-only) | calendar instances + reminders + next alarm, fed back to the model |

`ToolRouter` accepts what a 1.7B model actually emits (numbers as strings, `"7:30 pm"`, weekday
names, relative or absolute times) and validates it strictly. Two fixes came from replaying the
real model:
- invented repeat `days` for one-off alarms are dropped unless the user asked for repetition;
- an absolute `datetime` beats a stray `"in_minutes": 0`.

`QuickCommandParser` handles common commands ("open camera", "timer 5 min", "alarm at 7am",
"flashlight on") instantly, even before the model is downloaded.

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | One-time model download, optional Firebase backup. Inference is offline. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Resumable model download worker |
| `SET_ALARM` | `set_alarm` / `set_timer` through the clock app |
| `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` | Local reminders (inexact fallback if exact alarms are denied) |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Reminders in the user's calendar and "Summarize my day". Requested only when used. |
| `CAMERA` | Declared for OEM builds that gate the torch; `setTorchMode` itself needs no runtime grant |
| `ACCESS_NOTIFICATION_POLICY` | Do Not Disturb toggle |
| `EXPAND_STATUS_BAR` | Swipe-down notification shade fallback |
| `REQUEST_DELETE_PACKAGES` | Long-press ▸ Uninstall |
| `<queries>` `MAIN`+`LAUNCHER`, clock intents, … | Package visibility on Android 11+ without `QUERY_ALL_PACKAGES` |

The optional accessibility service is used **only** for the lock-screen and notification-shade
global actions. It cannot read window content. A prominent disclosure dialog is shown before
sending the user to Accessibility settings.

---

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:lintDebug
```

The JVM unit tests cover:
- the Jinja-parity prompt builder
- output and tool-call parsing, the tool router, and quick commands
- fuzzy search, backup crypto, and categorization parsing/grammar
- the Markdown parser
- theme contrast (WCAG AA for every theme), the wallpaper text-colour logic, and settings
  persistence and migration
- the exact prompts `ChatAgent` sends; these are exported to `app/build/prompt-fixtures/` for
  replay

**Screenshot test.** `ThemeScreenshotTest` uses Robolectric native graphics to render the home
screen, drawer and theme picker in every theme. The variants are minimal mode, light/dark/banded
wallpapers, and themed/greyscale icons. It fails on crashes or blank frames and writes PNGs to
`app/build/outputs/screenshots/` for review.

**JNI integration test.** `LlamaNativeHostTest` builds the same `llama_jni.cpp` / `llm_engine.cpp`
for the desktop and drives it through the real Kotlin `LlamaNative` declarations with a real GGUF.
It is skipped unless configured. Build the host bridge first:

```bash
cmake -S tools/llm-host-harness -B build/host -G Ninja -DLLAMA_CPP_DIR=$PWD/app/src/main/cpp/llama.cpp
```

```bash
cmake --build build/host
```

Then run the test:

```bash
./gradlew :app:testDebugUnitTest --tests '*LlamaNativeHostTest*' -Pailauncher.test.nativeDir=$PWD/build/host -Pailauncher.test.model=/path/to/qwen3-1.7b-q4_k_m.gguf
```

**Host harness.** Replay prompts (e.g. the exported fixtures) against the model:

```bash
build/host/llm_host_harness --model /path/to/model.gguf app/build/prompt-fixtures/fast_alarm.txt app/build/prompt-fixtures/fast_launch.txt
```

---

## Verification performed

All of the following was run while building this project:

- `assembleDebug` and `assembleRelease` (R8 + resource shrinking, 16 MB APK) succeed with **zero
  compiler warnings**. `lintDebug` is clean except for the intentional `OldTargetApi`
  (targetSdk 36).
- **65 JVM unit tests pass**, including one that renders 23 theme/layout screenshots. The JNI
  integration test passes against the real Qwen3-1.7B Q4_K_M:
  - streaming of CJK and emoji text
  - prefix cache, GBNF grammar, and both cancellation paths
  - Kotlin exceptions propagating through C++
- **Release APK**:
  - all 7 arm64 CPU variants and the x86_64 libraries are packaged
  - every `.so` is 16 KB page-aligned (Android 15+)
  - R8 keeps the JNI surface
  - the merged manifest has the HOME filter, `singleTask` and `extractNativeLibs`
- **The app's own prompts, replayed through the native engine and real model**, produced correct
  tool calls: `set_alarm`, `launch_app`, `create_reminder`, and
  `toggle_setting(flashlight)` (from "it's dark in here, I need some light"). A factual question
  produced a plain answer, and the agenda follow-up was summarized. Prefix caching cut follow-up
  prompt evaluation from ~11 s to under 1 s on a 4-thread x86 host, which ran at about 12 tok/s.

**Not verified here**: running on a physical device or emulator (this build machine has no
KVM/device). Before shipping, test on real arm64 hardware: home role, gestures, download
foreground service, reminders after reboot, Google Sign-In with your OAuth clients, and on-device
tok/s.

---

## Known limitations

- **Wi-Fi and Bluetooth** cannot be switched programmatically on Android 10+. The tool opens the
  system panel or settings page instead. The flashlight and DND (after policy access) toggle
  directly.
- **Accessibility service & Google Play**: publishing requires the accessibility declaration in
  Play Console. The device-admin fallback may require the PIN instead of biometrics on the next
  unlock.
- **Model accuracy**: a 1.7B model makes mistakes (e.g. a category or a time). Mitigations include
  the grammar, `appCategory` and heuristics, router sanity checks, manual overrides, and
  confirmation before every action.
- **UI copy is English** and inline in Compose. Moving it to `strings.xml` for localization is a
  follow-up; system-facing strings are already resources.
- **x86_64** is built for emulators (AVX2) and is slow; arm64 is the target.
- **targetSdk is 36**, which meets the current Google Play requirement. compileSdk is 37.1, which
  Compose 1.13 requires. Raise targetSdk after testing Android 17 behaviour changes.

---

## Deliverables map

| Step | Files |
|---|---|
| 1. Gradle & manifest | `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro` |
| 2. Native JNI bridge | `app/src/main/cpp/CMakeLists.txt`, `llama_jni.cpp` (`loadModel`, `generateStream`, `stopGeneration`, `unloadModel`, …), `llm_engine.{h,cpp}` |
| 3. Data layer | `data/local` (`AppEntity`, `ChatMessageEntity`, `ReminderEntity`, DAOs, `AppDatabase`), `data/preferences`, `data/auth/GoogleAuthRepository.kt`, `data/packages/PackageRepository.kt` |
| 4. Domain & AI engine | `data/llm/LocalLlmEngine.kt`, `domain/ai/Qwen3PromptBuilder.kt`, `domain/ai/ModelOutputParser.kt`, `domain/ai/ToolRouter.kt`, `domain/action/ActionExecutor.kt` + `data/actions/AndroidActionExecutor.kt`, `domain/usecase/ChatAgent.kt` |
| 5. Presentation | `presentation/home/LauncherHomeScreen.kt`, `drawer/AppDrawerScreen.kt`, `chat/AiChatScreen.kt`, `model/ModelDownloadScreen.kt`, `settings/SettingsScreen.kt`, `onboarding/OnboardingScreen.kt` |

## License notes

The app code is yours to license. llama.cpp is MIT; Qwen3 weights are Apache-2.0.
