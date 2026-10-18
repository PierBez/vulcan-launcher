# Nova Launcher reference (feature source only)

These are notes from a static analysis of `nova-launcher-89400-8-9-4.apk` (Nova Launcher 8.9.4, package `com.teslacoilsw.launcher`, SHA-256 `cd97d8af43ce369fd12874a50077eba1c5f2157008eced681669b0669bcc8143`), done on 2026-10-18 with REA, JADX and apktool. Nothing was executed.

**Nova's code is proprietary. Do not copy code, resources or assets from it into this repo.** Use this file only to decide *what* to build. The APK and decompiled output live outside the repo.

## Feature inventory (from Nova's settings strings)

- **Icon shapes (15):** round, square, rounded square, squircle, teardrop, pebble, flower, vessel, tapered/cropped, pentagon, hexagon, heptagon, octagon, crop, …
- **Scroll effects (13):** simple, cube, flip, flip vertical, glass, accordion, card stack, in and out, revolving door, tablet, throw, wipe, zoom fade.
- **Folders:**
  - Previews: fan, grid, line, radial, stack.
  - Backgrounds: circle, disc, platform, square.
  - Options: clip to background, prioritise the first icon.
- **Badges:** dots, numeric dots, dynamic (uses the notification content).
- **Gestures (13):** swipe up/down/left/right, two-finger swipe up/down, double tap, double tap + swipe up/down, pinch in/out, two-finger rotate clockwise and anticlockwise.
- **Nova actions:**
  - Navigation and launcher: app drawer, default page, jump to page, desktop previews, toggle dock, first item in folder.
  - System panels: expand notifications, expand quick settings, toggle notification bar, recent apps.
  - Search and assistants: search, voice search, assistant, Google Discover.
  - Screen lock.
- **Other:**
  - Layout: dock background (can draw behind the nav bar), drawer groups and tabs, hide apps.
  - Appearance: night mode (auto or scheduled), wallpaper modes.
  - Backup: to device storage or SD card.
  - Feed: Google Discover feed page (overlay or page transition, edge swipe).

## Things to deliberately *not* replicate

What Nova 8.9.4 does, and why this project avoids each one:

- **Activity logger:**
  - What it records: Wi-Fi on/off and connected network (SSID), Bluetooth on/off and devices (name and MAC), every app or shortcut launched from the launcher, app usage, and package installs and removals.
  - Where it goes: uploaded every 8 hours to `api.novalauncher.com/stats/rawDataLog`.
  - Protection: identifiers are hashed with unsalted MurmurHash3, which is pseudonymous rather than anonymous.
  - When it runs: skipped only when the user is in Korea, judged by an IP lookup. No user toggle was found.
- **Ads:** mediation configured from the server (Pangle, Vungle, Moloco, Fyber, Meta, AdMob). Ads are suppressed for Prime users and for users who have limited ad tracking.
- **Analytics:** Firebase, Sentry, Amplitude, TikTok, Branch.
- **Instabridge AI:** context collectors for contacts, calendar, location, apps, Wi-Fi and Bluetooth, synced to `ai.instabridge.com` when the user consents. The SMS and call-log collectors exist but the permissions aren't declared.

## Public coverage (for context)

- Instabridge acquired Nova on 2026-01-20; Branch had owned it since 2022. Ads and trackers followed. <https://novalauncher.com/nova-is-here-to-stay/>, <https://www.androidauthority.com/nova-launcher-acquisition-ads-update-3633871/>
- Nova AI and a "Nova Plus" tier were spotted in a v8.6.8 beta. <https://www.androidheadlines.com/2026/04/nova-launcher-could-soon-add-an-ai-assistant-and-nova-plus-subscription-tier.html>
- Prime vs free features: <https://www.guidingtech.com/nova-launcher-prime-vs-nova-launcher/>
