---
name: build-apk
description: Build the signed release APK with the buildApk Gradle task, then always ask whether to scp it to skhw (first choice) or adb push it to the connected phone. Always build first without asking for permission to build — the ONLY question you ever ask is the transfer question afterward. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the release APK and optionally send to phone

> **Never ask whether to build — just build.** When this skill applies (the user
> asked to build, or you've made changes that are ready to test), run the build
> immediately. Do **not** ask "shall I build?" / "want me to run buildApk?" — that
> question is wrong. The **only** question in this whole flow is the `AskUserQuestion`
> about transferring the APK, asked **after** a successful build. So: always build,
> *then* ask about the transfer.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK
> goes to `/sdcard/tmp/<apk name>` — **never** `/sdcard/Download/` or anywhere
> else. Create `/sdcard/tmp` if needed and push there.

> **Never run `adb install` (or `pm install`).** The build step may copy the APK
> to the phone with `adb push` — and only after confirming with the user — but
> **the user installs the APK themselves** from the phone's file manager. Do not
> install it for them under any circumstances.

> **Never `git commit` or `git push` on your own.** Building does not include
> committing. After building (and the optional `adb push`), the user tests the
> build themselves. **Only when the user explicitly says "Push"** do you then
> `git commit` the changes and `git push origin custom`. The user's **"Push"**
> means *commit-and-push-to-the-fork* — it is unrelated to the `adb push` file
> copy in step 4.

> **ALWAYS end every build by asking — via `AskUserQuestion` — how to transfer
> the APK: `scp` to skhw (FIRST choice), `adb push` to `/sdcard/tmp/`, or not at
> all.** This is mandatory and applies to *every* successful build, even
> verification builds and even when the user didn't mention transferring. Do
> **not** settle for asking in prose ("say the word") or assuming the answer —
> fire the `AskUserQuestion` prompt as the final step (step 3) of the build,
> every time.

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-shutokukanri_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).
   - versionCode for that build = upstream packed code (`(major<<19)|(minor<<9)|patch`) × 100 + `BUILD_NUMBER`.

2. **Build** (Gradle must be launched with JDK 21 — the default `java` on this machine is JDK 11; the JDK 25 *toolchain* the code compiles with is auto-provisioned by the foojay resolver):
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk < /dev/null`
     (the `< /dev/null` guarantees it never blocks on stdin)
   - This runs `android:app:assembleRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> <path>` and `>>> versionCode <n>`; use those to confirm the exact filename and code, and confirm `BUILD SUCCESSFUL`.

3. **At the end of every build, ALWAYS ask** via `AskUserQuestion` how to transfer the APK to the phone — no exceptions, no assuming, no asking only in prose. Options, in this order: "Scp to skhw" (FIRST choice) / "adb push" / "No, just build". Fire this prompt as soon as the build reports `BUILD SUCCESSFUL`, regardless of whether the user mentioned transferring.

4. **Transfer per the answer:**
   - **Scp to skhw** — invoke the global **scp** skill (copies the newest APK in `~/tmp/` to `skhw:~/tmp/`). If skhw is unreachable (its tunnel is served by the phone's sshd and may be down), report that and offer the adb push instead.
   - **adb push:**
     - `adb devices` — confirm a device is connected.
     - `adb shell mkdir -p /sdcard/tmp`
     - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
     - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).
     - Never `adb install` — the user installs manually from `/sdcard/tmp/`.

## Note — transfer directly, do not rely on a task prompt

This repo's `buildApk` task (`android/app/build.gradle.kts`) has **no** interactive prompt — it only
builds, copies the APK to `~/tmp`, and bumps `BUILD_NUMBER`. Asking the user and running the `scp` /
`adb push` is Claude's job (steps 3–4), done conversationally.

## Signing

Release signing is non-interactive: `android/app/build.gradle.kts` reads credentials from the
gitignored `keystore.properties` in the repo root, pointing at
`~/.android-keystores/shiroikuma-shutokukanri.jks` (alias `abdm`). If `keystore.properties` is missing the
release APK is unsigned and will not install. (Upstream's own `SignApkTask` / `ABDM_KEYSTORE_*` CI
signing path is unused by us.)

## Note — only the Android app

We build only the `android:app` module. Never run desktop packaging tasks (`desktop:app:*`) as part of
a normal build.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
