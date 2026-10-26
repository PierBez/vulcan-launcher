# Roadmap and Nova feature parity

Goal: cover what people use Nova Launcher for, with zero network access. The checklist below follows Nova 8.9.4's own settings screens (taken from its string resources; see `docs/nova-reference.md`).

Every Nova feature that can work without the network is built. Each Nova settings screen has a counterpart, and only one on-device feature stays out ("Android 7.1 icons", which Android itself makes impossible). The remaining 🚫 rows need the network, which this project never uses.

Legend: ✅ done · ⚠️ partial · ❌ not started · 🚫 won't do

## Vulcan design (beyond Nova)

Vulcan's own look, on by default; every Nova-style option is still in settings.

| Feature | Status | Notes |
|---|---|---|
| First-run dock | ✅ | Default phone, messages, browser, camera (and gallery if room) |
| Calm defaults | ✅ | 4×6 grid, 64 dp icons (60 dp in the drawer) in the shape each app ships with (the system mask), rounded-square folders, no dock background (the icons sit on the wallpaper) |
| Appliance look | ✅ | The default theme ("Appliance"): a warm-grey housing with black legends, like a Braun radio or calculator, and one signal orange for the one live thing on screen (current page, active letter, focused search, selected tab). Green marks switches that are on. Light and dark follow night mode |
| Housing dock (optional) | ✅ | Dock → Background → Housing: a floating pill in the housing colour with a hairline edge and a soft shadow |
| Housing drawer | ✅ | One A–Z grid on the opaque housing, with a letter index down the right edge (touch or drag to jump). Follows the finger on swipe up and pull down; a fling or passing halfway decides. Recessed search slot at the bottom, under the thumb; it rides up with the keyboard and results sit just above it |
| Housing menus | ✅ | Long-press and home menus are raised housing popovers at the finger: shortcuts and notifications as rows, actions as icon tiles |
| Glass (optional) | ✅ | The previous frosted look is still there: Blur on in Look & feel, Glass dock and Glass grid drawer. Real blur through Android's cross-window blur (Android 12+) or Samsung's view blur on One UI |
| Glance | ✅ | A large clock over a printed date legend on the first page, with one status line: what's playing, the next event today, or charging. All on-device |
| Haptics | ✅ | Ticks when a drag lifts, an item drops and the drawer commits; a firmer one when a folder is made |

## Home screen

| Feature | Status | Notes |
|---|---|---|
| Desktop grid | ✅ | 3–12 columns × 3–12 rows ("big grid" range) |
| Subgrid positioning | ✅ | Half-cell steps; toggling converts the layout |
| Pages, default page | ✅ | 1–9 pages; add, remove, reorder and set default in desktop previews |
| Desktop previews | ✅ | Pinch in (default), home button option or action |
| Icon layout (size, labels, label size, color, font, lines, shadow) | ✅ | Separate size and labels for home, dock and drawer |
| Transition effects (13) | ✅ | Simple, cube, flip, flip vertical, glass, accordion, card stack, in and out, revolving door, tablet, throw, wipe, zoom fade |
| Page indicator | ✅ | None, dots, line |
| Infinite scroll | ✅ | |
| Wallpaper scrolling | ✅ | On, off, reverse |
| Search bar | ✅ | Top, bottom, above or below dock icons; 3 shapes, colors; tap = drawer search, mic = voice search |
| Lock layout | ✅ | Long-press shows the menu only; no moving, adding or removing |
| Widget overlap | ✅ | When placing and resizing |
| Add icons of new apps to home | ✅ | |
| Notification bar show/hide, dark icons | ✅ | |
| Screen orientation | ✅ | Portrait (default), rotate with phone, landscape |
| Hide clock | ✅ | Hidden while the home screen shows. Needs `WRITE_SECURE_SETTINGS`, granted once with `adb shell pm grant` (Nova needs the same) |

## Dock

| Feature | Status | Notes |
|---|---|---|
| Dock on/off, icon count | ✅ | 3–9 icons |
| Dock pages | ✅ | Up to 3, swiped sideways |
| Dock background | ✅ | None, rectangle, rounded, card; color and opacity |
| Draw behind navigation bar | ✅ | |
| Swipe indicator | ✅ | Arrow above the dock; tap opens the drawer |
| Dock as overlay, dock shadow | ✅ | |

## App drawer

