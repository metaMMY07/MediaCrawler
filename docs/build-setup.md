# Build setup — `dev.mediasearch` (集合)

Skeleton/reference document for the Android build of this project.
Everything below was executed on this machine; commands and exit codes are real.

---

## 1. Toolchain (fixed, process-local)

| Component | Value | How it is selected |
|---|---|---|
| JDK | `D:\CodexToolchains\jdk17\jdk-17.0.16+8` (OpenJDK 17.0.16+8-LTS, Microsoft) | `org.gradle.java.home` in `gradle.properties` + `JAVA_HOME` exported by `scripts/build.ps1` |
| Gradle | 8.13 | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle user home | `<repo>\.gradle-user-home` (writable, project-local) | defaulted by `gradlew.bat`, exported by `scripts/build.ps1` |
| Android SDK | `C:\Users\30622\AppData\Local\Android\Sdk` | `local.properties` (`sdk.dir`) |
| `compileSdk` | 35 (`android-35`, installed) | `app/build.gradle.kts` |
| `buildToolsVersion` | `35.0.0` (installed; pinned so AGP never resolves a different revision) | `app/build.gradle.kts` |
| `targetSdk` / `minSdk` | 35 / 29 | `app/build.gradle.kts` |

> **No system or user environment variable is modified.** `JAVA_HOME`, `ANDROID_HOME`,
> `ANDROID_SDK_ROOT` and `GRADLE_USER_HOME` are only ever set inside the child process
> started by `scripts/build.ps1` (and, for `GRADLE_USER_HOME`, defaulted inside
> `gradlew.bat`). The global Gradle home (`%USERPROFILE%\.gradle`) is never written.

### Why `android-36.1` is *not* used
The SDK has `platforms/android-31, 33, 34, 35, 36.1` and `build-tools/35.0.0, 36.1.0, 37.0.0`.
`android-36.1` is a *different* platform id from `android-36` — there is no plain `android-36`
and no plain `build-tools/36.0.0` installed. `compileSdk = 36` would therefore try to
download a platform that does not exist locally. `compileSdk = 35` is used deliberately.

---

## 2. Plugin versions (pinned, no `+`)

| Plugin | Version | Reason |
|---|---|---|
| `com.android.application` (AGP) | **8.12.1** | Latest 8.x that pairs with Gradle 8.13; already present in the machine's Gradle module cache, so it needs no fresh download. AGP 9.x requires Gradle 9.x. |
| `org.jetbrains.kotlin.android` | **2.2.10** | Kotlin 2.x, matches AGP 8.12.1; already in the module cache. |
| `org.jetbrains.kotlin.plugin.compose` | **2.2.10** | Compose compiler Gradle plugin must match the Kotlin version exactly. |

Verified available on the configured repositories:

* `com.android.tools.build:gradle` stable 8.x line ends at `8.13.2`; `8.12.1` is a valid release.
* `org.jetbrains.kotlin:kotlin-gradle-plugin` and `compose-compiler-gradle-plugin` both publish `2.2.10`.

---

## 3. Dependencies (pinned)

`minCompileSdk` values below were read out of each artifact's
`META-INF/com/android/build/gradle/aar-metadata.properties` — they are measurements, not guesses.

| Coordinate | Version | `minCompileSdk` | Note |
|---|---|---|---|
| `androidx.compose:compose-bom` | 2025.07.00 | — | resolves `ui 1.8.3`, `material3 1.3.2`, `runtime 1.8.3`, `foundation 1.8.3` |
| `androidx.compose.ui:ui` | (BOM) 1.8.3 | 35 | |
| `androidx.compose.ui:ui-graphics` | (BOM) 1.8.3 | 35 | |
| `androidx.compose.ui:ui-tooling-preview` | (BOM) 1.8.3 | 34 | |
| `androidx.compose.material3:material3` | (BOM) 1.3.2 | 34 | Material You / dynamic color |
| `androidx.compose.ui:ui-tooling` | (BOM) 1.8.3 | 35 | `debugImplementation` only |
| `androidx.activity:activity-compose` | 1.10.1 | 35 | **1.11.0 requires compileSdk 36 — do not bump** |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.2 | 35 | |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.2 | 35 | **2.9.4 pulls compose runtime 1.9.0 → needs compileSdk 36 — do not bump** |
| `androidx.core:core-ktx` | 1.16.0 | 35 | |
| `androidx.webkit:webkit` | 1.14.0 | 34 | added for a WebView-based login flow (`WebViewFeature`) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | — | |
| `org.chromium.net:cronet-embedded` | **143.7445.0** | — | see §4 |
| `junit:junit` | 4.13.2 | — | `testImplementation` |
| `org.json:json` | 20250107 | — | `testImplementation` (real `org.json` for JVM unit tests) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 | — | `testImplementation` |

