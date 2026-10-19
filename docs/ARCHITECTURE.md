# Architecture

Plain Android Views plus the Kotlin standard library. No AndroidX, no Compose, no third-party libraries at runtime; JUnit and org.json are used only for tests.

```
MainActivity root (FrameLayout, handles window insets itself)
├── content (LinearLayout, vertical)
│   ├── search bar (optional, top)
│   ├── glance strip (date and status, scrolls with the first page)
│   ├── Workspace ─ CellLayout × pages        home pages, paged sideways with a transition effect
│   ├── Indicator                              page dots / line
│   ├── dockBox ─ Workspace ─ CellLayout × dock pages (one row each)
│   └── search bar (optional, bottom)
├── Drawer.root                                overlay, GONE until opened
├── dropBar (Remove | App info | Uninstall)    shown only while dragging
└── overlay (folder window, desktop previews or widget resize frame), added on demand
```

## Files

| File | Role |
|---|---|
| `MainActivity.kt` | Builds the screen, renders items, drag and drop (including folder merging), the long-press menu, actions, widgets, shortcuts and activity results. |
| `Workspace.kt` | Horizontal pager used for home, dock and drawer pages. Detects the 13 gestures (one- and two-finger, double tap, pinch, rotate) and reports them by name; applies the page transition effect while scrolling. `onPull` streams a swipe up live (the drawer follows the finger). |
| `CellLayout.kt` | Fixed grid `ViewGroup` plus the pure grid functions: `overlaps`, `fitItems` (after a grid change), `firstFreeSpot`, `removePage`, `movePage`, `scaleItems` (subgrid). |
| `Store.kt` | The `Item` model (app, widget, pinned shortcut, action, activity or folder, at page/x/y/w/h) and its JSON. Three SharedPreferences files: `layout`, `looks`, `data`. Export/import for backups. |
| `Looks.kt` | Every setting as a `Choice` (group, key, values, names) in `LOOKS`; `Looks` reads and writes them. |
| `Actions.kt` | Action and gesture lists, `twoFingerGesture`, `isNight`, drawer sort comparators. All pure. |
| `Effects.kt` | The 13 transition effects as a pure `pageTransform(effect, offset)`. |
| `Icons.kt` | Icon shapes (`shapeOutline`), `ShapedIcon`, icon pack loading (`appfilter.xml` plus iconback/mask/upon/scale), custom icons. |
| `IconView.kt` | Icon + label view with drop shadow, notification badge and swipe-up detection. |
| `Folders.kt` | Folder preview layouts (`folderPreview`, pure), the `FolderIcon` drawable, and `GlyphIcon` for action shortcuts. |
| `Drawer.kt` | The app drawer: tabs, drawer folders, search, sorting, frequent row, list/grid/pages styles, fast scroll. Opening and closing is a 0–1 `progress` driven by the finger or an animation. `Group`, `tabApps` and `drawerOpens` are pure. |
| `Theme.kt` | Material 3: the colour scheme (`Palette`; system wallpaper colours on Android 12+, else tonal palettes from a seed via `tone`, which searches for CIE L*) and the shared components: `listItem`, `showSheet`, `Toggle` switch, `tonalButton`, `iconButton`, `styleDialog`. Glass: `setDepth`/`blurHome` (home sinks back and blurs; window blur reaches the wallpaper), `semBlur` (Samsung's view blur, through reflection), `glassTint`, `frost`, and the `tick`/`confirm` haptics. |
| `Symbols.kt` | Outline icons drawn on a 24-unit grid (`Symbol`), and `ACTION_SYMBOLS` for action shortcuts. No icon font or third-party assets. |
| `Overlays.kt` | Page indicator, search bar, folder window, desktop previews, widget resize frame, undo bar. |
| `SettingsUi.kt` | The full-screen settings page (`showSettings`; reopens at the same page and scroll after `recreate()`), value pickers, edit dialogs, icon picker, hidden apps, drawer group editor, smart folders, backup, permissions. |
| `Services.kt` | `BadgeService` (notification listener) and `ActionService` (accessibility, global actions only). Both are off until the user enables them. |
| `Now.kt` | The Now page overlay (Nova's feed): date, media controls (via the notification listener), calendar, favourite contacts, frequent apps. Also the glance strip above the first home page (`glanceView`, `updateGlance`). |
| `BackupImport.kt` | Reads a `.novabackup` (zip → SQLite `favorites`/`workspaceScreens` + preferences XML). Parsing and conversion (`intentComponent`, `intentShortcut`, `importToItems`, `importedPrefs`) are pure and tested. |
| `PinActivity.kt` | Accepts "add to home screen" requests from other apps. |
| `WidgetFrame.kt` | Wraps an `AppWidgetHostView` so a long-press reaches the launcher. |

## Data flow

1. `items` (a `MutableList<Item>`) is the single source of truth, loaded from `Store` in `onCreate` and reloaded in `onResume` if `PinActivity` changed the store. Only one `MainActivity` edits it: one started outside the home task while Vulcan is the default home hands off to the home one and finishes (`handOffToHome`), and package callbacks reload from the store before they write.
2. Any change edits `items`, calls `save()`, then `render()`.
3. On start, `fitItems` moves items that no longer fit the configured grid, dock or page count.
4. `render()` clears every `CellLayout` and rebuilds views from `items` (widget views are kept and re-attached, so widgets don't reload). Missing apps (mid-update, disabled, on an unmounted SD card) are skipped, not deleted; an uninstalled package's items are removed (`removePackage`).
   On start, items of packages that are gone for good (Android says not installed for that profile, or the profile was removed) are dropped too, since no callback came while the launcher was stopped.
   A layout that can't be read falls back to the newest readable automatic backup (three daily generations; the raw text is kept in `files/layout-unreadable.json`); a single bad item is dropped on its own.
   The layout file stores `version` (now 2) and `sub`, the subgrid factor its positions are in, so it reads right whatever the settings file says: `load(sub)` converts it (`layoutIn`). Version 1 had no `sub` and is read as already in the current factor.
5. Settings are read through `Looks` while building views. Changing one calls `recreate()`; the open settings page is saved in `onSaveInstanceState` and reopened.
6. Every colour comes from `m3` (a `Palette`), built in `onCreate` from the Theme color setting and the panel theme (night mode).

## Apps and profiles

Apps come from `LauncherApps.getActivityList` for every profile, so work-profile apps are included. An app's key is `pkg/cls/userSerial`. Launches go through `LauncherApps.startMainActivity` with the chosen launch animation. A `LauncherApps.Callback` reloads the app list when packages change.

## Drag and drop

This uses the framework API (`View.startDragAndDrop`). The `localState` payload is an `Item` (moving on home), an `App` (from the drawer or a drawer folder), a `FolderChild` (out of a home folder) or a `ShortcutInfo` (from the long-press menu).

A drag that never moves more than 16 dp counts as a long-press, and shows the long-press menu instead (`trackDrag`, `dragEnded`). The drawer and folder windows hide as soon as the drag moves. Dropping an app-like payload on an app, shortcut, action or folder merges them into a folder; otherwise the drop needs free cells (unless widget overlap is on). Holding near a page edge flips pages.

## Widgets

The flow is pick, then bind, then configure, then place: `pickWidget` → `addWidget` (`bindAppWidgetIdIfAllowed`, or the `ACTION_APPWIDGET_BIND` prompt) → `configureWidget` → `placeWidget`. Cancelling at any step frees the widget ID. Removed widgets keep their ID until the undo bar times out. The widget being bound or configured is saved (`pendingWidget`), so its result still lands if the launcher is killed meanwhile, and `onCreate` frees any widget ID no item holds. `ResizeFrame` resizes within the provider's limits, and each widget view reports its size with `updateAppWidgetSize`.

## Tests

- `app/src/test`: JVM unit tests for all pure logic (grid, gestures, effects, folders, drawer tabs, night mode, sun times, Nova import parsing, colour tones and WCAG contrast of every text/container pair).
- `tools/perf`: device measurements over adb. `measure.sh` (release: cold start, single instance, drawer `gfxinfo`), `leak.sh` (debug: Activity count after 10 recreates and a forced GC; analyse a kept heap dump with `shark-cli`), `trace.sh` (Perfetto trace of the drawer, main-thread frame times through `frames.sql`). Debug builds also turn on StrictMode (logged).
- `tools/ui-test/a11y.py`: flags clickable views without a spoken label and touch targets under 48 dp on the screen showing now.
- `app/src/androidTest/.../DeviceCheck.kt`: a plain `Instrumentation` (no test libraries) that runs on a phone. It dispatches real two-finger `MotionEvent`s into the launcher window to check all 13 gestures. With `-e check import` it reads `files/test.novabackup` through the real import code. Your settings are saved before the run and restored after.

## Shortcuts taken on purpose

Simplifications are marked in the code with `ponytail:` comments. Each one says where it stops working and what the upgrade is. Find them with `grep -rn "ponytail:" app/src`.
