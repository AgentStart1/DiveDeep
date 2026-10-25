# Development

## Android SDK

Install Android SDK Platform 37.1 (`platforms;android-37.1`) and accept its SDK
license before building. The Android app, shared library, and test fixture use
API 37 with minor API level 1 because the updated Compose Android dependencies
require it. Major and minor compile SDK versions are configured in
`gradle/libs.versions.toml`; the target and minimum SDK versions are independent.

The build uses AGP 9.4.1 with Gradle 9.8.2 and compile SDK 37.1. Keep these
versions compatible rather than suppressing unsupported compile SDK warnings.

## Validation

Run the Android/Kotlin baseline from the repository root:

```sh
./gradlew detekt :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidTestFixture:assembleDebug --no-daemon
```

After changing Android build tooling or settings fields, also build both release
variants and inspect individual deprecations:

```sh
./gradlew :androidApp:assembleRelease :androidApp:assembleDaily --warning-mode all --no-daemon
```

Without signing credentials these builds produce unsigned APKs. For settings UI
verification, select the HTTP backend, edit the API key, save, and reopen settings
to confirm persistence. The key must remain fully masked while typing and after
saving; switching to llmd disables the field without clearing it.

With an unlocked Android device selected through `ANDROID_SERIAL`, the focused
Compose instrumentation tests cover masked editing, saving the entered key,
disabled-field retention, and replacement when a new configuration is loaded:

```sh
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.divedeep.SettingsApiKeyTest --no-daemon
```

Acquire the shared device lock before running this command on a shared device.
These tests render an isolated settings screen and do not invoke llmd IPC.

### iOS simulator

The **iOS Test** workflow (`.github/workflows/ios-test.yml`) runs
`:shared:iosSimulatorArm64Test` on an Apple Silicon `macos-15` runner for relevant
pull requests and manual dispatches. It exercises the shared common tests on the
iOS simulator and uploads test results. It does not test the SwiftUI app.

To run the same check locally, use an Apple Silicon Mac with Xcode selected,
its license accepted, and an iOS simulator runtime installed:

```sh
./gradlew :shared:iosSimulatorArm64Test --warning-mode all --no-daemon
```

Windows and Linux cannot run this task. A disabled task on those hosts is not
evidence that iOS tests passed; check the macOS workflow result instead.

### Gradle deprecation diagnosis

Use `--warning-mode all` to see individual warnings. To include their call sites,
add `-Dorg.gradle.deprecation.trace=true` (quote this argument in PowerShell).
With the current dependencies, the remaining warnings originate inside plugins:

| Warning | Origin from the deprecation trace |
| --- | --- |
| Unsupported `HotReloadUsageType` attribute (Gradle 10) | Compose Hot Reload 1.2.0, `HotReloadUsage.kt` |
| `KotlinNativeBundleArtifactsTypes` enum attribute (Gradle 10) | Kotlin Gradle plugin 2.4.21, `KotlinNativeBundleArtifactFormat.kt` |
| `Configuration.setVisible` (Gradle 11) | AGP 9.4.1, Detekt 2.0.0-alpha.6 and KSP 2.3.12 configuration creation |

The Kotlin warning's generic suggestion to upgrade from KGP 2.0 does not apply:
this project already uses 2.4.21. These plugin calls still need compatible
upstream releases before adopting Gradle 10/11. Do not hide the warnings or
modify plugin internals in project scripts. The former Project dependency
notation warning is no longer emitted after upgrading AGP from 9.2.1 to 9.4.1.