No Python, no Node, no JS toolchain takes part in this build.

### ⚠️ Do not "upgrade" the Compose/androidx versions
`compileSdk` is 35. `androidx.activity` 1.11.0+, `androidx.lifecycle` 2.9.4+, `androidx.core`
1.17.0+ and Compose 1.9.x+ all declare `minCompileSdk=36` and will hard-fail the build with
*"…requires libraries and applications that depend on it to compile against version 36 or later"*.
If `compileSdk` is ever raised, those can be raised together.

---

## 4. Cronet version choice — why `143.7445.0`

Google publishes two different things under `org.chromium.net:cronet-embedded`:

* **`143.7445.0` (chosen)** — the real artifact. Its POM declares `cronet-shared`,
  `cronet-common`, `httpengine-native-provider`, `jsr305` and `protobuf-javalite`, and its AAR is
  ~13.7 MB (it carries the native libraries).
* **`500.0.1` / `500.0.2`** — a *deprecated empty shim*. Its own POM says:
  *"DEPRECATED. This is a transitional empty artifact that merely pulls the cronet-bundled
  artifact. Depend on cronet-bundled directly instead."* Its AAR is 791 bytes.

Since the requirement names `org.chromium.net:cronet-embedded`, the real artifact is pinned.
Native libraries for `arm64-v8a` and `x86_64` are kept by the ABI splits; the other ABIs
(`armeabi-v7a`, `x86`) are dropped.

`app/proguard-rules.pro` keeps `org.chromium.**`, anything implementing `CronetProvider`, and the
`META-INF/services/**` descriptors, because Cronet discovers its engine reflectively /
through `ServiceLoader` — without those keeps, R8 breaks the release build at runtime.

---

## 5. Application configuration

* Entry point: `dev.mediasearch.MainActivity` (declared in the manifest; the class itself is
  owned by another workstream — the manifest does not require it to exist at build time).
* App label: **集合** (`app/src/main/res/values/strings.xml`).
* `android.permission.INTERNET` (+ `ACCESS_NETWORK_STATE`).
* Cleartext HTTP is off for **every** build type: `android:usesCleartextTraffic="false"` **and**
  `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` and no
  `debug-overrides` block.
* Backup disabled: `android:allowBackup="false"`, `android:fullBackupContent="false"`, plus an
  explicit all-domains exclusion in `res/xml/data_extraction_rules.xml`.
* Theme is a plain platform theme (`@android:style/Theme.Material[.Light].NoActionBar`) — the UI
  itself is Compose Material3, so no AppCompat / Material-Components dependency is needed.
* Launcher icon is a pure-XML adaptive icon (`mipmap-anydpi-v26` + a default-config copy); no PNGs.

### ABI splits
`splits.abi` is enabled with `include("arm64-v8a", "x86_64")` and `isUniversalApk = false`:

