<img src="docs/icon.svg" width="96" alt="Vulcan Launcher icon">

# Vulcan Launcher

A small Android home-screen launcher written from scratch, aiming at the features people use Nova Launcher for, but without ads, trackers or analytics.

**It has no INTERNET permission, so nothing it does can leave the phone.**

## Status

All four stages are built: home screen and widgets, looks, gestures and actions, drawer and folders. [ROADMAP.md](ROADMAP.md) has the full Nova feature checklist with each feature's status.

- **Home:** up to 12×12 grid with subgrid positioning, 1–9 pages, 13 transition effects, infinite scroll, page indicator, search bar, desktop previews (pinch in), layout lock.
- **Dock:** 3–9 icons, up to 3 pages, background styles, can be hidden or toggled.
- **Drawer:** grid, list or horizontal pages; tabs, drawer folders and smart folders; hide apps; sorting; frequently used row; fast scroller; work-profile tab.
- **Folders:** drop an icon on another. 5 preview styles, 4 backgrounds, pop-up or full-screen window, swipe to open.
- **Icons:** icon packs (with fallback theming for apps the pack doesn't cover), 15 shapes, reshaping legacy icons, per-icon custom label, icon and swipe action.
- **Gestures:** all 13 Nova home gestures, mapped to 17 actions, plus "jump to page" and "open app".
- **Long-press menu:** app shortcuts (drag them to home), notifications, edit, remove, app info, uninstall, widgets.
- **Now page:** swipe right on the first page for the date, media controls, upcoming events, favourite contacts and frequent apps, all on-device.
- **Other:** notification badges (dots, numbers, notification picture), night mode (system, always, scheduled or sunset to sunrise), contact search, a choice of web search engine, hide clock, backup and restore (including importing a Nova Launcher backup), widget resizing.

Long-press empty space on home for widgets, shortcuts, wallpaper, desktop previews and **Settings**.

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

On-device gesture check (13 gestures with real multi-touch events) and Nova-import check:

```sh
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.vulcanlauncher.test/dev.vulcanlauncher.DeviceCheck
```

## Permissions

| Permission | Why |
|---|---|
| `EXPAND_STATUS_BAR` | The "expand notifications" and "expand quick settings" actions (default: swipe down). Normal permission, granted at install. |
| `REQUEST_DELETE_PACKAGES` | **Uninstall** in the long-press menu and drop bar. It only opens the system's uninstall prompt, which the user confirms. Normal permission. |
| Notification access (`BadgeService`, `BIND_NOTIFICATION_LISTENER_SERVICE`) | Notification badges, notifications in the long-press menu and media controls on the Now page. **Off until the user turns it on** in Settings → Permissions. Notifications are counted in memory and never stored. |
| `KILL_BACKGROUND_PROCESSES` | **Close app** in the long-press menu (Android only lets it stop an app's background processes). Normal permission. |
| `READ_CONTACTS` (runtime, optional) | Contact search in the drawer and the favourite-contacts card on the Now page. **Asked for only when you turn one of those on.** |
| `READ_CALENDAR` (runtime, optional) | The upcoming-events card on the Now page. **Asked for only when you turn it on.** |
| `ACCESS_COARSE_LOCATION` (runtime, optional) | "Sunset to sunrise" night mode. Uses the last location Android already knows; the sun times are calculated on the device. **Asked for only when you pick that mode.** |
| `WRITE_SECURE_SETTINGS` (granted only from a computer) | **Hide clock** on the home screen. Apps can't grant it to themselves; you run `adb shell pm grant dev.vulcanlauncher android.permission.WRITE_SECURE_SETTINGS` once. Vulcan only adds or removes the `clock` entry of the status-bar icon list. |
| Accessibility service (`ActionService`, `BIND_ACCESSIBILITY_SERVICE`) | The screen lock, recent apps and power menu actions (Android offers no other way). **Off until the user turns it on.** It reads no screen content (`canRetrieveWindowContent="false"`) and only calls `performGlobalAction`. |

Nothing else, and no `INTERNET`. App shortcuts need Vulcan to be the default launcher, which is a user choice rather than a permission. The `<queries>` entries in the manifest (launcher apps, icon packs, shortcut creators, voice and web search) only make those apps visible to the launcher. Any new permission must be justified in this table (see [CLAUDE.md](CLAUDE.md)).

## Docs

- [ROADMAP.md](ROADMAP.md): stages and the Nova feature-parity checklist.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how the code fits together.
- [docs/nova-reference.md](docs/nova-reference.md): what we learned from analysing Nova Launcher 8.9.4. Used as a feature reference only; no Nova code is used here.
