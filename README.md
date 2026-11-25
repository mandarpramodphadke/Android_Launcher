# AI Launcher

An offline-first Android home-screen launcher with a private on-device assistant. **Qwen3**
(0.6B, 1.7B or 4B, GGUF Q4_K_M) runs locally through **llama.cpp** via JNI, on the CPU or
optionally the GPU (Vulkan). It sorts your apps into smart categories, sets alarms, timers and
reminders, opens apps and folders, manages your to-dos and day plan, finds large or forgotten
files, spots unused apps, summarizes notifications, calls and messages contacts, and answers
questions. Inference never uses the network, and no user data is ever sent anywhere.

Built with Kotlin, Jetpack Compose (Material 3), MVVM + Clean Architecture, Hilt, Room, DataStore,
WorkManager, Navigation Compose, Coil, Credential Manager (Google Sign-In) and optional Firebase.

---

## Contents

1. [What's new in 1.5: System info](#whats-new-in-15-system-info)
1. [What's new in 1.4: to-do widgets and smart folders](#whats-new-in-14-to-do-widgets-and-smart-folders)
1. [What's new in 1.3: Google Play version](#whats-new-in-13-google-play-version)
1. [What's new in 1.2](#whats-new-in-12)
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

## What's new in 1.5: System info

A page after the AI chat, in the **full** version first (flag `SYSTEM_INFO`). From the home screen,
swipe left past the last page to the assistant, then swipe left again; swipe right (or Back) to
return. The assistant's top bar and the *AI Launcher* tools folder also open it.

- **At a glance**: the phone's name, Android version and chip, with rings for battery, RAM and
  storage.
- **Battery**: level, charging state and source, time to full, health, temperature, voltage,
  current draw, estimated capacity, charge cycles (Android 14+) and technology.
- **Processor**: chip name, cores grouped by type (e.g. 1 × Cortex-X3, 3 × Cortex-A715, 4 ×
  Cortex-A510) with top speeds, each core's live speed, a one-minute "clock load" graph,
  architecture, governor and instruction sets.
- **Graphics**: GPU name and vendor (from OpenGL ES), OpenGL ES and Vulkan versions; live load
  and clock on phones that expose them (Adreno).
- **Memory and storage**: RAM installed, usable, used (with a one-minute graph) and available,
  swap (zRAM); storage capacity, used and free, including SD cards.
- **Display, device, software, network, temperature & power, cameras, features and sensors**:
  resolution, density, size, refresh rate, HDR; brand, model, code name, SoC, board; Android
  version and API level, security patch, build, kernel, bootloader, ABIs, up time; connection
  type and speed estimate; thermal state and battery saver; each camera's megapixels and focal
  length; NFC, eSIM, UWB, fingerprint and other features; every sensor.

Live values refresh every second while the page is open and stop when you leave it. *Copy all*
puts the whole report on the clipboard. Everything is read on the phone through public Android
APIs that need no permission; nothing that identifies the phone (serial number, IMEI, Android ID,
MAC address) is read, and nothing is saved or sent anywhere.

---

## What's new in 1.4: to-do widgets and smart folders

In the **full** version first; the Google Play version gets them after they have been tried on a
phone (see "How new features reach the Play version" in [docs/PLAY_RELEASE.md](docs/PLAY_RELEASE.md)).
Each is behind a flag in `app/build.gradle.kts`: `TODO_WIDGETS`, `HOME_DRAG_AND_DROP` and
`SMART_FOLDERS`.

- **To-do widgets**, drawn by the launcher itself in your theme's colours. Long-press the wallpaper
  ▸ *Widgets* ▸ *AI Launcher*:
  - **To-do list**: overdue first, then due today, then by priority. Tick items off (they stay
    struck through for ten minutes, so a mis-tap is easy to undo), tap one to edit it, or tap +.
    The tune button (long-press the widget) limits it to one list, e.g. *Shopping*.
  - **Today**: the block of your day plan that is on now (or next), and what is due today.
  - **Add a to-do**: a bar that opens the to-do editor in one tap.

  They resize and move like any widget, and update the moment a to-do changes (in the planner, by
  the assistant, or on another widget).
- **Drag and drop on the home screen**: long-press an app or folder and move your finger.
  - Drop it on an empty cell to move it, or at the screen edge to go to the next page (past the
    last page, a new page is added).
  - Drop an app on another app to make a folder: it's named after their shared category
    ("Social") and opens so you can rename it. Drop an app on a folder to put it inside.
  - Drop it on the dock to pin it there.
  - Lift your finger without moving to get the usual menu.
- **Smart folders** that fill themselves. In the folder editor, choose *Fills itself*:
  - **New apps**: installed in the last 7, 14 or 30 days
  - **Not used lately**: not opened for 30, 60 or 90 days (long-press an app to uninstall it)
  - **Most used**: your 8, 12 or 16 most opened apps of the last four weeks
  - **By category**: every app in one of the drawer's categories (Games, Social, …)
  - **AI Launcher tools**: assistant, *My day*, planner, to-dos, *Catch up*, Private Space,
    widgets and settings, as one folder

  The usage rules use Android's Usage access when you allow it (it stays on the phone), and
  otherwise the launches made from AI Launcher. Smart folders show a small sparkle badge, never
  include Private Space apps, and can be turned back into a regular folder (*Pick apps myself*).

The database moves to version 3 (`Migration2To3` adds two nullable columns), so nothing you have
set up changes.

---

## What's new in 1.3: Google Play version

The app now builds in two versions from the same code (Gradle product flavors):

- **`full`** (application ID `com.ailauncher`): installed directly from an APK. Every feature,
  unchanged.
- **`play`**: ready for Google Play. It has its own application ID, declares no storage permission
  (the file tools search only folders the user picks with Android's folder picker), drops the
  unused camera permission, and has no Google sign-in or cloud backup. A Gradle check
  (`verifyPlayReleasePlayPolicy`) fails the build if a permission Play restricts ever reaches its
  manifest, or if a release has nowhere to send reports of AI replies.

New in both versions:

- **Report** under every assistant reply (Google Play's AI-generated content policy): pick a reason,
  add details, and choose whether to include your question. The reply is hidden on the phone at
  once; the report goes to the developer's Google Form in the background, or opens an email draft
  if no form is configured.
- **Privacy policy** and **open-source licences** screens in Settings. The policy's single source is
  `docs/privacy-policy.md`, which GitHub Pages can publish for the Play listing.
- A prominent disclosure before the app opens Accessibility settings, a "can make mistakes"
  notice in the assistant, and a short safety rule in its system prompt.

The full publishing guide, with suggested answers for every Play Console form, is
[docs/PLAY_RELEASE.md](docs/PLAY_RELEASE.md).

---

## What's new in 1.2

An incremental update of the 1.1 code base: every existing feature, tool contract and test is kept.

- **One text field everywhere.** `LauncherTextField` / `LauncherCommandBar` (`BasicTextField` +
  `TextFieldState`) replace every input: home command bar, drawer search, chat, folder name and
  description, to-do and planner editors, rename and backup passphrase dialogs. The placeholder
  shows only while the field is empty *and* unfocused, never wraps, and there is a one-tap clear.
  **Cursor fix:** the cursor used to trail behind the letter just typed because each keystroke made
  a round trip through a ViewModel `StateFlow`; the text state now lives in the ViewModel as a
  `TextFieldState` that the field edits synchronously, and search reads it through `snapshotFlow`.
- **Multi-page home** (`HorizontalPager`) with a page indicator above the dock, add / remove /
  reorder pages, and Home snapping back to page 1. Apps, folders and **widgets** are placed on a
  4- or 5-column grid (`HomeItemEntity`); widgets come from a searchable, grouped picker, go through
  the system bind and configure flows, and can be resized or removed after a long-press.
- **Custom folders** with a name, a purpose ("apps for my job") and an accent colour: a drawer tab,
  folders on home pages or in the dock, and "open my Work folder" from the assistant.
- **Motion:** apps open with a clip-reveal from the tapped icon; the drawer follows your finger and
  settles on a low-bounce spring behind a scrim; the command bar morphs into the chat input
  (`SharedTransitionLayout`); a 160 ms dim/scale-out precedes double-tap lock, and the clock,
  pages, dock and command bar spring back in one after another on unlock.
- **Seven new offline tools:** `list_unused_apps`, `find_largest_files`,
  `query_files_by_activity`, `manage_todo`, `manage_planner`, `summarize_notifications`,
  `contact_action`, plus a **To-dos & Daily Planner** screen and planner blocks / urgent to-dos in
  `get_today_agenda`.
- **On-device context:** an opt-in notification digest (badges, one-tap OTP / meeting / parcel
  pills, deleted after 24 h), "Share → AI Launcher" for text and PDFs, clipboard suggestions when
  you focus the command bar, five predicted apps in the drawer, app shortcuts in the long-press
  menu, and a biometric **Private Space** for hidden apps.
- **Model switcher** (Qwen3 0.6B / 1.7B / 4B, or any imported `.gguf`) and optional **GPU offload
  (Vulkan)** with an automatic CPU fallback.

---

## Features

### Launcher
- **Real HOME app**: `MAIN`/`HOME`/`DEFAULT` filter, `singleTask` + `stateNotNeeded`, and a transparent
  `Theme.Material3.DayNight.NoActionBar` window with `windowShowWallpaper`, so the live/static
  wallpaper renders natively behind Compose. Settings has a `RoleManager.ROLE_HOME` request button.
- **Home screen**: swipeable pages (page 1 has the clock and date, a next-alarm/reminder pill,
  notification pills and a to-do/planner glance chip), a page indicator, a 5-slot dock shared by
  apps and folders, and the **AI Command Bar**. The bar doubles as instant fuzzy app search,
  offers voice input when an offline recognizer exists, and suggests actions for what is on the
  clipboard (open link, call, summarize, extract tasks, …) when you focus it.
- **Pages, grid and widgets**:
  - 4 or 5 columns; 48–52 dp icons with optional Android 13 themed (monochrome) icons; one-line
    12 sp labels with a wallpaper shadow
  - long-press the wallpaper ▸ *Home pages* to add, remove or reorder pages; Home returns to page 1
  - long-press the wallpaper ▸ *Widgets* for every installed widget grouped by app, with previews
    and search. `LauncherAppWidgetHost` handles `ACTION_APPWIDGET_BIND`, configuration activities
    and `startListening`/`stopListening`; long-press a widget for resize handles, page moves and
    removal
- **Custom folders**: create from the drawer's *Folders* tab, the home menu, an app's long-press
  menu or (full version) by dropping one app onto another (name, description, accent colour,
  searchable multi-select app list). Open a folder to see its purpose and apps, add/remove apps,
  edit, pin to the dock, add to a home page or delete it. Smart folders fill themselves (new apps,
  not used lately, most used, a category, or the launcher's own tools).
- **To-do widgets** (full version): a to-do list, *Today* and an *Add a to-do* bar, drawn by the
  launcher and listed first in the widget picker.
- **Readable on any wallpaper**: text colour follows `WallpaperColors` (or a Light/Dark override)
  and is never relied on alone:
  - every word on the wallpaper carries a halo (dark text) or drop shadow (light text)
  - chips get a backing of the opposite polarity
  - optional top and bottom gradients shade the regions that hold text
  
  So a wallpaper that is light overall but dark behind the clock no longer hides it. Status-bar
  icon colour is applied when a screen *resumes*, never "restored" when one is disposed, which
  removes the race that left dark icons over dark screens after navigation.
- **Gestures**: swipe up drags the drawer open, swipe down opens notifications, swipe left past the
  last page opens the AI chat, double-tap locks the screen (after a 160 ms dim/scale-out), and
  long-press opens the home menu.
  - Double-tap lock prefers `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN`, which keeps biometric
    unlock working. It falls back to `DevicePolicyManager.lockNow()`.
- **App drawer** (an overlay of the home screen that tracks your finger, with a spring settle and
  a backdrop scrim):
  - an **A–Z** grid or list with a draggable letter fast-scroller
  - an **AI Smart Categories** view with a live "AI is organizing…" banner
  - a **Folders** tab, a **Suggested** row of five predicted apps (launch habits with a 7-day
    half-life, time of day, weekday/weekend, and headphones / Bluetooth / car audio), and unread
    badges
  - typo-tolerant fuzzy search (prefix, acronym, subsequence, edit distance, Unicode-aware)
  - pull down at the top to close
- **Long-press menu**: the app's own shortcuts (`LauncherApps.getShortcuts`), Pin to / Remove from
  dock (with reordering), Add to home screen, Add to folder, Change category (a manual override
  the AI never replaces), Rename, Move to Private Space, App info and Uninstall.
- **Private Space**: hidden apps live behind a `BiometricPrompt` (fingerprint, face or the device
  PIN), in the drawer and in Settings.
- **App launch animation**: `LauncherApps.startMainActivity` with the icon's bounds and
  `ActivityOptions.makeClipRevealAnimation` (scale-up fallback).
- **Live package sync**: a runtime `BroadcastReceiver` (`PACKAGE_ADDED/REMOVED/CHANGED/REPLACED`)
  plus `LauncherApps.Callback`, debounced into one incremental Room sync. New installs trigger
  incremental AI categorization.

### To-dos & Daily Planner
A screen reachable from the home glance chip, the drawer (search "todo" or the Folders tab) and the
assistant's result cards. *To-dos*: quick add, lists (Inbox, Errands, …), priorities, due dates,
overdue / today / upcoming / no-date sections. *Day plan*: a timeline of your time blocks, calendar
events (read-only, with permission) and free slots between 08:00 and 21:00; tap a free slot to plan
it. The assistant reads and edits the same data through `manage_todo` and `manage_planner`.

### Notifications, sharing and contacts
- **Notification digest** (opt-in, with a disclosure first): a `NotificationListenerService` keeps
  the title and text of recent notifications in Room for 24 hours, on-device only. It powers unread
  badges, home pills for one-time codes (copy), meeting links (join) and parcel tracking numbers
  (track), and `summarize_notifications` ("what did I miss?"). Turning it off deletes the data.
- **Share → AI Launcher**: `text/plain` and `application/pdf` open in the assistant with
  *Summarize*, *Extract tasks* and *Ask about it*. PDF text is extracted on-device with
  `PdfRenderer` (Android 15+).
- **Contacts**: "call mum", "text Sam I'm running late", "WhatsApp Alex" find the contact after a
  `READ_CONTACTS` check and always show a confirmation card first.

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
- **Model switcher**: Qwen3 **0.6B** (fastest), **1.7B** (recommended) or **4B** (best quality,
  8 GB+ RAM), all Q4_K_M, or any `.gguf` you import. Each keeps its own download state; switching
  frees the loaded model and the next request loads the new one.
- **Processor**: *CPU Only (Safe Default)* or *GPU Offload (Vulkan)*. The Vulkan backend is loaded
  only when you choose it; if there is no usable GPU, loading fails, or the driver crashes the
  launcher, the engine falls back to the CPU on its own and Settings says why.
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
  home/ drawer/ folders/ planner/ chat/ model/ onboarding/ settings/ components/ navigation/ theme/
        │  observes StateFlow / calls use cases & repositories
        ▼
domain/                Pure Kotlin: no Android framework types
  model/               AppInfo, ChatMessage, ToolInvocation, EngineState, LlmException, …
  repository/          AppRepository, ChatRepository, ModelRepository, HomeLayout/Folder/Dock,
                       Todo/Planner, AppUsage, File, NotificationDigest, Contact, Shortcut (interfaces)
  ai/                  LlmEngine (interface), Qwen3PromptBuilder, ModelOutputParser,
                       ToolDefinitions, ToolRouter, QuickCommandParser, SystemPrompts,
                       DateRangeParser, ArgumentGrounding
  usecase/             ChatAgent (agent loop), CategorizeAppsUseCase, HeuristicCategorizer,
                       AgendaBuilder, FreeSlots
  home/ notifications/ clipboard/ suggestions/   HomeGridPlanner, NotificationSmartExtractor,
                       ClipboardAnalyzer, AppSuggestionRanker (all pure, unit-tested)
  action/              ActionExecutor, LauncherNavigator (interfaces)
        ▲  implemented by
        │
data/                  Android implementations
  llm/                 LocalLlmEngine, LlamaNative (JNI), DeviceResources, GpuOffloadGuard
  local/               Room: entities, DAOs, AppDatabase (v2 + Migration1To2)
  preferences/         DataStore settings & model config
  packages/            PackageRepository, PackageChangeReceiver, Coil icon fetchers, shortcuts
  home/ widgets/       home layout, folders, dock, LauncherWidgetHost
  usage/ files/ notifications/ contacts/ productivity/ share/
  auth/ backup/ chat/ reminders/ download/ categorize/ actions/ system/
        │
        ▼ JNI
app/src/main/cpp/      llama_jni.cpp (JNI bridge) → llm_engine.cpp (C++ engine) → llama.cpp b12651
                       (+ libggml-vulkan.so on arm64, loaded only for GPU offload)
```

**Agent loop (`ChatAgent`)**:

```
user text ─► QuickCommandParser ──match──► tool card (no LLM; works without the model)
    │
    └─► Qwen3PromptBuilder (ChatML + <tools>) ─► LocalLlmEngine.generate() ── stream ──►
        ModelOutputParser ─► answer │ <think> │ <tool_call> ─► ToolRouter (validate & normalise)
            ├─ read-only tool (agenda, files, unused apps, lists, digest): run, feed
            │  <tool_response>, generate again; the card shows the full result with buttons
            └─ side-effect tool: confirmation card ─► ActionExecutor ─► Android APIs
```

---

## Getting started

### Requirements
- Android Studio (latest stable) or the command line with **JDK 17+**.
- Android SDK packages: `platforms;android-37.1`, `ndk;29.0.14206865` (NDK r29), `cmake;3.31.6`.
  Android Studio and AGP install them automatically when their licences are accepted.
- An arm64 phone with **6 GB+ RAM** recommended. Qwen3-1.7B needs about 1.4 GB of free RAM while
  loaded (0.6B about 0.7 GB, 4B about 3.2 GB).
- For the optional Vulkan backend (on by default for arm64): a host C/C++ compiler (gcc or clang,
  used to build ggml's shader generator) and network access for the first configure, which fetches
  the pinned Vulkan-Headers and SPIRV-Headers tags. The NDK's `glslc` compiles the shaders. Set
  `ggml.vulkan=false` for a CPU-only build.

### Clone & build

```bash
git clone --recurse-submodules <this-repo-url>
```

If you already cloned without `--recurse-submodules`, fetch the pinned llama.cpp:

```bash
git submodule update --init --depth 1
```

```bash
./gradlew :app:assembleFullDebug
```

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

For the Google Play version, build an app bundle (see [docs/PLAY_RELEASE.md](docs/PLAY_RELEASE.md)):

```bash
./gradlew :app:bundlePlayRelease
```

Then press Home and pick **AI Launcher**, or use *Settings ▸ Make AI Launcher your home app*.

The first native build compiles llama.cpp, seven arm64 CPU variants, the arm64 Vulkan backend
(about 1,200 compute shaders) and the x86_64 emulator build, which takes a while. Later builds are incremental. Native code is always compiled
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
ggml.vulkan=true                                  # false = CPU-only native build
vulkan.headers.dir=/path/to/Vulkan-Headers        # offline builds; default: fetch tag v1.4.369
spirv.headers.dir=/path/to/SPIRV-Headers          # default: fetch tag vulkan-sdk-1.4.363.0
play.application.id=io.github.example.ailauncher # Google Play version (permanent once uploaded)
support.email=you@example.com                      # privacy policy contact; email fallback for reports
report.form.url=https://docs.google.com/forms/…   # in-app AI-reply reports, see docs/report-form.md
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
| Models | Qwen3-0.6B Q4_K_M (396,705,472 bytes, SHA-256 `ac2d9771…524a`, commit `50968a44`) · **Qwen3-1.7B Q4_K_M (default)** (1,107,409,472 bytes, `b139949c…1897`, commit `d7f544ee`) · Qwen3-4B Q4_K_M (2,497,281,312 bytes, `f6f85177…813a`, commit `22c9fc8a`) |
| Source | `huggingface.co/unsloth/Qwen3-*-GGUF`, each pinned to a commit. The official Qwen repos publish no Q4_K_M for these sizes. |
| Location | `filesDir/models/<id>.gguf`, imports in `filesDir/models/imported/`. Excluded from cloud backup; included in device-to-device transfer. |
| Runtime | llama.cpp **b12651** using the `llama_batch_ext` / `llama_process` API; CPU by default, Vulkan when GPU offload is on |

**Runtime CPU dispatch.** arm64 builds use `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`: ggml ships
seven CPU kernels, from armv8.0 through armv8.2 (dotprod/fp16) and armv8.6 (i8mm) to armv9
(SVE2/SME). It picks the fastest one the SoC supports at runtime, so newer phones get the fast
kernels and older ones never hit an illegal instruction inside the launcher process.

AGP only packages `SHARED` libraries, so `CMakeLists.txt` builds ggml's `MODULE` backends as
`SHARED`. `jniLibs.useLegacyPackaging = true` extracts them to `nativeLibraryDir`, where the engine
scans for them. Without both, the variants are compiled but never shipped.

**Selective backend loading.** `LlmEngine::InitBackends` scores the `libggml-cpu-*.so` variants and
loads only the best one; it never touches `libggml-vulkan.so`. `EnableGpuBackend()` loads Vulkan the
first time a model is loaded with GPU offload on.

**GPU offload (Vulkan, arm64).** With the toggle on, all layers are offloaded (`n_gpu_layers`) to
the first Vulkan device; on the CPU path an explicit empty device list keeps llama.cpp away from
the GPU. Fallbacks, all automatic:
1. no Vulkan device: load on the CPU;
2. GPU load throws: retry on the CPU;
3. the driver crashes the process: `GpuOffloadGuard` writes a marker before the GPU load and the
   first GPU generation; finding it at the next start switches the setting back to CPU only.

The reason is shown under *Settings ▸ On-device AI ▸ Processor*, and the status sheet shows where the
model runs. The NDK r29 `glslc` lacks cooperative-matrix and integer-dot extensions, so those
shader variants are skipped at build time (ggml detects this).

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
| `get_today_agenda` | none (read-only) | calendar instances + reminders + next alarm + planner blocks + urgent to-dos, fed back to the model |
| `list_unused_apps` | `inactiveDays?` (default from Settings, 30) | `UsageStatsManager` (with Usage access) merged with the launcher's own launch log; card with Open / Hide / Uninstall |
| `find_largest_files` | `limit` (15), `fileType` (all, video, image, audio, document, archive) | MediaStore sorted by `SIZE`; card with Open / Share |
| `query_files_by_activity` | `filterMode` (`unused_since`, `downloaded`, `opened_or_clicked`, `modified_range`), `unusedDays?`, `startDateIso?`, `endDateIso?`, `fileType` | MediaStore (`IS_DOWNLOAD` on 30+) and the launcher's `RecentFileAccessEntity` log |
| `manage_todo` | `action` (add, complete, delete, list), `title?`, `listName?`, `priority?`, `dueIso?`, `todoId?` | Room `TodoItemEntity` |
| `manage_planner` | `action` (schedule_block, reschedule, remove_block, get_day_plan), `dateIso?`, `startTime?`, `durationMinutes?`, `title?`, `blockId?` | Room `PlannerBlockEntity` + calendar events + free slots |
| `summarize_notifications` | `appName?` (read-only) | the opt-in on-device digest (last 24 h) |
| `contact_action` | `action` (call, sms, whatsapp), `contactName`, `message?` | Contacts provider → `ACTION_CALL` (or `ACTION_DIAL` without `CALL_PHONE`), `smsto:`, WhatsApp |

**Token budget.** The full tool block is ~1,730 tokens, so it is used when the context is ≥ 3072
tokens. *Auto* context (the new default) is 4096 on phones with 6 GB+ RAM and 2048 otherwise; at
2048 a compact profile with the original seven tools plus `manage_todo` (~1,060 tokens) keeps
room for the conversation. The system prompt still contains no clock, so the prefix KV cache keeps
follow-up turns fast.

**Argument grounding.** Replaying the real model showed slot errors a 1.7B model makes: "tomorrow"
scheduled for today, "two months" as 2 days, a hallucinated list name. `ArgumentGrounding` lets
unambiguous dates, times, durations, ranges and list names from the user's own words override the
model's values. Day-plan questions for another day ("when am I free tomorrow?") are rerouted from
`get_today_agenda` to `manage_planner(get_day_plan)`.

`ToolRouter` accepts what a 1.7B model actually emits (numbers as strings, `"7:30 pm"`, weekday
names, relative or absolute times) and validates it strictly. Two fixes came from replaying the
real model:
- invented repeat `days` for one-off alarms are dropped unless the user asked for repetition;
- an absolute `datetime` beats a stray `"in_minutes": 0`.

`QuickCommandParser` handles common commands ("open camera", "timer 5 min", "alarm at 7am",
"flashlight on", "open my Work folder", "add milk to my errands list", "largest videos",
"what did I miss", "call mum") instantly, even before the model is downloaded.

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
| `PACKAGE_USAGE_STATS` (special access) | `list_unused_apps` and suggested apps. Optional; the launcher's own launch log is used without it. |
| `READ_MEDIA_IMAGES` / `_VIDEO` / `_AUDIO` (`READ_EXTERNAL_STORAGE` ≤ 32) | File tools. Requested from the tool card or Settings. |
| `MANAGE_EXTERNAL_STORAGE` (optional special access) | Include documents and archives in file searches |
| `READ_CONTACTS`, `CALL_PHONE` | `contact_action`, requested on confirmation. Without `CALL_PHONE` the dialer opens instead. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (user-granted) | Opt-in notification digest, after a disclosure. Data stays on-device and is purged after 24 h. |
| `USE_BIOMETRIC` | Private Space unlock |
| `<queries>` `MAIN`+`LAUNCHER`, clock intents, widget providers, `VIEW */*`, WhatsApp, … | Package visibility on Android 11+ without `QUERY_ALL_PACKAGES` |

The optional accessibility service is used **only** for the lock-screen and notification-shade
global actions. It cannot read window content. A prominent disclosure dialog is shown before
sending the user to Accessibility settings.

---

## Testing

```bash
./gradlew :app:testFullDebugUnitTest :app:testPlayDebugUnitTest
```

```bash
./gradlew :app:lintFullDebug :app:lintPlayDebug
```

The JVM unit tests cover:
- the Jinja-parity prompt builder
- output and tool-call parsing, the tool router, and quick commands
- fuzzy search, backup crypto, and categorization parsing/grammar
- the Markdown parser
- theme contrast (WCAG AA for every theme), the wallpaper text-colour logic, and settings
  persistence and migration
- the exact prompts `ChatAgent` sends; these are exported to `app/build/prompt-fixtures/` for
  replay (one fixture per new tool, plus the full and compact tool prefixes)
- v2: the Room 1 → 2 migration (Robolectric, from the exported v1 schema), the new tools' routing
  and argument grounding (including verbatim real-model outputs), quick commands, date-range and
  day parsing, the notification extractor, clipboard analysis, app suggestions, the home grid
  planner, the agenda builder, free slots and the model catalog
- 1.5: System info: /proc/cpuinfo, /proc/meminfo and CPU range parsing, core names and clusters,
  clock load, size and speed formatting, the page's sections and "Copy all" text, and the swipe
  from the assistant (which ignores swipes on text fields and sideways-scrolling rows)
- 1.4: the Room 2 → 3 migration (from the exported v2 schema, with folders, apps and widgets on
  the pages), smart-folder rules and their storage, the drop planner, to-do widget ordering and
  the *Today* overview, and `HomeDragAndDropTest`, which drives real touch input through the home
  screen: long-press and drag onto an app (new folder), a folder, a smart folder and an empty
  cell; long-press and release for the menu; tap to open; and the to-do widget's buttons

**Screenshot test.** `ThemeScreenshotTest` uses Robolectric native graphics to render the home
screen, drawer and theme picker in every theme. The variants are minimal mode, light/dark/banded
wallpapers, themed/greyscale icons, a second home page, a 5-column grid with a folder and widget
placeholders, and the drawer's Folders tab. It fails on crashes or blank frames and writes PNGs to
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
./gradlew :app:testFullDebugUnitTest --tests '*LlamaNativeHostTest*' -Pailauncher.test.nativeDir=$PWD/build/host -Pailauncher.test.model=/path/to/qwen3-1.7b-q4_k_m.gguf
```

**Host harness.** Replay prompts (e.g. the exported fixtures) against the model:

```bash
build/host/llm_host_harness --model /path/to/model.gguf app/build/prompt-fixtures/fast_alarm.txt app/build/prompt-fixtures/fast_launch.txt
```

---

## Verification performed

1.5.0 (full version):

- `assembleFullRelease` builds a **23.8 MB APK**, version 1.5.0 (6), signed with the same key,
  16 KB aligned, with the same permissions as 1.4.0. Zero Kotlin compiler warnings; lint is clean
  on both versions except for `OldTargetApi`.
- **151 JVM unit tests pass on both versions** (1 JNI host test skipped unless configured). New: the
  /proc and /sys parsers, the System info sections, the swipe that opens the page, a Robolectric
  run of the real reader (every value must load or fall back without crashing), and 4 System info
  screenshots (43 in all).
- Not verified here: the values a real phone reports (no device on this machine).

1.4.0 (full version):

- `assembleFullRelease` builds a **23.7 MB APK**, version 1.4.0 (5), signed with the same key as
  every earlier APK (so it installs over 1.3), 16 KB aligned, with exactly the same permissions as
  1.3.0. Zero Kotlin compiler warnings; `lintFullDebug` and `lintPlayDebug` are clean except for
  `OldTargetApi`.
- **143 JVM unit tests pass on both versions** (1 JNI host test skipped unless configured),
  including the Room 2 → 3 migration, the drag-and-drop touch tests and 39 screenshots (8 new:
  the to-do widgets in four themes, smart folders in the drawer, and the folder editor).
- Not verified here: dragging and the widgets on a physical phone (no device on this machine).

All of the following was run while building 1.3.0:

- `bundlePlayRelease` (**41 MB app bundle**; Google Play delivers an APK of about 20 MB to arm64
  phones) and `assembleFullRelease` (R8 + resource shrinking, **23.6 MB APK**) succeed with **zero
  Kotlin compiler warnings**. `lintFullDebug` and `lintPlayDebug` are clean except for the
  intentional `OldTargetApi` (targetSdk 36).
- **bundletool** validates the Play bundle and builds a universal APK from it: package
  `io.github.mandarpramodphadke.aura`, version 1.3.0 (4), no storage or camera permission, native
  libraries, licences and privacy policy included. `verifyPlayReleasePlayPolicy` passes.
- **122 JVM unit tests pass on both versions** (1 JNI host test skipped unless configured),
  including the Room 1 → 2 migration test, the regression test built from verbatim real-model
  outputs, folder-mode file selection, the report-form parser, and a test that renders 31
  theme, layout and assistant screenshots (the source of the store graphics).
- **Native**: llama.cpp b12651 builds for arm64 with seven CPU variants plus `libggml-vulkan.so`
  (about 1,200 SPIR-V shaders compiled by the NDK's `glslc`), and CPU-only for x86_64. Every `.so`
  is 16 KB page-aligned.
- **The app's own prompts, replayed through the native engine and the real Qwen3-1.7B** (desktop
  build of the same `llm_engine.cpp`): correct tool choice for alarms, launches, reminders, the
  flashlight, folders, unused apps, files, to-dos, notifications and contacts. Slot errors the model
  made ("unused for a month" → 2 days, "tomorrow" → today, an invented list name) are what
  `ArgumentGrounding` fixes, and those verbatim outputs are a unit test. Prefix caching reuses the
  1,733-token system and tools block: follow-up prompt evaluation drops from ~19 s to ~1 s on a
  4-thread x86 host running at about 12 tok/s.

**Not verified here**: running on a physical device or emulator (this build machine has no
KVM/device, and no GPU). Before shipping, test on real arm64 hardware: home role, gestures,
widgets (bind and configure flows), the drawer drag, download foreground service, reminders after
reboot, the notification listener, share sheet, BiometricPrompt, Google Sign-In with your OAuth
clients, on-device tok/s on CPU, GPU offload on a few Adreno / Mali / Xclipse devices, and in the
Play version the folder picker, folder search and Report (email draft). Google Play's internal
testing track is the easiest way to do this.

---

## Known limitations

- **Wi-Fi and Bluetooth** cannot be switched programmatically on Android 10+. The tool opens the
  system panel or settings page instead. The flashlight and DND (after policy access) toggle
  directly.
- **Accessibility service & Google Play**: publishing requires the accessibility declaration in
  Play Console. The device-admin fallback may require the PIN instead of biometrics on the next
  unlock.
- **Model accuracy**: a 1.7B model makes mistakes (e.g. a category or a time). Mitigations include
  the grammar, `appCategory` and heuristics, router sanity checks, argument grounding, manual
  overrides, and confirmation before every action. The 0.6B model is noticeably weaker at tools.
- **GPU offload** depends on the phone's Vulkan driver. It is off by default; some drivers are slower
  than the CPU for small models, and crashes are handled by switching back to the CPU.
- **PDF sharing** needs Android 15+ (or SDK extension 13) for on-device text extraction; scanned PDFs
  have no text to extract.
- **Widgets** can only be bound after the system's one-time "allow AI Launcher to create widgets"
  dialog; some OEM widgets refuse third-party launchers.
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
| v2 UI | `components/LauncherTextField.kt`, `LaunchAnimations.kt`, `home/HomeContent.kt`, `HomeGrid.kt`, `HomeSheets.kt`, `HomeEffects.kt`, `folders/`, `planner/`, `drawer/PrivateSpace.kt`, `chat/ToolResultCards.kt`, `navigation/LauncherNavHost.kt` |
| v2 data & tools | `data/local/AppDatabase.kt` (`Migration1To2`), `data/home/`, `data/widgets/LauncherWidgetHost.kt`, `data/usage/`, `data/files/`, `data/notifications/`, `data/contacts/`, `data/productivity/`, `data/share/`, `data/actions/LauncherToolExecutor.kt`, `domain/ai/ToolDefinitions.kt`, `ToolRouter.kt`, `ArgumentGrounding.kt` |
| v2 models & GPU | `data/download/ModelCatalog.kt`, `ModelRepositoryImpl.kt`, `data/llm/GpuOffloadGuard.kt`, `cpp/CMakeLists.txt` (Vulkan), `llm_engine.cpp` (`EnableGpuBackend`, `n_gpu_layers`) |

## License notes

The app code is yours to license. llama.cpp is MIT; Qwen3 weights are Apache-2.0.
