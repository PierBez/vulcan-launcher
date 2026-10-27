# Vulcan: store submissions

## 1. IzzyOnDroid (days)

Open a new issue at https://codeberg.org/IzzyOnDroid/repodata/issues (Codeberg account needed; pick the "new app" template if one is offered).

**Title:** New app: Vulcan Launcher (dev.vulcanlauncher)

**Body:**

> Please consider including Vulcan Launcher.
>
> - Source: https://github.com/krishnanunnir/vulcan-launcher (MIT)
> - Package: `dev.vulcanlauncher`
> - APK: attached to each GitHub release as `vulcan-launcher.apk` (latest: https://github.com/krishnanunnir/vulcan-launcher/releases/tag/v0.13, ~190 KB), signed with a release key, not debuggable.
> - Signing certificate SHA-256: `da454f94a59d15cb8b6119483d05cfb971a2c61c8695ba5f53f80b7c9fcaa555`
> - Fastlane metadata (descriptions, icon, screenshots, changelogs): `fastlane/metadata/android/en-US/` on `main`.
> - No INTERNET permission, no trackers, no runtime dependencies (plain Android SDK; JUnit only for tests).
> - Reproducible: `v0.13` rebuilt from a clean clone (`./gradlew assembleRelease`, JDK 17) matches the release APK (`apksigcopier compare` passes).
>
> A home screen with the features people use Nova Launcher for, and no network access at all.

## 2. F-Droid main repo (weeks)

Needs a GitLab account with an SSH key.

```sh
# 1. Fork https://gitlab.com/fdroid/fdroiddata in the browser, then:
git clone --depth 1 git@gitlab.com:<your-gitlab-user>/fdroiddata.git
cd fdroiddata
git checkout -b dev.vulcanlauncher
cp /Users/krishnanunnir/Projects/nova/vulcan-launcher/docs/fdroid/dev.vulcanlauncher.yml metadata/
git add metadata/dev.vulcanlauncher.yml
git commit -m "New app: Vulcan"
git push -u origin dev.vulcanlauncher
```

Then open a merge request to `fdroid/fdroiddata` `master` and choose the **App inclusion** template. Tick its checklist; notes for the free-text parts:

> Vulcan is an MIT-licensed Android launcher with no INTERNET permission and no runtime dependencies.
> The release APK is reproducible (verified with `apksigcopier compare` against a clean-clone build at the tagged commit), so the recipe uses `Binaries` + `AllowedAPKSigningKeys` to ship the developer-signed APK.
> The build installs JDK 17 from bookworm to match the upstream toolchain; happy to change this if the buildserver has a preferred way.
> Fastlane metadata is on `main` and will be in the tagged source from the next release.

The MR's CI runs `fdroid lint` and a test build; fix anything it reports in the same branch. A reviewer usually follows up within a few weeks.
