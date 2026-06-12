# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**白い熊 取得管理** — a personal fork of [AB Download Manager](https://github.com/amir1376/ab-download-manager),
an open-source download manager written in Kotlin / Compose Multiplatform. Upstream ships both a
desktop app and an Android app; **we build and ship only the Android APK** (the `android:app` module).

This repository (`ShiroiKuma0/shiroikuma-shutokukanri`) is a fork. We track upstream
(`amir1376/ab-download-manager`) and layer our own customizations on top of it.

## Fork Workflow — READ THIS FIRST

This is the most important section. The whole point of this repo is to maintain a small set of
customizations on top of upstream and rebuild as upstream releases new versions.

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-shutokukanri` — our fork (push here).
- `upstream` → `https://github.com/amir1376/ab-download-manager.git` — the original (read-only, for rebasing).
- **`master`** mirrors upstream's `master`. We do **not** develop on it.
- **`custom`** is our development branch. **All our work lives here.** This is the default working branch.

### Our customizations (what makes this a fork)

| What | Value | Where |
| --- | --- | --- |
| Installed app ID | `shiroikuma.shutokukanri` | `gradle.properties` → `APP_ID`, applied in `android/app/build.gradle.kts` |
| Code namespace | `com.abdownloadmanager.android` (unchanged from upstream) | `android/app/build.gradle.kts` |
| App launcher label | `白い熊 取得管理` | `app_name` / `app_short_name` in `android/app/src/main/res/values/strings.xml` |
| Main-screen header title | `白い熊 取得管理` (hardcoded, replaces `Res.string.app_title`) | `android/app/src/main/kotlin/com/abdownloadmanager/android/pages/home/HomePage.kt` |
| App icon | Traced black–yellow style: yellow-outlined glyph, black interiors, black square; source SVG `~/tmp/shutokukanri-icon.svg` | launcher: `android/app/src/main/res/drawable/ic_launcher_{foreground,background,monochrome}.xml`; in-app: `shared/resources/src/commonMain/kotlin/com/abdownloadmanager/resources/icons/AppIcon.kt` |
| Fork versioning | `VERSION_NAME` + `BUILD_NUMBER` override of upstream's git-tag versioning | `gradle.properties` + root `build.gradle.kts` |
| ABI | arm64-v8a only (`ndk.abiFilters`) | `android/app/build.gradle.kts` |
| Signing | `shiroikuma-shutokukanri.jks` via gitignored `keystore.properties` | `~/.android-keystores/shiroikuma-shutokukanri.jks`, alias `abdm` |
| JDK toolchain auto-provisioning | foojay resolver enabled (upstream has it commented out) | `settings.gradle.kts` |

The app ID is deliberately changed so this fork installs **alongside** upstream without conflict. The
namespace stays `com.abdownloadmanager.android` so `R`/`BuildConfig` and all source packages remain
unchanged — only the installed package id differs.

### Versioning & APK naming

Upstream derives its version from **git tags** (`git-version-plugin` in the root `build.gradle.kts`)
and bit-packs the semver into a versionCode: `(major shl 19) or (minor shl 9) or patch`
(see `buildSrc/src/main/kotlin/buildlogic/versioning/VersionUtil.kt`). Our `custom` branch always sits
between tags, so the root `build.gradle.kts` is patched to take the version from `gradle.properties`
instead:

- `VERSION_NAME` in `gradle.properties` **tracks the latest upstream release tag** (currently `1.9.0`).
- `BUILD_NUMBER` is **our** increment. It starts at `1` and bumps by `1` on every build with changes.
  **It must stay ≤ 99** (two decimal digits in the versionCode).
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"` (e.g. `1.9.0+1`).
- Fork `versionCode` = upstream packed code × 100 + `BUILD_NUMBER`
  (e.g. `1.9.0` packs to `528896`, so `1.9.0+1` → `52889601`).
- Output APK filename = `shiroikuma-shutokukanri_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`
  (e.g. `shiroikuma-shutokukanri_1.9.0+2_arm64-v8a.apk`).

So the first build is `+1` (`52889601`), the next build with changes is `+2` (`52889602`), and so on.
When upstream's version climbs, the packed code climbs with it, keeping upgrades monotonic.

### Building

Requires the **Android SDK** (`sdk.dir` in gitignored `local.properties` → `/home/shiroikuma/android-sdk`).
The build uses a **JDK 25 toolchain** (`jvm.toolchain=25` in `gradle.properties`), auto-downloaded by
the foojay resolver on first build. Gradle itself must be launched with JDK 21 — the default `java` on
this machine is JDK 11:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk
```

See the **build-apk** skill for the full build-and-push procedure.

`buildApk` (defined in `android/app/build.gradle.kts`):
1. builds `assembleRelease` for `android:app` (signed via `keystore.properties`),
2. copies the APK to `~/tmp/shiroikuma-shutokukanri_<version>_arm64-v8a.apk`,
3. **auto-increments `BUILD_NUMBER`** in `gradle.properties` for the next build.

### Rebasing onto a new upstream release

When the user says a new upstream version is out, follow the **upstream-new-version** skill. In short:
1. `git fetch upstream --tags`.
2. Advance `master` to the new upstream release.
3. Rebase `custom` onto `master`, preserving every customization in the table above.
4. Set `VERSION_NAME` to the new upstream tag and **reset `BUILD_NUMBER` to `1`**.
5. Build the new `+1` version with `./gradlew buildApk`; continue further changes as `+2`, `+3`, …

### HARD RULES (do not violate)

- **Never install APKs to the phone automatically.** After building, **ask** the user. Only when they
  confirm, `adb push` the APK to `/sdcard/tmp/` (the user installs it manually from there). Do **not**
  use `adb install`.
- **Never commit or push on your own.** Develop and build, let the user test, and **only commit/push
  when the user explicitly says "Push"**. Push goes to `origin` (`custom` branch).

## Build Commands

```bash
./gradlew buildApk                       # Our fork build: signed release APK → ~/tmp + bump BUILD_NUMBER (use this)
./gradlew android:app:assembleRelease    # Build the signed release APK only
./gradlew android:app:assembleDebug      # Build debug APK (app id gets .debug suffix)
```

All Gradle invocations need `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
The desktop app (`desktop:app`) builds too, but we do not ship it; don't break it gratuitously.
There are no unit tests we rely on in this repository.

## Repo layout (upstream's)

- `android/app` — the Android application (what we ship).
- `desktop/` — the Compose Desktop application.
- `shared/` — code shared between desktop and Android (UI, config, resources, updater…).
- `downloader/` — the download engine (core + monitor).
- `buildSrc/`, `compositeBuilds/` — build logic and convention plugins (`buildlogic.versioning` etc.).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with
Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This
overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
