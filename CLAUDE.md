# AI Launcher: working notes for Claude

Android home-screen launcher with an on-device Qwen3 assistant (Kotlin, Jetpack Compose, Hilt,
Room, llama.cpp through JNI). One code base, two product flavors:

- `full`: installed directly from an APK, application ID `com.ailauncher`. The owner's main app.
- `play`: the Google Play version, application ID `io.github.mandarpramodphadke.aura` (permanent).

README.md describes the app; docs/PLAY_RELEASE.md describes the Play version and its policy work.

## Workflow agreed with the owner: the full app first

- Build every new feature or change in the `full` app first, and give the owner a signed full
  release APK to try on their phone.
- A feature reaches the `play` app only after both:
  1. the owner confirms it works on their phone, and
  2. it has been checked against Google Play policy: permissions, the Data safety form, user data
     leaving the device, AI-generated content rules.
- If Google Play doesn't allow a feature, it stays full-only. Tell the owner why, and what change
  would make it allowed.
- Gate a new feature with a BuildConfig flag declared in both flavors in `app/build.gradle.kts`:
  `true` in `full`, `false` in `play`, like `BROAD_FILE_ACCESS` and `CLOUD_FEATURES`. When it
  graduates, set it to `true` for `play` (or remove the flag).
- A new permission goes in `src/main/AndroidManifest.xml` only if Play allows it for this app.
  Otherwise, also remove it in `src/play/AndroidManifest.xml`.
- `verifyPlayReleasePlayPolicy` must keep passing. Never weaken it to get a build through.
- Build the Play bundle (`bundlePlayRelease`) only when the owner asks, or when a feature graduates.

## Signing

- Release builds are signed with the owner's key, `ai-launcher-upload-key.jks`. It signed every APK
  so far and is also the Play upload key. It is not in the repository and must never be committed.
- Ask the owner to upload the key and its password note before building a release APK. An APK
  signed with any other key will not install over their app.
- Pass the key through git-ignored `local.properties`: `release.store.file`,
  `release.store.password`, `release.key.alias` and `release.key.password`.

## Rules to keep

- `domain/` has no `android.*` imports. Android APIs live in `data/`, Compose in `presentation/`.
- Inference is 100% on-device. User data never goes over the network; the only exceptions are the
  model download and an AI-reply report that the user chooses to send.
- Keep `SystemPrompts` free of timestamps (the prefix KV cache depends on it) and keep existing tool
  names and arguments.
- Keep lint clean apart from `OldTargetApi`, and keep zero Kotlin compiler warnings.

## Build and check

```bash
./gradlew :app:testFullDebugUnitTest :app:testPlayDebugUnitTest
```

```bash
./gradlew :app:lintFullDebug :app:lintPlayDebug
```

```bash
./gradlew :app:assembleFullRelease
```

```bash
./gradlew :app:bundlePlayRelease
```

- The native build (llama.cpp and the arm64 Vulkan backend) needs the NDK. It fetches the pinned
  Vulkan-Headers and SPIRV-Headers at configure time, unless `vulkan.headers.dir` and
  `spirv.headers.dir` point to local checkouts.
- After UI changes, re-run `ThemeScreenshotTest` and look at `app/build/outputs/screenshots/`.

## Open items

- 1.4 features are full-only until the owner has tried them on their phone: `TODO_WIDGETS`,
  `HOME_DRAG_AND_DROP`, `SMART_FOLDERS` (table in docs/PLAY_RELEASE.md). Their policy check passed:
  no new permission, nothing leaves the device.
- Before the first Play submission, set `report.form.url` (Google Form, see docs/report-form.md).
  Until then, AI-reply reports open an email draft, which leaves the app.
