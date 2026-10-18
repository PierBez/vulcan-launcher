# Architecture

Plain Android Views plus the Kotlin standard library. No AndroidX, no Compose, no third-party libraries at runtime; JUnit is used only for tests.

```
MainActivity (FrameLayout root, fitsSystemWindows)
├── LinearLayout (vertical)
│   ├── Workspace ─ CellLayout × PAGES      home pages, horizontally paged
│   └── CellLayout (page = DOCK)            dock, one row
├── drawer (LinearLayout: EditText + GridView)   overlay, GONE until swipe up
└── removeBar (TextView)                    shown only while dragging
```

## Files

| File | Role |
|---|---|
| `MainActivity.kt` | Wires everything together: renders the layout, drag and drop, the widget flow, the drawer and launching apps. |
| `Workspace.kt` | Horizontal pager. Intercepts horizontal drags for paging and reports vertical swipes through the `onSwipeUp` and `onSwipeDown` callbacks. |
| `CellLayout.kt` | Fixed grid `ViewGroup`. Children carry their cell rectangle in `CellParams`. `isFree` and `firstFree` do placement. `overlaps()` is a pure function and has a unit test. |
| `WidgetFrame.kt` | Wraps an `AppWidgetHostView` so a long-press can start a drag even though the widget handles its own touches. |
| `Store.kt` | The `Item` model (an app or a widget at page/x/y/w/h), persisted as one JSON array in SharedPreferences. `page == DOCK (-1)` means the dock. |

## Data flow

1. `items` (a `MutableList<Item>`) is the single source of truth, loaded from `Store` in `onCreate`.
2. Any change (drop, remove, widget placed) edits `items`, calls `store.save(items)`, then `render()`.
3. `render()` clears every `CellLayout` and rebuilds views from `items`. Missing apps (for example mid-update) are skipped, not deleted.

## Drag and drop

This uses the framework API (`View.startDragAndDrop`). The `localState` payload is:
- an `Item`, when moving something already on home, or
- a `ResolveInfo`, when dragging a new app from the drawer.

Each `CellLayout` has a drop listener. `removeBar` deletes `Item` payloads and re-renders on `ACTION_DRAG_ENDED`. Holding the drag near a page edge flips pages (`edgeFlip`, rate-limited to one flip per 700 ms).

## Widgets

The flow is pick, then bind, then configure, then place:
`pickWidget` → `addWidget` (`bindAppWidgetIdIfAllowed`, or the `ACTION_APPWIDGET_BIND` prompt) → `configureWidget` (if the provider has a configure activity) → `placeWidget` (first free spot on the current page, sized from `minWidth`/`minHeight`). Cancelling at any step frees the allocated widget ID. The `AppWidgetHost` (ID 1024) listens between `onStart` and `onStop`.

## Shortcuts taken on purpose

Simplifications are marked in the code with `ponytail:` comments, each saying where it stops working and what the upgrade is. Find them with `grep -rn "ponytail:" app/src`.
