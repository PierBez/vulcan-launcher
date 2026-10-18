# CLAUDE.md

A from-scratch Android launcher aiming at Nova Launcher feature parity with **no network access**. Read `ROADMAP.md` for current stage and parity status, and `docs/ARCHITECTURE.md` before changing structure.

## Build and test

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r <apk>`.
- The first build downloads Gradle 8.14.3, AGP 8.12.3 and Kotlin 2.1.21. In the Claude Code sandbox, Java's network access times out (even though curl works), so run Gradle with the sandbox disabled when dependencies must be fetched.
- To check permissions after a build: `/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt2 dump permissions <apk>`.

## Hard rules

- **Never add the `INTERNET` permission or any networking, analytics, ads or crash-reporting library.** That's the project's reason to exist.
- Every permission must be listed and justified in the README's Permissions table. Prefer a permission the user explicitly turns on (accessibility, notification listener) over one granted silently.
- **Never copy code, resources or assets from Nova Launcher.** `docs/nova-reference.md` is a feature list only. The Nova APK and decompiled sources live outside this repo (`../nova-launcher-*.apk`) and must never be committed.
- No runtime dependencies (no AndroidX, no Compose) unless a feature truly cannot be done with the platform API. Discuss it before adding one. Test-only dependencies are fine.

## Conventions

- Plain Views, built in Kotlin code (no XML layouts). Match the existing style: small files, `apply {}` builders, sizes in dp via `dp`.
- `items: MutableList<Item>` in `MainActivity` is the single source of truth: mutate it, then `store.save(items)`, then `render()`.
- Mark deliberate shortcuts with a `// ponytail:` comment that names the limit and the upgrade path.
- Pure logic (geometry, parsing, settings mapping) goes in top-level functions with a JUnit test in `app/src/test`. View code isn't unit-tested.
- When finishing a feature, update its row in `ROADMAP.md` and the parity percentage.
- `minSdk 26`, `targetSdk 35`, Java/Kotlin target 17.
