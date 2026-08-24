# Changelog — 白い熊 取得管理

This file carries **two histories**: the fork's own releases first, then upstream
[AB Download Manager](https://github.com/amir1376/ab-download-manager)'s changelog below, unchanged.
Fork releases are named `<upstream version>+<build>`; each says which upstream release it is built on.

## 白い熊 取得管理 1.10.2+001 — 2026-08-24

Built on upstream **v1.10.2**.

### Changed

- Rebased onto upstream v1.10.2 — a clean replay, no conflicts; all 26 fork commits went across untouched.
- Fork counter reset for the new upstream line: `versionName 1.10.2+001`, `versionCode 52941001`
  (upstream packed `529410` × 100 + 1), above the previous line's highest build (`52940912`), so it
  installs straight over any earlier fork build.

### Inherited from upstream 1.10.2

- **Custom DNS / DNS-over-HTTPS**, present on Android as a new **DNS** row in Settings → network,
  directly under the proxy row: System resolver, or a validated `https://` DoH endpoint (upstream #1380).
- Native messaging manifests for additional browsers (#1366) and more browser-integration installation links.
- Refreshed translations across ~25 locales, dependency updates, and Gradle wrapper 9.6.1 → 9.7.1.
- Not shipped here: the tray-menu additions, the CLI `Win32Exception` fix and the donation-link change
  are desktop-side, and this fork ships the Android APK only.

## 白い熊 取得管理 1.10.1+011 — 2026-08-08

Built on upstream `master` at `db9ffad7`, seven commits past v1.10.1.

### Added

- **The finished-download box now floats over other apps.** Outside the app a completed download used
  to arrive as a plain white system toast — all `Toast` can render for a background app. It now gets its
  own `TYPE_APPLICATION_OVERLAY` window hosting the very notification the app draws in-UI, so the black
  surface, yellow border, colour overrides, font and text scale all come along; it sits 96 dp above the
  bottom edge and fades like a toast.
- The overlay window is `NOT_FOCUSABLE` and `NOT_TOUCHABLE`, so it cannot take focus or swallow a tap
  meant for the app underneath, and putting it up is fully guarded — the toast is decided *after* the
  attempt rather than from a permission pre-check, so a refused window still yields the old toast.
- New optional permission 「他のアプリの上に表示」 (`SYSTEM_ALERT_WINDOW`), next to the
  battery-optimization row in settings.
- **A short double buzz when a download finishes** — two 60 ms taps 90 ms apart, riding on the completion
  event so it arrives in-app and over other apps alike, sent with notification audio attributes so Do Not
  Disturb silences it like the notification sound. A failed download stays silent.
- 「完了時に振動」 switch below the sound group, **on by default** (existing installs included), and part
  of the notification category of the settings ZIP.

## 白い熊 取得管理 1.10.1+008 — 2026-08-06

Built on upstream `master` at `db9ffad7`. Functionally identical to `1.10.1+7`.

### Changed

- **Zero-padded build counter.** The root build script formats the counter `"%03d"`, so the versionName,
  the APK filename and the release tag all read `1.10.1+008`. Unpadded counters sort wrongly as text
  (`+10` before `+3`), burying the newest build in directory and release listings.
- The padding is text only: `gradle.properties` still stores the plain integer and the `versionCode` stays
  `packed code × 100 + BUILD_NUMBER`, so the counter cap remains 99 and the headroom is unchanged.
- Nothing already built or published was renamed; `1.10.1+7` and earlier tags stay as they are.

## 白い熊 取得管理 1.10.1+7 — 2026-08-06

Built on upstream `master` at `db9ffad7`, seven commits past v1.10.1.

### Added

- **`CANCEL_EXPORT`** — a running 保存復元 export can be called off. Handled *before* the reply channel is
  demanded, so a caller that sends no `reply_action` still stops its export; fire-and-forget, with an
  unknown `reply_id` a silent no-op. The export core reads an `isCancelled` flag at **every entry boundary,
  fonts included, never mid-`write()`**, and the archive is written as `<final-name>.part` and renamed only
  once whole, the part deleted in a `finally` — so a cancelled run leaves the directory exactly as it found
  it. Terminal `ERROR:cancelled` goes through the guarded reply lambda; no foreground service, no wakelock.
- **Every category states a default on/off.** `LIST_CATEGORIES` now answers the four-field line
  `id⇥label⇥parent⇥on|off` (empty parent for a top-level item), so the in-app picker and 白い熊 自由作業盤's
  picker start from the same answer; an absent `items` extra means "the on ones".

### Changed

- Rebased onto upstream `master` past v1.10.1, picking up dependency updates, the new Android multiplatform
  library plugin, the Gradle-script refactor and a Win32 startup fix. Gradle wrapper 9.5.1 → 9.6.1.
- `compileSdk`/`targetSdk` now follow upstream's version catalog (API 37, was a hardcoded 36).

## 白い熊 取得管理 1.10.1+5 — 2026-07-25

Built on upstream **v1.10.1**.

### Added

- **保存復元 automation — token-gated headless export.** Two exported broadcast actions, gated on a master
  switch and a secret token, running with no Activity and no interaction:
  `EXPORT_STATE` runs the ordinary category-ZIP export headlessly (a `path` extra overrides the configured
  directory, an `items` extra exports a subset, an unknown id aborts and writes nothing) and answers
  `OK:<absolute path>|<bytes>|<human size>|<n> categories`; `LIST_CATEGORIES` answers instantly with one
  `id⇥label` line per selectable category, sub-options carrying their parent's id.
- **Progress broadcasts with real counts, never a percentage** — `区分 3/10 — 外観（テーマ・色・書体）`, plus
  structured `current`/`total`/`unit` extras, throttled to one per 500 ms with a forced final one.
- **The reply is always a fresh broadcast** (`setPackage` + `FLAG_INCLUDE_STOPPED_PACKAGES`), never a live
  Binder — no `ResultReceiver`, no `PendingIntent`, no `Messenger`, and never dependent on the
  ordered-broadcast result, which EMUI severs between third-party apps. Exactly one terminal reply per
  request, guarded by an `AtomicBoolean`; a partial ZIP is deleted when an export fails. Distinct errors:
  `automation disabled`, `bad token`, `no-directory`, `no-storage-access`.
- **Automation switch + token rows** inside the existing エクスポート / インポート section: 「自動エクスポート」
  master switch (**default off**), and 「自動化トークン」 showing the token abbreviated, tap-to-copy with a
  confirmation, 「再生成」 behind a confirm dialog. The token is 24 `SecureRandom` bytes, hex-encoded,
  generated lazily, compared constant-time (`MessageDigest.isEqual`), and stored in its own SharedPreferences
  file — so it can never travel inside an export ZIP.
- **Font files became their own selectable part** — `appearance.fonts`, a sub-option of `appearance`,
  exportable and importable on its own and shown indented under 外観 in the checklist.

### Changed

- **Family backup-name convention** — every backup, from the panel and the automation path alike, is now
  `shiroikuma-shutokukanri_<yyyy-MM-dd_HH-mm-ss>.zip`: no version, no `-export` infix, so sister apps'
  backups sort uniformly in one directory. The app version moved into `manifest.json` as `appVersion`, and
  the old names stay recognised when the page looks for the latest export.
- **The export core is one headless-callable function** — `export(categories, OutputStream, onProgress)` —
  with the panel and the automation receiver as two thin callers; no export logic is duplicated. It reads
  the download categories straight from storage when the download system has not booted, so an automated
  backup can no longer capture an empty category list.

## 白い熊 取得管理 1.10.1+4 — 2026-07-25

Built on upstream **v1.10.1** (new speed limiter, Ktor-based integration server, per-download API keys).

### Added

- **Category-based settings export/import** — the first section of the UI page: a settable export directory
  queried on page open for the latest export; export as **a ZIP of plain JSON, one file per category**, plus
  a manifest and the imported font files as real files under `fonts/`; **nine selectable categories** covering
  every settable item in the app (appearance, general, download settings, notifications, system/API, proxy,
  per-host settings, download categories, browser bookmarks); **merge-not-wipe import** tolerating missing and
  extra keys, upserting per-host rows and bookmarks, keeping this install's category item lists and never
  touching device-local state, where one failing category never fails the whole import; black yellow-bordered
  success dialogs where export **OK** and import **後で** close the whole chain and **今すぐ再起動** relaunches
  the app; and an Arcanechat-style pill button row.

### Changed

- **kxkb-format UI page** — headings underlined exactly as wide as their text (20 sp bold sections, 18 sp
  sub-headings), a 1 px full-width hairline between top-level sections, and a 36 dp + 18 dp-per-tier indent
  ladder.
- Rebased onto upstream v1.10.1.

### Fixed

- **Directory picker**: a null initial directory was stringified into the literal path `"null"` and browsed;
  it now stays absent and the picker falls back to the public Downloads directory (a latent upstream bug,
  exposed by the export-directory picker).

## 白い熊 取得管理 1.9.2+2 — 2026-07-16

Built on upstream **v1.9.2**.

### Changed

- **Themed flash notifications on every screen** — the "Finished" flash after a completed download, and every
  other in-app flash (cancelled, errors, info, loading), now renders as the fork-styled card (black surface,
  solid yellow border) on **all** activities: the in-app browser, the download-completion dialog, the
  add-download pages — not just the main screen. Previously every screen other than the main one fell back to
  Android's unstylable white system toast. That fallback now fires only when no app UI is visible at all,
  tracked by a visible-screen counter so overlapping activity transitions cannot re-enable it prematurely.
  Flash cards draw above the dialog layer, matching the old toast's stacking.

## 白い熊 取得管理 1.9.2+1 — 2026-07-02

First published release of the fork, built on upstream **v1.9.2**. Everything below is what the fork adds
on top of stock.

### Added

- **白い熊 theme** (id `shiroikuma`) — pure black `#000000` backgrounds and surfaces, pure yellow `#FFFF00`
  text, icons, accents and borders. **Default theme for fresh installs**; a one-time seed switches existing
  installs over on first boot after upgrade. Coexists with all upstream themes.
- **白い熊 取得管理 UI settings page** — a dedicated appearance page (sectioned layout with cascading indents),
  reachable from the top of Settings or by long-pressing the home-screen hamburger: theme picker; UI scale;
  **12 individually settable colors** layered over the active theme, each with a picker sheet offering hex
  entry, RGB sliders, a palette and a reset-to-theme-default; **external font import** (`.ttf`/`.otf`) with
  every font option rendered in its own glyphs; text-size scale; and a **download-list item spacing** slider
  (0–32 dp). All overrides persist in `shiroikumaUi.json` and apply app-wide, live.
- New configurable types and renderers powering the page: `ColorConfigurable` (picker sheet),
  `FontConfigurable` (in-glyph previews + add-font), `SliderConfigurable` (inline slider).
- **Yellow border on every dialog** — the shared SheetUI container draws the fork's border (theme
  `onSurface`, i.e. pure yellow under the 白い熊 theme), covering the add/edit download dialogs, queues,
  categories, batch, download-info, finished, updater, browser prompts and every other sheet.
- **Fork-styled flash notifications** — in-app flashes draw a solid full-opacity yellow border instead of
  upstream's near-invisible 10 %-alpha one; still theme-driven, so it follows colour overrides.
- **Traced black–yellow icon** for launcher and in-app use: yellow-outlined download glyph, black interiors,
  black square background; glyph scaled to 65 % in the launcher.

### Changed

- **App id `shiroikuma.shutokukanri`** — installs side-by-side with the official app; the code namespace
  stays `com.abdownloadmanager.android`, so only the installed package id differs.
- **Launcher and browser labels** renamed to 白い熊 取得管理, and the main-screen header title hardcoded to it.
- **Android only** — the desktop app is not shipped; **arm64-v8a only** (`ndk.abiFilters`), keeping the APK lean.
- **Fork versioning** — `versionName = <upstream version>+<build>`, `versionCode = upstream packed semver × 100
  + build`, monotonic across upstream upgrades.
- **Signed releases** via a local keystore (`keystore.properties`), non-interactive, and the **foojay toolchain
  resolver enabled** so the JDK build toolchain auto-provisions.

### Fixed

- **New-issue form** fixed and de-branded to point at this fork instead of upstream.

---

# Upstream changelog — AB Download Manager

Everything below is upstream's own changelog, unchanged.

# Changelog

## Unreleased

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## 1.10.2

### Added

- Added built-in DNS-over-HTTPS (DoH) support (#1380)
- Added native messaging manifests for additional browsers (#1366)
- Added more browser integration installation links
- Added "Import From Clipboard" to the system tray menu

### Fixed

- Fixed a `Win32Exception` that could occur when uninstalling native messaging from the CLI (#1345)

### Updated

- Updated translations
- Updated the donation link

## 1.10.1

### Changed

- Disabled the AOT Cache

## 1.10.0

### Added

- Native Messaging support, allowing the browser to launch the app if it's not already running
- Command-line interface (CLI)
- Support for API key authentication (#1335)

### Improved

- Faster app startup using the AOT cache (#1333)
- Updated translations
- Improved the speed limiter behavior (#1315)

## 1.9.2

### Added

- New Twilight theme (#1292)
- Optional download completion notifications on Android (#1290)

### Fixed

- Fixed a crash on some older CPUs on Windows
- Fixed oversized system tray icon on macOS

### Improved

- Updated translations
- Prevented Android devices from sleeping while downloads are active (#1291)
- Various UI and UX improvements

## 1.9.1

### Added

- An option to customize notification sounds (#1259)

### Fixed

- Ongoing notification was laggy on Samsung One UI devices (#1269)

### Improved

- Updated Translations
- Minor UI/UX improvements

## 1.9.0

### Added

- Czech language support
- User-friendly error messages for download errors (#1252)
- An option to remember the last selected queue and quickly add downloads to it by long-clicking the `Add` button (
  #1246)
- An option to import/export downloads using JSON format
- A `Download` button on the multi-download page for cases where users do not want to start downloads without queue
  processing (#1247)
- The app now includes a logger that can be enabled using a command-line flag (#1226)
- Startup errors are now logged automatically to help diagnose initialization issues

### Changed

- The default unqueued "Max Concurrent Downloads" value has been changed from "Unlimited" to 3 (This can be customized
  in the app settings)

### Improved

- Updated translations
- Added an indicator on the Android main page when resume is not supported (#1248)
- Extract the file name from the download link as a fallback when no response information is available (#1209)
- Minor UI/UX improvements

## 1.8.8

### Added

- Reordering categories (#1119)

### Changed

- Quit Shortcut changed from `Ctrl-W` to `Ctrl-Q`

### Fixed

- Show part info not showing automatically if it was previously shown (#1183)
- "Edit Saved Checksum" now correctly updated on the Checksum page on Android
- the Main window correctly remembers its maximized state (#1185)
- System tray now uses the native UI on Linux (arm64)
- Other UI/UX improvements

### Improved

- Updated translations
- Minor UI improvements

## 1.8.7

### Added

- Ability to change the storage root when selecting the download location on Android (e.g., save to SD card) (#1101)
- Option to choose the render API on Desktop (#1103)

### Changed

- Default render API set to `SOFTWARE` on Linux

### Improved

- Updated translations
- Increment/decrement buttons in number fields are now more accessible (#1104)
- Minor UI improvements

## 1.8.6

### Added

- Linux ARM support (#1081)
- An option to set max concurrent downloads for manually resumed downloads (#1085)

### Fixed

- Do not reset the download if storage is not mounted yet (#1087)
- Error when assembling HLS media if destination folder was not created yet (#1089)
- Do not reset the download if server changes status code from 206 to 200 (#1088)

### Improved

- Updated translations
- Queue logic improvements (#1086)
- Linux Installation Script updated to support ARM devices (#1090)

## 1.8.5

### Added

- Windows ARM support (#1055)
- In-app browser bookmark feature on Android (#1072)
- In-app browser can be added to the launcher (App Menu)
- In-app browser can be used as the default browser

### Fixed

- System tray crash on some Linux environments (#1060)
- Black screen issue on some Linux environments (#1066)
- Notification sound playing even when notification sounds are muted on Android (#1064)

### Improved

- Updated translations
- In-app browser UI/UX improvements on Android
- System tray now uses native UI on Windows (#1060)
- Use a default User-Agent when no value is provided by the user (#1071)
- Small UI improvements

## 1.8.4

### Added

- In-app browser for Android
- The Android service now tells the user why it is running
- The Add-Multi-Download page can now filter downloads using search and wildcards

### Fixed

- Random app crashes on some Android devices caused by service-related issues
- Issues with the in-app update feature on some Android devices

### Improved

- Updated translations
- The Android Foreground Service is now only used when necessary (active downloads, active queues, scheduled queues) and
  automatically stops after inactivity
- Add-Multi-Download page UI/UX improvements

## 1.8.3

### Added

- Ability to sort and remove queue items on Android (#996)
- A new shortcut to open the download list from the download progress dialog (#1001)

### Fixed

- Download table state not saved properly on desktop (#999)
- Update related notifications appearing repeatedly on android (#998)

### Improved

- Updated translations
- Settings Page UI improvements on android (#990)

## 1.8.2

### Fixed

- Resolved issues with the In-App update feature on some android devices
- Disabled notification badges on the launcher icon on android
- The application crashes on some devices (desktops) because of an issue in system theme detection logic

### Improved

- Updated translations
- Added tooltips for action buttons
- Display selected count in the "Add Multi Download" page on desktop (#970)
- Reduced battery consumption
- Various UI/UX enhancements

## 1.8.1

### Fixed

- Android 10 storage access issue that caused download errors (#977)

### Improved

- Updated translations
- Better support for adaptive icons on Android (#978)
- Improved directory picker on Android (#979)
- Slightly reduced application size

## 1.8.0

### Added

- Android Support
- macOS users can now use homebrew to install/update the application

### Fixed

- Some HLS streams are not recognized properly

### Improved

- Updated translations
- UI improvements

## 1.7.1

### Added

- Support for custom data directory (#895)

### Fixed

- System shortcuts not working on the main page (#885)
- Segment info display issues
- Suggested file names from browsers are now automatically corrected before use (#896)

### Improved

- Translations updated
- Download list UI improvements (#897)
- Extra icon sizes added to the Windows `.ico` file

## 1.7.0

### Added

- Support for downloading media files: audio, video, non encrypted HLS streams from the browser (browser extension needs
  to be updated)
- Option to customize each item individually in the “Add Multiple Downloads” page (by right-clicking on each item) (
  #866)
- “Download Page” and “Custom User-Agent” options are now available in the “Add Download” > "Configs" dialog
- Ability to remove recently used save locations (#873)

### Changed

- Browser integration API updated; updating the browser extension is required to support new features

### Improved

- Updated translations
- The download creation time is now set to when the “Add Download” dialog is opened (#846)

## 1.6.14

### Fixed

- An issue causing slow download speeds on some websites

### Improved

- Updated translations
- Download Engine improvements
- Minor UI improvements

## 1.6.13

### Fixed

- **Access Denied** error could sometimes happen when adding a list of downloads (#826)

### Improved

- Updated translations
- Download engine improvements (#828)
- **Customize Table Columns** popup now supports drag to reorder (#830)

## 1.6.12

### Added

- **Per Host Settings** — save username, password, thread count, user-agent, and more for specific hosts (#820)
- Support for using **Move** action by holding **Shift** during drag & drop (#821)

### Changed

- UI scale is now relative to the system scale instead of using a fixed value (#814)

### Fixed

- Encoding issue with the default download folder on Linux (#810)

### Improved

- Updated translations
- Enhanced multi-display support (#814)
- Main window now remembers its maximized state (#815)

## 1.6.11

### Added

- Option to change the download size unit (#804)

### Fixed

- "Permission denied" error when starting a new download (#795)

### Improved

- Updated translations
- Improved settings page (#805)
- Automatically fix illegal characters in server-provided filenames (#781)
- Better handling of filenames received from the server (#780)
- Use the OS default download location on first launch (#789)

## 1.6.10

### Added

- New Black theme (#767)

### Fixed

- Restored missing executable permissions for files inside archives (macOS & Linux) (#765)
- Eliminated flickering on the "New Update" page (#770)

### Improved

- Updated translations
- Hovering between menus now works without closing the open one (#766)
- Better item selection and new keyboard shortcuts on the queue items page (#769)
- Small UI improvements

## 1.6.9

### Fixed

- "Keep System Awake" was not properly cancelled on Windows (#755)
- "Create Desktop Entry" had issue if the path contains spaces on Linux (#733)
- Application crash on systems that have invalid font names (#737)
- Some settings statuses were not updating correctly (#732)
- "Download Dialog" position shifted when multiple dialogs were open simultaneously (#758)
- "Add Download Dialog" position shifted when multiple dialogs were open simultaneously (#761)

### Improved

- Translations updated
- Better handling of filenames received from the server (#759)

## 1.6.8

### Fixed

- Can't change Auto Shutdown option if it was enabled

## 1.6.7

### Added

- Lithuanian language support
- Kurdish (Sorani) language support
- Option to automatically shut down the system when downloads or queues complete (#726)
- Option to delete partial files on download cancellation (#724)

### Changed

- App icon is now hidden from the Dock at runtime on macOS when the system tray is used (#710)
- Default max download retry count changed to 3

### Fixed

- Removable storages are no longer monitored on Windows (#705)

### Improved

- Translations updated
- System stays awake while downloads are in progress (#725)
- Confirm dialogs and buttons now have better focus behavior
- Dropdowns are now searchable (#706)
- IO Operations improvements

## 1.6.6

### Added

- Option to create desktop entry on Linux (#698)

### Changed

- Renamed Linux desktop and autostart files for better compatibility (#699)

### Fixed

- Removed duplicate UI Scale option in the Settings (#697)

### Improved

- Updated translations
- Pressing "Stop All" now closes all active download windows (#700)

## 1.6.5

### Added

- New themes (Deep Ocean, new Dark, new Light)
- Category accepted file types are now optional (#690)
- Option to change the app font (#692)
- Option to switch between relative and absolute date/time formats (#694)
- Option to clear all items in the queue at once

### Changed

- Renamed the previous Dark theme to **Obsidian**
- Renamed the previous Light theme to **Light Gray**

### Fixed

- Issue where the app wouldn’t start for some Windows users (#695)

### Improved

- Updated translations
- General UI Improvements
- Automatically scroll to new downloads on the main page (#672)
- Improved path validation for new downloads (#693)

## 1.6.4

### Added

- Queues are now visible on the home page, next to the categories (#661)
- In-app update is now supported on macOS (#627)
- New option to enable the native menu bar on macOS (#646)

### Fixed

- macOS: Window now activates properly when "Show Downloads" is clicked from the system tray (#632)
- Linux: Startup desktop entry now includes an icon (#634)
- An issue where the "Edit Download" page could unintentionally change the download status (#641)
- Queue status not updated properly sometimes (#663)

### Improved

- Translations updated
- Minor UI improvements

## 1.6.3

### Added

- Korean Language
- An option to append ".part" extension to incomplete downloads (disabled by default)

### Fixed

- Prevent freeze when opening a file or folder
- Some websites close the connection if we ask for resume support
- Some non-standard links not captured correctly
- Crash when opening browser integration links on macOS
- Multiselect with Meta key not working as expected on macOS
- Multiselect not stopped properly after window focus lost

### Improved

- Translations updated
- Minor UI/UX improvements

## 1.6.2

### Added

- Thai Language

### Fixed

- System Tray crashes sometimes in Linux
- Icons not rendered properly sometimes in Linux
- System Tray icon color in macOS
- Quit handler in macOS

### Improved

- Translations updated
- Respect user defined position of system buttons in Linux

## 1.6.1

### Fixed

- Application shortcut in Windows have no icon

### Improved

- Translations updated

## 1.6.0

### Added

- macOS support
- Polish Language
- Hungarian Language
- Luri Bakhtiari Language
- Silent Download option in the Browser Integration
- Donate button in the app to support the project

### Fixed

- Overriding an existing download sometimes didn't work as expected.
- "Start Queue" checkbox sometimes did not work as expected.

### Improved

- Translations updated
- Custom Window decorations
- Window dragging on Linux is now handled by the OS
- Each platform now uses its own system button style
- JVM updated to version 21

## 1.5.8

### Added

- An option to allow update existing download from "Add Download" page if a duplicate is detected

### Fixed

- Crash when opening "Items" section of "Queues" page

### Improved

- Translations updated
- Duplicate download detection
- Minor UI/UX improvements

## 1.5.7

### Added

- Drag and Drop files to other categories or external applications

### Fixed

- "Parts Info" section in the "Download Progress" window does not expand for the first time

### Improved

- Translations Updated
- Improved UI rendering on Windows, resulting in higher FPS.
- Minor UI/UX Improvements

## 1.5.6

### Added

- Finnish Language Support
- An option to make the start time of queues optional
- An ability to edit saved checksums on the "File Checksum Checker" page'

### Changed

- The "Close" button in the "Download Progress" window has been renamed to "Cancel" (this stops the download and closes
  the window). To close the window without stopping the download, use the "X" button.

### Fixed

- An issue where filenames in email attachments were not captured correctly
- The updater wouldn't resume after the download was stopped
- "Open Folder" doesn’t work properly on Linux when the file name contains special characters.
- Changing settings in the 'Download Progress' window also affects other download items!

### Improved

- Translations updated
- "Download Progress" and "Queues" windows UI improvements
- Pressing "Download Browser Integration" the download page will be opened in the corresponding browser

## 1.5.5

### Added

- Japanese Language
- An option to automatically "Retry Failed Downloads" (Disabled by default for now)
- An option to Import/Export download credentials as curl command

### Fixed

- The download progress sometimes shows incorrect speeds and ETAs
- Some unverified hostnames can't be used when the "Ignore SSL Certificate" is enabled
- Startup on Boot issue in macOS
- Drag And Drop of links issue in macOS
- Some shortcuts didn't work properly in macOS
- System Tray didn't work in macOS

### Improved

- Translations updated
- Minor UI improvements
- App Icon size in macOS
- Override "About" dialog in macOS

## 1.5.4

### Added

- The app now supports full portability by creating an empty `.abdm` directory in the installation folder.
- An option to delete user data (configuration files) when using Windows Uninstaller.

### Fixed

- Unchecked "Use Category" didn't work as expected.

### Improved

- Download engine improvements.
- Translation updated.

## 1.5.3

### Added

- Vietnamese language.
- An option to "Select Queue" dialog to start the queue immediately.
- An option to allow user set custom User-agent in the settings.
- An option to not "Use Category" by default.
- An option to disable SSL Certificate Verification.
- An option to show/hide icon labels in the main toolbar (you can hover over them to see their labels).
- An option to not use System Tray.

### Fixed

- Sometimes Thread count not applied correctly.
- The download completion dialog appeared even when its option is disabled.
- Some servers return 256 Bytes instead of full size

### Improved

- Translations updated
- Minor UI improvements
- Use system language as default language
- Proxy Settings page improved

## 1.5.2

### Added

- An ability to validate downloads with File Checksum
- System Proxy support
- Proxy Auto Configuration (pac) support

### Changed

- Maximum allowed thread count has been increased

### Fixed

- Fixed the incorrect System Tray name on Linux.

### Improved

- Translations Updated
- Settings window size will be remembered now

## 1.5.1

### Added

- Italian Language
- German Language
- Georgian Language
- Indonesian Language
- An option to change download speed unit
- An ability to start new download using Rest-Api

### Fixed

- System tray in Linux now has correct icon and native option menu
- App crashes when changing theme in Linux
- Open file/folder action fails sometimes in Windows

### Improved

- Translations updated
- Split category and location configuration options in multi download page
- Home page minor UI improvements

## 1.5.0

### Added

- In App Update feature.
- An option to track deleted files on disk and remove them from the download list (either manually or automatically).
- Delete option to the Main toolbar.

### Changed

- UI Scale maximum value increased to 3x.

### Fixed

- When you change "Default download Folder", the "Download Location" of categories also updated (if they are inside "
  Default Download Location").
- Issue on the "Add Multiple Download" page where the "Save Mode" set to "All in Same Location" was not functioning as
  expected.
- App does not start on boot when installation path contains space.

### Improved

- Redesigned "About" Page.
- Translations updated.
- "Extra Config" section on the "Add Download" page was not displaying correctly in some languages.

## 1.4.4

### Added

- UI Scale option

### Changed

- The "Selection" cell in download list table can be hidden (optional)

### Fixed

- Improved "Open Folder" in Windows
- Support third party file managers in Windows
- "Download Progress" Window shows up even if the "Auto Show Progress Window" option is disabled
- Improved "Confirm Delete Download" UX
- Improved the readability of shortcut text in menus
- Improved sort of download list by status
- Resize handle moves in opposite direction for RTL languages
- Improved "Home" page
- Improved "About" page
- Updated translations

## 1.4.3

### Added

- "Download Completion" window
- "Exit Confirmation" dialog when there is active download
- An Option to automatically show "Download Completion" window (Optional)
- An Option to automatically show "Download Progress" window when user presses on "Resume" (Optional)

### Changed

- Default thread count is now 8
- Shape of filename of release binaries changed (added arch name after platform name)

### Fixed

- "Delete entire list" task does not remove all downloads
- Filename not detected correctly from some download servers
- Rename download changes state to paused if it was finished
- Improved installation script for linux
- Improved "Settings" page
- Translations updated

## 1.4.2

### Added

- Edit Download Page (Rename, Refresh links/credentials etc…)
- Translators Credit Page
- Traditional Chinese Language
- Spanish Language
- French Language
- Turkish Language

### Changed

- Updated translations

## 1.4.1

### Added

- Portuguese (Brazilian) Language

### Changed

- Updated some languages

### Fixed

- Language names not shown by their native names
- Selected language not saved properly
- Wrong text for "Close" button in "Batch Download" page

## 1.4.0

### Added

- Localization Support
- Persian Language
- Arabic Language
- Chinese (Simplified) Language
- Ukrainian Language
- Russian Language
- Albanian Language
- Bengali Language

### Changed

- Category Download Location is now optional

### Fixed

- A bug in Download Engine
- "Add Queue" page will be shown properly when opened from "Import List" page

## 1.3.0

### Added

- Proxy Support
- Categories now can have URL patterns

### Fixed

- Application freezes a while when we drag(and drop) a large file on it
- Improved category section in the home screen

## 1.2.0

- in this version we replaced Wix installer with Nsis for better customization and more control over the installation
  process in Windows.
- if you use Windows in order to install this version please first uninstall the previous msi version (your settings and
  downloads will be safe)

### Added

- You can now create and customize categories
- Pause/Resume in header actions

### Changed

- Change installer in Windows from Wix to Nsis
- Improved import link page

## 1.1.0

### Added

- Added Batch Download
- Added an option to merge TitleBar with MenuBar (disabled by default)
- Added two cli options --version, --exit

### Fixed

- Fixed Opening downloaded file creates a subprocess in Windows
- Fixed that some non-standard links not imported correctly
- Improved window custom decoration logic and title bar position
- Improved settings page

## 1.0.10

### Fixed

- Improve home page
- Improve download page
- Opening Directory Picker cause the app to crash in Linux
- Folder opened two times when clicking on open folder in Linux

## 1.0.9

### Added

- Sparse File Allocation

### Fixed

- Improve directory picker
- Improve show help UX in settings
- Improve Download Engine

## 1.0.8

### Added

- Use server Last-Modified time option in settings
- Show Open File button if new download already exists and completed

### Fixed

- Improved custom window decoration in linux
- When we click on open folder in linux it opens the file instead!
- Some URLEncoded filenames are not decoded properly

## 1.0.7

### Added

- support follow system Dark/Light mode
- auto paste link (if any) from clipboard when opening add url page

### Fixed

- App is now open in center of screen
- Some settings doesn't persist after app restart
- Download speed shows a high value incorrectly when we reopen the app window after a while
- Some files not downloaded correctly now fixed

## 1.0.6

### Added

- Add Community and Browser Integration links to the app menu

### Changed

- Change default download folder

### Fixed

- Exception will not throw anymore if System Tray is not supported by the OS

## 1.0.5

- Improve Download Engine

## 1.0.4

- Improved UI/UX in Download Page

## 1.0.3

- Download Info Page now show users that a download file supports resuming or not

### Fixed

- Download links that does not support resume now handled correctly
- Some Web pages does not download correctly

## 1.0.2

- Error messages improvements

### Fixed

- handle some webservers does not respect requested range at first place

## 1.0.1

- UI improvements

### Changed

- repository url updated

## 1.0.0

- This is the first release of the app

### Added

- Multi Connection File Download
- Speed limiter
- Download Queues
- Download Scheduler
- DownloadManager Browser Integration Support
- Dark/Light themes
