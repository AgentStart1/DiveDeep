# Development

## Android SDK

Install Android SDK Platform 37.1 (`platforms;android-37.1`) and accept its SDK
license before building. The Android app, shared library, and test fixture use
API 37 with minor API level 1 because the updated Compose Android dependencies
require it. Major and minor compile SDK versions are configured in
`gradle/libs.versions.toml`; the target and minimum SDK versions are independent.

## Validation

Run the Android/Kotlin baseline from the repository root:

```sh
./gradlew detekt :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidTestFixture:assembleDebug --no-daemon
```
