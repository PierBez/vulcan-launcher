<img src="docs/icon.svg" width="96" alt="Vulcan Launcher icon">

# Vulcan Launcher

A small Android home-screen launcher written from scratch, aiming at the features people use Nova Launcher for, but without ads, trackers or analytics.

**It has no INTERNET permission, so nothing it does can leave the phone.**

## Status

Stage 1 (home screen and widgets) is done. See [ROADMAP.md](ROADMAP.md) for what exists and what's next.

- 3 home pages of 5×5 cells, swiped left and right, plus a 5-slot dock.
- App drawer with search: swipe up on home to open it.
- Long-press an app in the drawer and drag it onto a page or the dock.
- Long-press anything on home to move it, or drop it on **Remove**.
- Long-press empty space on home to add a widget or change the wallpaper.
- Swipe down on home to open the notification shade.

## Build

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

## Permissions

| Permission | Why |
|---|---|
| `EXPAND_STATUS_BAR` | Swipe down on home opens the notification shade. Normal permission, granted at install. |

Nothing else. Any new permission must be justified in this table (see [CLAUDE.md](CLAUDE.md)).

## Docs

- [ROADMAP.md](ROADMAP.md): stages and the Nova feature-parity checklist.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how the code fits together.
- [docs/nova-reference.md](docs/nova-reference.md): what we learned from analysing Nova Launcher 8.9.4. Used as a feature reference only; no Nova code is used here.
