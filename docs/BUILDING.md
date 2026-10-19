# Building Vulcan Launcher

Requirements (macOS, Homebrew):

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

`gradle.properties` sets `org.gradle.java.home` to the Homebrew JDK 17 path. Change it if your JDK lives somewhere else. Create `local.properties` (it is git-ignored):

```
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

Then:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Press Home on the phone and pick **Vulcan Launcher**.

On-device gesture check (13 gestures with real multi-touch events) and Nova-import check:

```sh
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.vulcanlauncher.test/dev.vulcanlauncher.DeviceCheck
```

## Release build

`./gradlew assembleRelease` signs the APK when a git-ignored `keystore.properties` sits in the repo root:

```
storeFile=/absolute/path/to/vulcan-release.jks
storePassword=…
keyAlias=vulcan
keyPassword=…
```

Without it the release APK is built unsigned. Publish it as `vulcan-launcher.apk` on a GitHub release so the README's "latest" download link keeps working.

## Project docs

- [ROADMAP.md](../ROADMAP.md): stages and the Nova feature-parity checklist.
- [docs/ARCHITECTURE.md](ARCHITECTURE.md): how the code fits together.
- [docs/nova-reference.md](nova-reference.md): what we learned from analysing Nova Launcher 8.9.4. Used as a feature reference only; no Nova code is used here.

The contributor rules (no network, every permission justified in the README) are in [CLAUDE.md](../CLAUDE.md).
