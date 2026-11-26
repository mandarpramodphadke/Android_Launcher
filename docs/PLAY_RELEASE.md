# Publishing AI Launcher on Google Play

The project builds two versions of the app from the same code:

| | `full` | `play` |
|---|---|---|
| Install | APK, directly (e.g. from GitHub) | Google Play |
| Application ID | `com.ailauncher` | `io.github.mandarpramodphadke.aura` |
| File tools | photos, videos, audio, optional all-files access | only folders the user picks (no storage permission) |
| Google sign-in and cloud backup | optional | not included |
| Camera permission (old OEM torch quirk) | declared | removed |

Everything else (the launcher, the on-device AI, the other tools) is identical. The Play build
fails if a permission that Google Play restricts for this kind of app reaches its manifest
(`verifyPlayReleasePlayPolicy`), or if a release has nowhere to send reports of AI replies.

## How new features reach the Play version

New features are built and tried in the full version first. A feature is switched on in the Play
version only after it has been tested on a phone and checked against Google Play's policies;
until then a switch in `app/build.gradle.kts` keeps it off in the Play build. Features that
Google Play doesn't allow, such as all-files access, stay in the full version.

| Since | Feature | Switch | Status |
|---|---|---|---|
| 1.4 | To-do widgets (list, Today, Add a to-do) | `TODO_WIDGETS` | Full only: waiting for a phone test |
| 1.4 | Drag and drop on the home screen | `HOME_DRAG_AND_DROP` | Full only: waiting for a phone test |
| 1.4 | Smart folders | `SMART_FOLDERS` | Full only: waiting for a phone test |
| 1.5 | System info page (after the AI chat) | `SYSTEM_INFO` | Full only: waiting for a phone test |
| 1.6 | Chat and System info as sliding panels | `CHAT_PANELS` | Full only: waiting for a phone test |
| 1.6 | Compact think button, chat background, assistant behaviour | `CHAT_CUSTOMIZATION` | Full only: waiting for a phone test |
| 1.6 | Choose device calendars, calendar links, .ics import | `MORE_CALENDARS` | Full only: waiting for a phone test |
| 1.6.1 | Answers to general questions, "which model suits my phone" (`get_device_info`) | `BETTER_ANSWERS` | Full only: waiting for a phone test |
| 1.7 | Icon packs, icon shapes, icon size, hide home labels | `ICON_CUSTOMIZATION` | Full only: waiting for a phone test |
| 1.7 | Clock styles, clock font, battery by the date | `CLOCK_STYLES` | Full only: waiting for a phone test |
| 1.7 | Blur behind drawer and sheets, wallpaper motion | `BLUR_EFFECTS` | Full only: waiting for a phone test |
| 1.7 | Music controls on the home screen | `MEDIA_CONTROLS` | Full only: waiting for a phone test |
| 1.7 | Instant search: contacts, settings, to-dos, files, sums, conversions | `INSTANT_SEARCH` | Full only: waiting for a phone test |
| 1.8 | Assistant memory | `ASSISTANT_MEMORY` | Full only: waiting for a phone test |
| 1.8 | Replies read aloud, microphone in the chat | `SPOKEN_REPLIES` | Full only: waiting for a phone test |
| 1.8 | Custom gestures (swipe down, double-tap, two-finger swipe) | `CUSTOM_GESTURES` | Full only: waiting for a phone test |
| 1.8 | Dock looks, command bar in the dock, home rows, app name size and font | `DOCK_AND_GRID` | Full only: waiting for a phone test |

Policy check for 1.8: no new permission and nothing new leaves the phone.
- *Memory* stores what the user asked to be remembered in the app's database on the phone, shown
  only to the on-device model, excluded from Android's cloud backup, and deletable in Settings.
  It is user-provided content kept on the device, so the Data safety answers don't change; the
  privacy policy now names it.
- *Spoken replies* use the phone's text-to-speech engine with an offline voice only (a network
  voice would send the reply's text out, so none is used). The engine is found with a `<queries>`
  entry for `android.intent.action.TTS_SERVICE` in `src/full/AndroidManifest.xml`; move it to
  `src/main` when the feature graduates. The microphone uses the system speech recognizer, as the
  home command bar already does (no `RECORD_AUDIO`).
- *Custom gestures*: "Quick Settings" uses the accessibility service's
  `GLOBAL_ACTION_QUICK_SETTINGS`. Before this graduates, add it to the Play Console accessibility
  declaration and to the in-app disclosure (they name lock and notifications today); the privacy
  policy already lists it.