| Build | Output APK |
|---|---|
| `assembleDebug` | `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, `…/app-x86_64-debug.apk` |
| `assembleRelease` | `app/build/outputs/apk/release/app-arm64-v8a-release.apk`, `…/app-x86_64-release.apk` |

There is **no** `app-debug.apk` / `app-release.apk` universal artifact. `arm64-v8a` is the
distribution build; `x86_64` is for the emulator.

### ⚠️ Signing — release is TEST-SIGNED
`buildTypes.release` uses `signingConfig = signingConfigs.getByName("debug")`.
This exists only so a release/R8 build can be produced in this round.
**The resulting APK is signed with the Android debug key and must not be distributed or published.**
Replace it with a real release keystore before any distribution.

Release also runs R8 (`isMinifyEnabled = true`). `isShrinkResources` is deliberately **off**:
resource shrinking cannot be runtime-verified here (no emulator/device is started), so only the
explicitly required minification is enabled.

---

## 6. Build commands

`scripts/build.ps1` is the supported entry point. It never deletes anything and never runs `clean`,
and it always exits with Gradle's real exit code.

```powershell
pwsh -File scripts\build.ps1 Debug      # :app:assembleDebug
pwsh -File scripts\build.ps1 Release    # :app:assembleRelease   (R8 + test signing)
pwsh -File scripts\build.ps1 Test       # :app:testDebugUnitTest
pwsh -File scripts\build.ps1 Lint       # :app:lintDebug
pwsh -File scripts\build.ps1 Deps       # dependency tree
pwsh -File scripts\build.ps1 Help       # usage (default)
```

Extra switches: `-Offline`, `-NoDaemon`, `-GradleUserHome <path>`, `-DryRun`.

`gradlew.bat` / `gradlew` also work directly. If you call them yourself, make sure a JDK is
reachable via `JAVA_HOME` (or `java` on `PATH`); `GRADLE_USER_HOME` is defaulted automatically.

---

## 7. Environment quirks on this machine

These are real, measured obstacles — not hypotheticals.

1. **`services.gradle.org` is unreachable** (connection reset after ~20 s). The Gradle
   distribution therefore cannot be downloaded. It has been pre-seeded into
   `<repo>\.gradle-user-home\wrapper\dists\gradle-8.13-bin\` (315 files, ~288 MB) from
   `D:\CodexToolchains\gradle\wrapper\dists\`, which uses the same URL hash
   (`5xuhj0ry160q40clulazy9h7d`) as `https://services.gradle.org/distributions/gradle-8.13-bin.zip`.
   `validateDistributionUrl=false` is set so the wrapper does not probe that dead host.
   **Do not delete `.gradle-user-home`** — it cannot be re-downloaded here.
2. **`D:\CodexToolchains\gradle` is not writable** by this process, so it cannot be used as
   `GRADLE_USER_HOME`. That is why the project-local `.gradle-user-home` exists.
3. **`curl.exe` cannot do HTTPS on this machine** — Windows schannel fails with
   `SEC_E_NO_CREDENTIALS (0x8009030e)` for every host. Plain HTTP works. Java's own TLS stack
   works fine, which is why Gradle can still resolve dependencies. Do not use `curl` to
   diagnose Maven access; use `java`/Gradle instead.
