---
name: upstream-new-version
description: Rebase our fork onto a new upstream release of amir1376/ab-download-manager. Use when the user says a new upstream version is out, asks to update/sync to upstream, bump to the new ABDM release, or rebase custom onto the latest upstream.
---

# Rebase the fork onto a new upstream release

This codifies the "new upstream version" half of the fork workflow. The goal: move `master` to the new
upstream release, replay our `custom` customizations on top of it, and produce a fresh `+1` build.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** Same hard rules as everyday
> development (see CLAUDE.md). After the rebase + build you stop and let the user test; you only
> `git push` when they explicitly say **"Push"**.

## Background — how versioning works here

- Upstream derives its version from **git tags** (`git-version-plugin`); our root `build.gradle.kts`
  is patched to read `VERSION_NAME` / `BUILD_NUMBER` from `gradle.properties` instead, because the
  `custom` branch always sits between tags.
- `VERSION_NAME` in `gradle.properties` **tracks the latest upstream release tag** (without the `v`).
- `BUILD_NUMBER` is **our** fork increment. It **resets to `1`** on each new upstream version and
  **must stay ≤ 99**.
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER>"`.
- Fork `versionCode` = upstream packed code × 100 + `BUILD_NUMBER`, where the packed code is
  `(major<<19)|(minor<<9)|patch` (`convertToVersionCode()` in
  `buildSrc/src/main/kotlin/buildlogic/versioning/VersionUtil.kt`). E.g. `1.9.0+1` → `52889601`.

So when upstream's version climbs (e.g. `1.9.0` → `1.9.1`), the packed code climbs with it
(`528896` → `528897`), and our fork's codes for the new line (`52889701`, …) all exceed the previous
line's, keeping upgrades monotonic. (Headroom check: packed code × 100 must stay below
`2 147 483 647` — fine while upstream's major version ≤ 40.)

## Steps

1. **Fetch upstream:**
   - `git fetch upstream --tags`
   - Identify the new release tag, e.g. `git tag --sort=-creatordate | head` or check
     `upstream/master`. Upstream tags releases as `v<semver>` (e.g. `v1.9.0`); the release commit is
     usually on `master`.

2. **Advance `master` to the new upstream release** (it mirrors upstream, no fork work lives there):
   - `git checkout master`
   - `git merge --ff-only upstream/master` (or `git reset --hard <tag>` if tracking an exact tag).

3. **Rebase `custom` onto the new `master`:**
   - `git checkout custom`
   - `git rebase master`
   - Resolve conflicts so **all** our customizations survive (see the table below). The conflict-prone
     files are `gradle.properties`, the root `build.gradle.kts`, `settings.gradle.kts`,
     `android/app/build.gradle.kts`, and `android/app/src/main/res/values/strings.xml`.

4. **Update versioning in `gradle.properties`:**
   - Set `VERSION_NAME` to the **new upstream tag** (without the `v`).
   - **Reset `BUILD_NUMBER` to `1`.**

5. **Verify our customizations are intact** (after resolving the rebase):

   | What | Expected value | Where |
   | --- | --- | --- |
   | Installed app ID | `shiroikuma.abdm` (`APP_ID` in `gradle.properties`) | `android/app/build.gradle.kts` → `applicationId` |
   | Code namespace | `com.abdownloadmanager.android` (unchanged from upstream) | `android/app/build.gradle.kts` |
   | App launcher label | `白い熊 ABDM` | `app_name` / `app_short_name` in `android/app/src/main/res/values/strings.xml` |
   | Fork version override | `version = "${VERSION_NAME}+${BUILD_NUMBER}"` instead of `gitVersion` | root `build.gradle.kts` |
   | Fork versionCode | packed code × 100 + `BUILD_NUMBER` | `android/app/build.gradle.kts` |
   | ABI filter | `ndk.abiFilters += "arm64-v8a"` | `android/app/build.gradle.kts` |
   | Release signing | `signingConfigs` from gitignored `keystore.properties` | `android/app/build.gradle.kts` |
   | `buildApk` task | builds, copies to `~/tmp`, bumps `BUILD_NUMBER` | `android/app/build.gradle.kts` |
   | foojay resolver | enabled (upstream keeps it commented out) | `settings.gradle.kts` |

   Also check whether upstream bumped `jvm.toolchain` in `gradle.properties` — the foojay resolver
   will auto-provision the new JDK, but Gradle itself still launches with JDK 21.

   Sanity check: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew android:app:assembleRelease --dry-run`
   to confirm the build scripts still evaluate.

6. **Build the new `+1`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildApk < /dev/null`), then deliver it via
   **`/after-build`** (auto: `/adb-push` if a phone is connected, else `/scp` to skhw — no prompt). This
   is the first build of the new upstream line (`<newVersion>+1`).

7. **Stop.** Let the user test. Commit/push only on their explicit **"Push"** (force-push may be needed
   for `custom` since rebasing rewrites history: `git push --force-with-lease origin custom`; `master`
   is a fast-forward).

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history) over
  merging, so the customization set stays easy to audit and replay.
- If upstream restructures a file we customize, port our change to the new structure rather than forcing
  the old diff.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
