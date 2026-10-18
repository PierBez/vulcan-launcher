# Roadmap and Nova feature parity

Goal: cover what people use Nova Launcher for, with zero network access. Overall parity today: **about 15–20%**.

Legend: ✅ done · ⚠️ partial · ❌ not started · 🚫 won't do

## Stage 1: Home screen and widgets ✅

| Feature | Status | Notes |
|---|---|---|
| App drawer with search | ✅ | |
| Home pages | ⚠️ | Fixed at 3 pages; count becomes a setting in stage 2 |
| Dock | ✅ | 5 slots |
| Drag from drawer, move, remove | ✅ | Android framework drag-and-drop; edge-hold flips pages |
| Widgets: add, configure, move, remove | ✅ | |
| Widget resize | ❌ | Widgets get their default size |
| Wallpaper picker | ✅ | Opens the system chooser |
| Layout persistence | ✅ | SharedPreferences JSON |

## Stage 2: Looks ❌

| Feature | Status | Notes |
|---|---|---|
| Grid size (home, dock, drawer) | ❌ | Currently constants in `MainActivity` |
| Page count | ❌ | |
| Icon packs | ❌ | ADW/Nova icon-pack format (`appfilter.xml`) |
| Icon shapes (Nova has 15) | ❌ | Adaptive icon masks |
| Icon size, label show/hide/size | ❌ | |
| Night mode / dark drawer | ❌ | |

## Stage 3: Gestures and actions ⚠️

| Feature | Status | Notes |
|---|---|---|
| Swipe up → drawer, swipe down → notifications | ✅ | Hard-coded |
| Configurable gestures (Nova: 13) | ❌ | Swipe up/down (1 and 2 fingers), double tap (+swipe), pinch in/out, rotate |
| Actions (Nova: about 15) | ❌ | Drawer, notifications, quick settings, recents, jump to page, open app, toggle dock, search… |
| Swipe actions on icons and folders | ❌ | |
| Screen lock action | ❌ | Needs an accessibility service or device admin, and the user must turn it on |

## Stage 4: Drawer and folders ❌

| Feature | Status | Notes |
|---|---|---|
| Home-screen folders | ❌ | Nova: 5 preview styles, 4 backgrounds |
| Drawer folders and tabs/groups | ❌ | |
| Hide apps | ❌ | |
| Notification badges (dots, numeric) | ❌ | Needs a notification listener the user turns on |
| Scroll effects (Nova: 13) | ❌ | |
| App shortcuts (long-press menu) | ❌ | `LauncherApps.getShortcuts`, needs default-launcher role |
| Backup and restore | ❌ | Export/import the layout JSON via the Storage Access Framework |

## Won't do 🚫

| Nova feature | Why |
|---|---|
| Ads, analytics, crash reporting, activity logging | The point of this project |
| AI assistant and context sync | Needs network and broad personal data |
| Google Discover page | Needs a Google-approved companion app |
| Weather, license checks, remote config | Need network |

## Known limitations

- Every layout change re-renders all pages, which may flicker with many widgets.
- A vertical swipe on a scrolling widget can trigger the drawer or shade instead of scrolling it.
- The app list reloads on every resume; add caching and a package-change receiver if that gets slow.
- Not yet tested on a real device.
