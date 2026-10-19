---
version: 1
slug: "n-java-dev-vulcanlauncher-mainactivity-kt-7107b8fc"
primary_target: "app/src/main/java/dev/vulcanlauncher/MainActivity.kt"
related_targets: ["app/src/main/java/dev/vulcanlauncher/Theme.kt","app/src/main/java/dev/vulcanlauncher/Drawer.kt"]
---

# Launcher surfaces (home, dock, glance, drawer, folders, menus, settings)

Mode: Operate. Used hundreds of times a day, one-handed, over the user's own wallpaper, in any light.
Scope (user, 2026-10-19): every visible surface. The old glass look is replaced, not kept. Anti-goals: looks like iOS, too decorated, too sparse/hidden.

## Direction contract

THESIS: The launcher is a well-made appliance (Braun SK4 / ET66 / T3): a warm-grey housing, printed legends, and one signal orange reserved for the single live thing on screen. It refuses Material You tonal pills and iOS frosted glass.

OWN-WORLD: Housing warm grey #E9E7E2 (dark: #2A2A28); recessed wells #DEDBD5 (dark #222220) with an inner top shadow; legends #2A2A28 (dark #E9E7E2), secondary #595752 (dark #A9A69F; #8C8A84 failed text contrast and is the outline); signal orange #E8721C, used once per screen (darkened to #B85410 on the light housing for 3:1); green #4E8F3A for "on" only. Round wells and pill slots, dot grilles, no blur, no gradients beyond well shading. Type: system sans, light for the clock, small tracked uppercase legends.

STORY: The user glances at time and date, launches from the dock or drawer, and can always tell what is live from the one orange dot.

FIRST VIEWPORT: Wallpaper untouched. Top-left: large light clock, uppercase legend "MON 19 OCT · CHARGED" beneath. Bottom: a pill-shaped recessed dock housing inset 14dp from the edges. Page indicator is a short dot grille with the current page in orange. Drawer: housing sheet (22dp top radius) with a recessed pill search, 4-column grid, right-edge letter index with the active letter in orange. Signature move: the signal dot, one orange point marking the live thing (current page, active index letter, focused search, open folder, running timer).

FORM: Appliance (Braun), my rank 1 of 7, chosen by the user as IMPECCABLE'S PICK over the assigned Stationery Index. seed:c9a1ed32de6d8aea

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