- *Dock and grid* only changes how the home screen is drawn.

Policy check for 1.7: no new permission and nothing new leaves the phone.
- *Icon packs* are found with `<queries>` for the intents icon packs declare (package visibility by
  intent, not `QUERY_ALL_PACKAGES`), and their icons are read from the pack's resources on the
  phone. The queries live in `src/full/AndroidManifest.xml`; when the feature graduates, move them
  to `src/main/AndroidManifest.xml`.
- *Music controls* read the active media sessions, which Android lists only to an enabled
  notification listener: the notification access the app already asks for. The title, artist and
  art are shown on the home screen and not stored or sent, so the Data safety answers don't
  change.
- *Instant search* uses the existing contacts permission (only if the user granted it), the
  existing file access (picked folders in the Play version), the to-do database and a built-in
  list of system settings pages. Sums and conversions are computed on the phone.
- Clock styles, the battery line (the system's battery broadcast, no permission) and the blur
  effects need nothing.

Policy check for 1.6.1: no new permission and no new data leaving the phone. `get_device_info`
reads the same public, permission-free details as the System info page (no device identifiers)
and hands them only to the on-device model, so the Data safety answers don't change. The new
prompt lets the assistant answer general questions, so it produces a wider range of text; the
safety rule stays in the prompt, and the AI disclosure and in-app reporting of replies (required
by the AI-Generated Content policy) already cover every reply.

Policy check for 1.6: no new permission. The chat background uses Android's photo picker (no
storage permission). Assistant instructions stay on the phone and the built-in safety rule always
stays in the prompt. Calendar links make the app download a calendar from an address the user
entered; that is a user-initiated download, not collection, so the Data safety answers don't
change, and the privacy policy already describes it. Choosing device calendars uses the existing
calendar permission.

Policy check for System info (1.5): it reads device details through public APIs that need no
permission, reads no device identifiers (serial number, IMEI, Android ID, MAC address), and keeps
everything on the phone, so it needs no Data safety change. The only network-related call reads
the connection type with `ACCESS_NETWORK_STATE`, which the app already declares.

Policy check for the 1.4 features: they add no permission and send nothing off the phone. Smart
folders that read app usage use the Usage access the app already has (on-device only), so the
Data safety answers below don't change. Once tested, set each switch to `true` in the `play`
flavor and build a new bundle.

## 1. One-time setup

1. **Developer account.** Create one at play.google.com/console (one-time US$25 fee and identity
   verification). New *personal* accounts must run a closed test with at least 12 testers for 14
   days before they can publish to everyone (see step 5).
2. **Privacy policy URL.** The policy is `docs/privacy-policy.md`, and the app shows the same text
   in Settings ▸ Privacy policy. Publish it for free with GitHub Pages: repository *Settings ▸
   Pages ▸ Build and deployment ▸ Deploy from a branch*, choose the branch that has this file and
   the `/docs` folder. The URL is then
   `https://mandarpramodphadke.github.io/Android_Launcher/privacy-policy.html`.
3. **Report form (recommended).** Google Play's policy for AI apps requires a way to report
   offensive AI replies *without leaving the app*. Set up the free Google Form described in
   [report-form.md](report-form.md) and put its pre-filled link in `report.form.url`. Without it,
   reports open an email draft to `support.email`, which works but leaves the app.
4. **Upload key.** Play needs your app bundle signed with an *upload key* that you keep. Google
   re-signs the app for users with its own key (Play App Signing). Keep the keystore file and its
   passwords safe and private: you need them for every update (a lost upload key can be reset
   through Play Console support, but it takes time). Never commit them.

## 2. `local.properties`

```properties
# Signing (upload key)
release.store.file=/path/to/upload-keystore.jks
release.store.password=…
release.key.alias=…
release.key.password=…

# Google Play build (optional overrides; the defaults live in app/build.gradle.kts)
report.form.url=https://docs.google.com/forms/d/e/…/viewform?usp=pp_url&entry.…=REASON&entry.…=REPLY…
```

`app/build.gradle.kts` sets the Play application ID (`io.github.mandarpramodphadke.aura`) and the
contact address (`mandarphadke1434@gmail.com`); `play.application.id` and `support.email` override
them. The application ID can never change after the first upload.

## 3. Build

```bash
./gradlew :app:bundlePlayRelease
```

