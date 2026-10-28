---
version: 1
slug: "n-res-drawable-ic-launcher-foreground-xml-1435f164"
primary_target: "app/src/main/res/drawable/ic_launcher_foreground.xml"
related_targets: ["docs/icon.svg","docs/icon.png","docs/apple-touch-icon.png","docs/favicon.ico","docs/index.html","README.md","fastlane/metadata/android/en-US/images/icon.png","app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml","app/src/main/java/dev/vulcanlauncher/SettingsUi.kt","app/src/main/java/dev/vulcanlauncher/MainActivity.kt","tools/export-logo.py"]
---

# Vulcan app icon

Mode: Operate. A quick visual identifier in Android's home picker, app lists, and settings, also used by the README, website, and store listing.

Scope: the user chose "A cleaner version of the current sunset", approved the result, and then asked to adopt it as the logo everywhere. Keep the approved geometry and Appliance palette across Android, Settings and About, the README, website header and favicons, Apple shortcuts, shared-link previews, and store metadata. Generate web and store assets together from the native vector source.

## Direction contract

THESIS: One orange half-sun and one clear horizon retain Vulcan's sunset identity without the incumbent's small, fading wave strokes.

OWN-WORLD: Existing signal orange, warm-white ink, and charcoal housing. Two opaque shapes, flat fills, and generous negative space; no gradients or additional symbols.

STORY: The user recognizes the same sunset immediately, including at small icon sizes and with themed icons enabled.

FIRST VIEWPORT: An orange semicircle above a single separated, round-ended warm-white horizon on charcoal. The approved pair is scaled uniformly to 92% about (54,54) and lifted 2 foreground units for optical balance inside the circular mask. Both paths remain inside the adaptive icon's central safe circle. Android controls the outer mask, including the settings nameplate's 64dp logo and About's native dialog icon; the website and store use a matching rounded-square crop. Apple receives an opaque square for its own mask. Every public export comes from tools/export-logo.py.

FORM: A narrow vector refinement inside the established Appliance world, with the sunset explicitly pinned by the user. No identity or composition tournament applies; no seed was needed.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Verification — original icon-only phase, 2026-10-27

The finish review returned **ship**, with every icon fidelity category matching and no material fixes. The native API 35 Pixel 8 app-info capture and Android Canvas exports are in `.impeccable/review/icon/`; the themed previews use illustrative colors over the native monochrome silhouette. The SVG shares the vector paths, stroke, and central adaptive-layer crop. Both paths, including round stroke caps, fit the radius-33 safe circle. `assembleDebug` and `git diff --check` passed; the store PNG carries native-render provenance, and the provenance scan found one shipping raster with none missing.

The artifact reused incumbent Signal Orange (`#E8721C`), warm-white night legend ink (`#E9E7E2`), and night housing (`#2A2A28`), with Android retaining control of the outer mask. At this original icon-only phase, `DESIGN.md` and `.impeccable/design.json` were unchanged and no global tokens or rules were added. Pre-existing tab-dot, page-grille, and search-preview drift was outside that scope and was neither repaired nor adopted as new guidance.

## Verification — approved logo rollout

After approving the two-shape icon, the user explicitly asked to adopt it as the logo everywhere. This authorizes a durable brand component within the existing Appliance world. `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` now record the fixed sunset identity and its narrow exception to the live-control signal rule; the remaining palette, typography, and component rules are preserved.

The finish review returned **ship**: all identity placements and export fidelity matched, with no material fixes. The review approved Android's native mask, Apple's opaque-square mask input, and custom shortcut icon precedence. The Settings nameplate uses the 64dp native mipmap with a 16dp start gap, About uses the native icon, and the home Launcher settings action uses the application icon after any user override. Generic settings gears remain functional symbols. The logo keeps its approved colors across the checked light and dark themes, while native system-mask and themed-icon support remain in place.

All rollout evidence was viewed in `.impeccable/review/logo-rollout/`: `website.png` (1280 × 720), `website-mobile.png` (390 × 844), `settings-light.png`, `settings-dark.png`, `settings-large-text.png`, `about.png`, `home-shortcut.png`, `settings-publish.png`, and `asset-board.png`. Native verification used an API 35 Pixel 8 emulator only. The refreshed Settings screenshot prepared for publication came from the actual emulator; the website and store copies both use 540 × 1170 output.

`tools/export-logo.py` derives every logo export from `app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_background.xml`, preserving the paths, colors, round horizon caps, and central 72/108 crop. `python3 tools/export-logo.py` regenerates all five output paths together: `docs/icon.svg`, `docs/icon.png` (512px), `fastlane/metadata/android/en-US/images/icon.png` (byte-identical to the web PNG), `docs/favicon.ico` (16/32/48px frames), and `docs/apple-touch-icon.png` (180px, opaque square for Apple's mask). The header and README use the SVG at 32px and 96px respectively; Open Graph and Twitter metadata use the public HTTPS PNG URL. See `docs/BUILDING.md` for regeneration details.

The final implementation passed `assembleDebug`; deterministic re-export assertions passed for all five paths, the ICO frames were checked, SVG/native geometry matched, and all four web logo assets returned HTTP 200 in the local serving check. The provenance scan found five changed shipping rasters with zero missing records, and `git diff --check` passed. These checks do not claim a public deployment or additional native device coverage.

The existing website CSS, font, color, and radius findings remain recorded in `.impeccable/review/logo-rollout/detector.json`; no website CSS changed. Those findings and the prior tab-dot, page-grille, and search-preview drift remain outside this approved brand rollout and are neither repaired nor canonized.

## Circular-mask optical correction

After the user flagged circular alignment, a fresh finish review requested one material correction: uniformly scale the approved pair to 92% about (54,54), then lift it 2 foreground units. The native foreground now carries that adjustment, preserving the colors, relative proportions, and two-shape arrangement, and `tools/export-logo.py` regenerated all public exports. The sidecar preview matches the current SVG and `DESIGN.md` records the optical alignment.

The verdict pass scored this fix **resolved**, with disposition **ship** for the circle-alignment correction. Native APK renders at 32/48/64px and large sizes, plus installed light/dark Settings captures, are in `.impeccable/review/circle-alignment/`; rounded-square and themed previews retained the identity. `assembleDebug` passed, the website/store Settings screenshots were refreshed from the corrected native capture, and the five changed shipping rasters passed provenance scanning with none missing. This verdict covers the single optical-placement fix; the earlier verdicts describe their preceding phases.

## Release 0.18 verification

Version 0.18/code 9 passed 43 unit tests and a signed release build. The signing certificate matches the preceding release, the APK is not debuggable, and its manifest has no internet permission. All five screenshots were recaptured from the signed APK on a clean Pixel 8 API 35 emulator using the existing dusk wallpaper, default layout, dark mode, normal font scale, and SystemUI demo status bar. Native 1080×2340 captures were exported as matching 540×1170 website/README and Fastlane copies, with origin metadata embedded.

The final release/screenshot review returned **ship**, with no material fixes. Evidence in `.impeccable/review/release-0.18/` includes the five native captures, the screenshot contact sheet, settled desktop/mobile website captures, and the website's five-image strip. The release app remained running and its emulator crash buffer was empty. README descriptions and version highlights match the captures; all thirteen current shipping logo/screenshot rasters passed provenance scanning with none missing.