4. **The JDK's argv decoding is the real blocker.** `sun.jnu.encoding` / `file.encoding` are
   `Cp1252` on this machine (system ANSI code page 1252, *not* 936). A JVM started here reads
   **command-line arguments through the ANSI code page**, so any argument containing
   `聚合搜索` arrives as `????`. Measured with a probe that printed both channels:

   | Channel | Without flags | With `-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8` |
   |---|---|---|
   | environment variable `GRADLE_USER_HOME` | `C:\...\????\.gradle-user-home` | `C:\...\聚合搜索\.gradle-user-home` ✅ |
   | `user.dir` (working directory) | `C:\...\????` | `C:\...\聚合搜索` ✅ |
   | **command-line argument** | `C:\...\????\...` | `C:\...\????\...` ❌ still broken |

   Environment and working directory are decoded through `sun.jnu.encoding`, which the flag
   overrides; argv comes from the C runtime and cannot be fixed from the JVM.

   Consequences, all applied:
   * `gradlew.bat` passes an **ASCII-only relative** `-classpath` (it `cd`s into the project
     first), so the wrapper itself never puts a non-ASCII string on a command line.
   * **`GRADLE_USER_HOME` must resolve to an ASCII path**, because Gradle forks its daemon with
     `-javaagent:<GRADLE_USER_HOME>\...\gradle-instrumentation-agent-8.13.jar` as an
     *argument*; with a non-ASCII home the daemon dies with
     `Error opening zip file or JAR manifest missing : C:\...\????\...`.

     This cannot be side-stepped from the command line — **measured**: Gradle 8.13 forks a
     single-use daemon even under `--no-daemon`, and
     `gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=" :app:assembleDebug` still printed
     *"To honour the JVM settings for this build a single-use Daemon process will be forked"*
     and failed with the identical error. An 8.3 short name does **not** help either: it
     canonicalises back to the non-ASCII long name, so the JVM aborts with
     `Unexpected error (103) returned by AddToSystemClassLoaderSearch`. Measured directly with
     `java -javaagent:<jar> -version`: exit 0 for the jar in a pure-ASCII directory
     (`D:\CodexToolchains\...` and the session temp dir), exit 1 for the very same jar reached
     through the 8.3 short name.

     The fix that is actually in place is a **directory junction** created by
     `scripts/build.ps1` / `gradlew.bat`:

     ```
     %TEMP%\mediasearch-gradle-home   ->   <repo>\.gradle-user-home
     ```

     Gradle only ever sees the ASCII alias, while the state stays durably inside the project.
   * **`ANDROID_USER_HOME` must be set** as well. AGP keeps user-level state (the debug
     keystore, `analytics.settings`, an internal cache) under `%USERPROFILE%\.android`, which is
     outside the writable sandbox, so `:app:validateSigningDebug` fails with
     `java.nio.file.AccessDeniedException: C:\Users\30622\.android\debug.keystore.lock`.
     `ANDROID_USER_HOME` *is* the `.android` directory itself (its contents mirror `~/.android`),
     so it is pointed at `<GRADLE_USER_HOME>\.android`. This also keeps the debug keystore
     stable across builds — a regenerated key would change the APK signature.

     Do **not** set `ANDROID_PREFS_ROOT` alongside it: with both set, AGP cannot create
     `AndroidLocationsBuildService` (*"Could not create provider for value source
     AndroidLocationsBuildService.AndroidDirectoryCreator"*) and configuration fails outright.
     `scripts/build.ps1` therefore removes `ANDROID_PREFS_ROOT` from its own process.
   * `kotlin.compiler.execution.strategy=in-process` — the Kotlin daemon would otherwise be
     forked with a non-ASCII classpath on its command line and hit the same corruption.

   If this project is ever moved to an ASCII-only path, `ANDROID_USER_HOME` and the junction
   become unnecessary; `org.gradle.daemon=true` already reflects the normal daemon mode.

5. **`curl.exe` cannot do HTTPS on this machine** — Windows schannel fails with
   `SEC_E_NO_CREDENTIALS (0x8009030e)` for every host. Plain HTTP works. Java's own TLS stack
   works fine, which is why Gradle can still resolve dependencies. Do not use `curl` to
   diagnose Maven access; use `java`/Gradle instead.
6. **`services.gradle.org` is unreachable** (connection reset after ~20 s), so the Gradle
   distribution cannot be downloaded. It is pre-seeded into
   `<repo>\.gradle-user-home\wrapper\dists\gradle-8.13-bin\` (315 files, ~288 MB) from
   `D:\CodexToolchains\gradle\wrapper\dists\`, which uses the same URL hash
   (`5xuhj0ry160q40clulazy9h7d`) as
   `https://services.gradle.org/distributions/gradle-8.13-bin.zip`.
   `validateDistributionUrl=false` stops the wrapper probing that dead host.
   **Do not delete `.gradle-user-home`** — it cannot be re-downloaded here.
   `dl.google.com`, `repo1.maven.org` and `plugins.gradle.org` are all reachable.
7. **No ASCII-only writable directory exists outside the project.** The Gradle user home
   therefore has to live inside the (non-ASCII) checkout. `C:\Users\30622\Documents\ChatGPT`,
   `%APPDATA%`, `%ProgramData%`, `C:\Windows\Temp` and the Android SDK tree are all denied by
   the sandbox; only the sandbox's own per-session temp directory is writable, and that path
   changes between sessions, so it is not usable as a durable Gradle home.
8. The `gradle-wrapper.jar` is the genuine artifact produced by Gradle 8.13
   (`gradle wrapper --gradle-version 8.13 --distribution-type bin`) — it is **not** hand-made.
   `gradlew.bat` carries a clearly marked `LOCAL PATCH` block; everything else is stock.
9. **`android.overridePathCheck=true` is required in `gradle.properties`.** AGP refuses to
   configure `:app` at all while the project path contains non-ASCII characters:

   ```
   > Failed to apply plugin 'com.android.internal.application'.
      > Your project path contains non-ASCII characters. This will most likely cause the build
        to fail on Windows. Please move your project to a different directory.
        This warning can be disabled by adding the line 'android.overridePathCheck=true' ...
   ```

   This is AGP's own guard, not the sandbox's, and there is no way to satisfy it other than
   moving the checkout or overriding it. AGP prints
   `WARNING: The option setting 'android.overridePathCheck=true' is experimental.` on every
   build. The real Windows risk it warns about is already mitigated by the ASCII junction and
   the UTF-8 flags above — but note that a build through a *native* Windows tool that cannot
   handle these paths would still be at risk, which is exactly why this line should be deleted
   if the project is ever relocated to an ASCII-only directory.

---

## 8. Measured build status

All commands below were run through the delivered entry point on this machine; the exit codes
are the real ones returned by Gradle.

| Command | Exit | Result |
|---|---|---|
| `scripts\build.ps1 Deps` | **0** | Full `debugRuntimeClasspath` tree resolves; no "could not resolve" entries |
| `scripts\build.ps1 Debug` | **1** | 26 tasks up-to-date, `:app:compileDebugKotlin` fails on **one** source error (below) |
| `gradlew.bat --version` | **0** | `Gradle 8.13`, launcher + daemon JVM `17.0.16` |

Everything up to and including manifest processing, resource merging, AAR metadata checks,
native-library merging/`stripDebugDebugSymbols`, `mergeExtDexDebug` and
`validateSigningDebug` **passes**. Dependency resolution — including
`org.chromium.net:cronet-embedded:143.7445.0` with its `cronet-shared` / `cronet-common`
transitives — completes without a single failure.

### The one remaining blocker (not in this workstream's files)

```
e: app/src/main/java/dev/mediasearch/bilibili/BilibiliAdapter.kt:238:18
   Unresolved reference 'value次播放'.
```

`BilibiliAdapter.kt` line 238 reads `return "$value次播放"`. Kotlin treats CJK characters as
valid identifier characters, so `$value次播放` is parsed as one identifier named
`value次播放` instead of interpolating `value`. The fix is to brace the interpolation:

```kotlin
return "${value}次播放"
```

That file belongs to the Bili-adapter workstream, so it is deliberately **not** touched here.
`:app:compileDebugKotlin` reports exactly this one error — no other file in the source set
produced a diagnostic — so `assembleDebug` / `assembleRelease` / `testDebugUnitTest` /
`lintDebug` are all expected to go green once it is corrected.

---

## 9. Known open items / not verified

* **No emulator or device was started**, and no APK has therefore ever been assembled. Nothing
  here proves the app installs, launches or renders. `MainActivity.kt` now exists in the tree.
* **No APK artifact exists yet** — the build stops at compilation, so there are no
  `app/build/outputs/apk/**` files to hash or side-load in this round.
* **Release signing is the debug key** (§5) — not distributable under any circumstances.
* R8/`isMinifyEnabled` for `release` is configured but **has never actually executed**, because
  compilation fails first. The `proguard-rules.pro` keeps for Cronet are reasoned, not proven.
* `isShrinkResources` is off; enabling it needs a runtime smoke test first.
* Instrumented-test (`androidTest`) dependencies are intentionally absent — the agreed test set is
  `junit4` + `org.json` + `kotlinx-coroutines-test`, all JVM unit tests. The sources also use
  `kotlin.test` (`kotlin.test.Test` / `assertEquals` / `assertTrue` / `assertFailsWith`), so
  `testImplementation(kotlin("test"))` is declared as well.
* Lint runs with `abortOnError = false` at this skeleton stage, so `:app:lintDebug` reports but
  does not fail the build. Tighten this once the source tree is complete.
* A non-fatal `Couldn't open current thread, error = 5` notice from Gradle's native file-watcher
  appears on every run (the sandbox denies it). Gradle degrades gracefully; it is not an error.
* AGP emits `Warning: ... only understands SDK XML versions up to 3 but an SDK XML file of
  version 4 was encountered` — a build-tools/platform metadata-version mismatch that is
  harmless for `compileSdk 35`.