The bundle is `app/build/outputs/bundle/playRelease/app-play-release.aab`. The full version's APK
for direct installs comes from:

```bash
./gradlew :app:assembleFullRelease
```

## 4. Create the app in Play Console

*Create app*: name **AI Launcher** (or your own name), default language, **App**, **Free**. Then work
through *Dashboard ▸ Set up your app*. Suggested answers:

### App content

- **Privacy policy:** the GitHub Pages URL from step 1.2.
- **App access:** "All functionality is available without special access". There is no login.
  Add a note for reviewers: "No account needed. The optional on-device AI model downloads from
  Settings ▸ On-device AI (0.4 to 2.5 GB). Simple commands such as 'open camera' or 'timer 5 min'
  work without it."
- **Ads:** No.
- **Content rating:** answer the questionnaire honestly. The assistant generates free text
  on-device, so say yes if asked about AI-generated content; users cannot talk to each other.
- **Target audience:** 18 and over (simplest for an AI chat app; avoids the Families programme).
- **News app, COVID-19, government, financial features, health:** No.
- **Data safety:** see below.
- **Foreground service (`FOREGROUND_SERVICE_DATA_SYNC`):** task type *Network transfer*.
  Description: "Downloads the on-device AI model (0.4 to 2.5 GB) that the user chose in the app.
  The user starts the download; it shows a progress notification with a Pause button and has to
  continue while the user leaves the app. It stops when the download finishes." Attach a short
  screen recording of starting a download and the notification.
- **Accessibility API declaration** (for "AI Launcher gestures"): not an accessibility tool.
  Description: "AI Launcher is a home-screen app. Its optional accessibility service performs only
  two global actions at the user's request: lock the screen when the user double-taps empty space
  on the home screen (GLOBAL_ACTION_LOCK_SCREEN), and open the notification shade when the user
  swipes down on the home screen. It cannot read window content
  (canRetrieveWindowContent=false), does not observe input, and collects or shares no data. Users
  see an in-app disclosure and agree before the app opens Accessibility settings; the feature can
  be turned off, and a device-admin fallback exists." Attach a video: the disclosure dialog, turning
  the service on, then double-tap to lock.

### Data safety (Google Play build)

The assistant, contacts, calendar, notifications, app usage and files are processed **on the
device only**, which Play does not count as collection. The only data that leaves the device is a
report the user chooses to send:

- *Does your app collect or share any of the required user data types?* **Yes.**
- *Is all of the user data collected by your app encrypted in transit?* **Yes.**
- *Do you provide a way for users to request that their data is deleted?* **Yes** (via the
  support email).
- Data type: **App activity ▸ Other user-generated content** (the reported AI reply, the user's
  question if included, the reason and details they typed, and the app/model version).
  - Collected: **Yes**. Shared: **No** (the Google Form is your own service).
  - Processed ephemerally: **No**. Required or optional: **Optional** (only when the user taps
    Send report).
  - Purpose: **App functionality** (reviewing reports to make the assistant safer).

Not declared, because the user starts them and expects the destination: the AI model download from
Hugging Face, opening links, sharing files, calls and messages through other apps, and voice
input through the phone's speech recognizer.

## 5. Test, then publish

1. **Internal testing:** upload the `.aab` to an internal testing track and install it on your
   phone from the Play link. Check the home screen, a widget, the drawer, a model download, Report
   on a reply, Settings ▸ Folders to search, and Settings ▸ Privacy policy.
2. **Closed testing:** with a new personal account, run a closed test with at least 12 testers who
   stay opted in for 14 days.
3. **Production:** apply for production access, then roll out. Reviews usually take a few days;
   the first review of an app with an accessibility declaration can take longer.

## 6. Store listing

Text: [play-listing.md](play-listing.md). Graphics, made from the app's own screens, are in
`docs/store/`:

- `icon-512.png`: app icon (512 × 512)
- `feature-graphic.png`: feature graphic (1024 × 500)
- `phone-*.png`: phone screenshots (1080 × 1920)

They are rendered from the screenshot test's images. To regenerate them after UI changes:

```bash
./gradlew :app:testPlayDebugUnitTest --tests '*ThemeScreenshotTest*'
```

```bash
node tools/store-graphics/render.js app/build/outputs/screenshots docs/store
```

Screenshots from your own phone are a good addition, especially the planner.

## Updating later

Raise `versionCode` and `versionName` in `app/build.gradle.kts`, build with the same upload key, and
upload the new bundle. Keep the full and Play versions on the same version numbers.
