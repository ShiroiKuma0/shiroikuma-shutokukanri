<div align="center">

<img src="assets/shiroikuma/icon.svg" width="120" alt="白い熊 取得管理 icon" />

# 白い熊 取得管理

**AB Download Manager in 白い熊 black and yellow — Android only, restyled end to end.**

A fork of [AB Download Manager](https://github.com/amir1376/ab-download-manager) with **major additions**: a pure-yellow-on-black 白い熊 theme, a full appearance-tuning settings page (12 settable colors, external fonts, text-size and UI scale, list spacing), fork-styled dialogs and notifications, and a traced black–yellow icon.

Installs **side-by-side** with the official app (app id `shiroikuma.shutokukanri`).

**📥 Latest release: [`1.9.2+1`](https://github.com/ShiroiKuma0/shiroikuma-shutokukanri/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-shutokukanri/releases)

</div>

---

## 🖤💛 The 白い熊 theme

A new built-in theme (`shiroikuma`) — pure black `#000000` backgrounds and surfaces, pure yellow `#FFFF00` text, icons, accents, and borders. It is the default on fresh installs, and a one-time seed switches existing installs over. Every yellow in the app is the pure `#FFFF00`, never a washed-out material tone.

## 🎛️ 白い熊 取得管理 UI — an appearance page that tunes everything

A dedicated settings page (top of Settings, or long-press the home hamburger): theme picker, UI scale, **12 individually settable colors** layered over the active theme (picker sheet with hex entry, RGB sliders, palette, and per-color theme-default reset), **external font import** (`.ttf`/`.otf`, every font option previewed in its own glyphs), text-size scale, and download-list item spacing. All of it persists and applies app-wide, live.

## 🟨 Fork-styled chrome

Every dialog (add, edit, queues, categories, download info, finished, updater, prompts, …) and every in-app flash notification carries the fork's solid yellow border, drawn from the active theme so it follows your color overrides too.

## 🐻‍❄️ Traced black–yellow icon

The launcher and in-app icon are redrawn in the fork's traced style: a yellow-outlined download glyph with black interiors on a black square.

## 📦 Lean Android packaging

Android APK only (the desktop app is not shipped), arm64-v8a only, signed releases, and fork versioning `<upstream>+<build>` (e.g. `1.9.2+1`) that stays upgrade-monotonic across upstream releases.

---

## Built on AB Download Manager

A fork of [amir1376/ab-download-manager](https://github.com/amir1376/ab-download-manager) (app id `shiroikuma.shutokukanri`, so it coexists with the official build). All download-engine features — queues, schedulers, browser integration, multi-connection downloads — come from upstream, which does the heavy lifting. The code remains under the [Apache License 2.0](LICENSE).

## Building

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-shutokukanri.git
cd shiroikuma-shutokukanri
git checkout custom

# Signed release APK → ~/tmp/shiroikuma-shutokukanri_<version>_arm64-v8a.apk
JAVA_HOME=/path/to/jdk-21 ./gradlew buildApk
```

Requires the Android SDK (`sdk.dir` in `local.properties`) and a `keystore.properties` in the repo root for release signing; the JDK 25 toolchain is auto-provisioned by the foojay resolver.