| Feature | Status | Notes |
|---|---|---|
| Style: vertical grid, list, horizontal pages | ✅ | |
| Grid columns/rows, icon size, labels | ✅ | |
| Background color (follows night mode) and opacity | ✅ | |
| Search | ✅ | Apps, plus an optional "search the web" entry that hands the query to your browser |
| Sort: A–Z, Z–A, newest, most used | ✅ | Launch counts are stored only on the device |
| Frequently used apps row | ✅ | |
| Show keyboard on open, close after launch | ✅ | |
| Fast scrollbar | ✅ | Letter sections |
| Contact search | ✅ | Optional; asks for the contacts permission when turned on |
| Web search engine | ✅ | Default app, Google, DuckDuckGo, Bing, Startpage, Ecosia or Brave; opens in your browser |
| Drawer groups: tabs and folders | ✅ | Folders first; keep grouped apps in the main tab |
| Smart folders | ✅ | From the category each app declares; no server lookup |
| Hide apps, show hidden apps | ✅ | |
| Work apps in their own tab | ✅ | Work-profile apps via `LauncherApps` |
| Private Space (Android 15+) | ✅ (beyond Nova) | Its own drawer tab with a lock/unlock button (Android asks for the screen lock). Its apps stay out of All apps, Frequent and the home screen |
| Pull down to close, or pull down to search | ✅ | |
| Remember position | ✅ | Tab and scroll position |
| Isolate tabs | ✅ | Tabs change only by tapping, never by swiping |

## Folders

| Feature | Status | Notes |
|---|---|---|
| Create by dropping an icon on another; add, remove, rename | ✅ | A folder left with one item turns back into it |
| Reorder icons in a folder | ✅ | Drag within the open folder; drag off its grid to take the icon out. No live shuffle while dragging |
| Preview styles (5) | ✅ | Grid, stack, line, fan, radial |
| Backgrounds (4) | ✅ | Circle, disc, platform, square (plus none); color |
| Clip to background, prioritize first icon, show name | ✅ | |
| Window style | ✅ | Pop-up or full screen. The pop-up opens over the folder's icon, under the thumb, and rises above the keyboard while renaming |
| Swipe to open (tap launches first item) | ✅ | |

## Look & feel

| Feature | Status | Notes |
|---|---|---|
| Icon packs (ADW/Nova format) | ✅ | Unlisted apps get the pack's iconback/iconmask/iconupon/scale treatment |
| Icon shapes (15) | ✅ | Same set as Nova. Applies to adaptive icons, and to legacy ones with reshape |
| Reshape legacy icons (unthemed or always) | ✅ | |
| Normalize icon size | ✅ | Measures each legacy icon's visible area and scales it to fill 84% of the icon box |
| Icon shadow | ✅ | |
| Custom icon and label per item | ✅ | From an icon pack (searchable picker), from the gallery, or the default |
| App launch animation | ✅ | Zoom from the icon (default), system, reveal, fade, slide up, slide left, none |
| Theme color | ✅ | Material 3 colour scheme. "Wallpaper colors" (default) uses the system palette on Android 12+, or a seed taken from the wallpaper on older versions; any listed colour can be the seed instead |
| Material 3 design | ✅ (beyond Nova) | Full-screen settings with switches, bottom sheets, M3 menus, filter-chip drawer tabs, search bars, snackbar, tonal action icons, drawn outline icons, blur behind panels (Android 12+) |
| Long-press menu | ✅ | Housing popover (glass when Blur is on). App shortcuts (drag to home), notifications (tap opens, long-press dismisses), edit, remove, app info, uninstall, widgets, hide, add to group, save APK |
| Drop targets while dragging | ✅ | Remove, app info, uninstall |
| Undo after removing | ✅ | |
| Icon export (PNG) | ✅ | Long-press menu |
| Wiggle icons during drag and drop | ✅ | Home icons wobble while something is dragged |
| "Android 7.1 icons" (prefer legacy icons) | 🚫 | Android doesn't expose an app's legacy icon separately once it has an adaptive one |
| Close app | ✅ | Long-press menu. Like Nova's, it can only stop an app's background processes (an Android limit) |

## Night mode

| Feature | Status | Notes |
|---|---|---|
| Off, on, follow system, schedule | ✅ | Darkens drawer, folders, menus and dialogs |
| Sunset to sunrise | ✅ | Last known location (optional permission) and an offline sun calculation; falls back to the schedule |

## Notification badges

| Feature | Status | Notes |
|---|---|---|
| Dots, numeric dots, dynamic (notification picture) | ✅ | Needs notification access, turned on by the user |
| Position, size, color | ✅ | Folders show the total of their apps |

## Gestures & inputs

| Feature | Status | Notes |
|---|---|---|
| 13 gestures | ✅ | Swipe up/down, two-finger swipe up/down, swipe left/right (past the last/first page), double tap, double tap + swipe up/down, pinch in/out, two-finger rotate clockwise and counterclockwise |
| Actions | ✅ | App drawer, notifications, quick settings, toggle notification bar, recent apps, default page, jump to page, desktop previews, toggle dock, search, voice search, assistant, screen lock, power menu, settings, add widget, wallpaper, open app |
| Swipe actions on icons and folders | ✅ | Swipe up on any home icon |
| Launcher actions as home-screen shortcuts | ✅ | Add shortcut → Launcher action |
| Home button | ✅ | Default page, desktop previews or nothing |
| Screen lock | ✅ | Accessibility service, turned on by the user (Android 9+) |
| "First item in folder" action | ✅ | Via "swipe to open" folders |

## Widgets and shortcuts

