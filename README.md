This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

### Android screen translation

Debug and Alpha can be installed beside Release, using `.debug` and `.alpha`
application ID suffixes. Their launcher icons carry **DEBUG** and **ALPHA**
ribbons. Each variant has its own settings and data; older unsuffixed Debug
installations do not automatically migrate to the new Debug package.

The home screen provides the translation switch, accessibility setup, and a
clearly labelled example of the translation display. Enable DiveDeep in Android's
accessibility settings before using screen translation; the home switch alone
does not grant accessibility access. You can also toggle translation through the
Quick Settings tile.

Open **翻译服务** to select local llmd IPC or an HTTP/HTTPS API, configure the
model and connection, then tap **保存翻译配置**. Local llmd requires caller
authorization in the selected llmd variant. API keys stay masked, and switching
backends preserves the entered connection details. Unsaved edits remain available
when moving between the home and configuration screens during the same session.

Open **应用排除** to choose apps that should not be translated. Checked apps are
excluded, and changes save automatically. **使用 Mock 翻译** is available under
the translation service's development options for testing only.

Tap a floating translation icon to open translation details. The sheet shows the
translation first, with the source below it. Long content scrolls inside the
sheet; **复制译文** copies the result and **关闭** returns to the page. The page
keeps receiving touches outside the floating controls.
If local llmd stops or becomes unavailable, the floating controls continue
following visible text and can still be disabled; new translations require the
provider to be ready again.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Static analysis: `./gradlew detekt`
- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

### Android translation integration

The app supports Release, Daily, and Debug llmd packages. Choose the installed
variant in local IPC settings and authorize DiveDeep in that llmd variant.

Contributor setup, deterministic regression tests, and real-device translation
integration tests are documented in [DEVELOPMENT.md](DEVELOPMENT.md#android-end-to-end-tests).

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