| Feature | Status | Notes |
|---|---|---|
| Add, configure, move, remove widgets | ✅ | The picker shows each widget's preview image (its app icon when the widget only has an Android 12 preview layout) |
| Widget resize | ✅ | Edge handles; honours the provider's resize mode and minimum size |
| Widget settings (reconfigure) | ✅ | |
| Pinned shortcuts and widgets from apps ("Add to home screen") | ✅ | `PinActivity` |
| Legacy shortcut creators, activity shortcuts | ✅ | Add shortcut → App shortcut / Activity |

## Now page (Nova's feed)

| Feature | Status | Notes |
|---|---|---|
| Feed page left of the first page | ✅ | Swipe right on the first page (default) or the "Now page" action; swipe left closes it. Can be turned off |
| Date and time, media controls, upcoming events, favourite contacts, frequent apps | ✅ | Each card can be turned off; calendar and contacts ask for permission when turned on |
| Weather, news, games, rewards, Google Discover | 🚫 | Need the network |

## Backup & restore

| Feature | Status | Notes |
|---|---|---|
| Back up to a file, restore from a file | ✅ | One JSON file through the system file picker (device storage, SD card, any provider) |
| Readable, AI-writable backup | ✅ | Beyond Nova: the backup explains itself (`howTo`, every setting's allowed values, actions, installed apps), so a person or an AI assistant can write a whole setup. Apps can be named by package only; values that aren't allowed are skipped and reported. Drawer tabs and folders, hidden apps and renamed apps are plain JSON. Older backups still restore |
| Reset to defaults | ✅ | |
| Automatic backup | ✅ | Daily, kept privately on the device |
| Import a Nova Launcher backup (.novabackup) | ✅ | Apps, folders, deep shortcuts, dock, pages, grid and subgrid. Widgets must be added again (their IDs belong to Nova) |
| Import from other launchers | 🚫 | Their layout databases aren't readable by other apps |

## Won't do 🚫

| Nova feature | Why |
|---|---|
| Ads, analytics, crash reporting, activity logging, daily stats | The point of this project |
| Nova AI, Nova Plus, Nova Mobile (eSIM), Rewards | Need the network and broad personal data |
| Google Discover, and the Nova Now cards that fetch data (weather, news, games) | Need the network or a Google-approved companion app. The on-device cards are on the Now page |
| Web search suggestions while typing, API integrations (Spotify, GitHub, …) | Network. Choosing a search engine is supported (it opens your browser) |
| Remote config, license checks, self-update | Network |

## Known limitations

- Changing a setting restarts the launcher screen.
- Picking up an icon stalls one frame (30–40 ms on a Galaxy S23): the framework's `startDragAndDrop` creates the drag surface synchronously. Launcher3 avoids it with its own in-window drag layer; that's the upgrade if it matters.
- Widgets whose setup screen hands off to another screen (Samsung's clock widgets) report "cancelled" at once, so Vulcan doesn't add them.
- TalkBack: pages turn with scroll actions, and every icon has "Menu" (plus "Move to page" on home) actions; with TalkBack on, Resize offers the sizes that fit as a list. The glance date and status lines are 24 dp touch targets (48 dp would take a row from the home grid).
- Translations: every string is a resource, ready for `values-<language>` files; only English ships so far. Dates on the glance and Now page follow the language's own order.
- Right-to-left languages: home, dock and drawer grids, menus and settings are mirrored; pages still run left to right.
- A paused work profile hides its apps (Vulcan has no cached list to show greyed out).
- The Private tab shows even when Android's "hide when locked" setting is on: launchers can't read that setting.
- Items that have no room after the grid shrinks stay hidden until it grows again.
- A vertical swipe on a scrolling widget can trigger a gesture instead of scrolling it.
- App shortcuts only work while Vulcan is the default launcher (an Android rule).
- Tested on a Galaxy S23 (Android 16):
  - Over adb: drawer, drag from drawer to home, folders (create, open, drag out), long-press menu, app shortcuts (listed, dragged to home, launched), home menu, settings, desktop previews, icon shapes, numeric badges, the accessibility action (recents), hide clock, contact search, and the Now page with calendar and favourites.
  - Robustness, with `tools/perf` (release build unless noted): one activity after starting Vulcan from outside the home task (was two); 2 activities after 10 recreates in a debug build (was 11, about 20 MB each, held by an activity-scoped `LauncherApps`); no drawer frame over 16 ms in three traced open/fling/close cycles (was one per open, up to 29 ms); cold start ~130 ms, unchanged. An item of an app uninstalled while Vulcan was stopped, and one from a removed profile, are dropped at the next start; a disabled app's icon is kept. Arabic (per-app locale) and 2× font checked by screenshots, and `tools/ui-test/a11y.py` on home, drawer and settings.
  - With `DeviceCheck` (real multi-touch events dispatched into the launcher window): all 13 gestures, and reading a `.novabackup` built from Nova's own table definitions. A backup file made by Nova itself hasn't been tried, and the media card hasn't been seen with something playing.
