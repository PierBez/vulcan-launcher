# Vulcan: store submissions

## F-Droid

Submitted on 29 October 2026: [New app: Vulcan, fdroiddata !54678](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/54678).

The public fork is [bizzaretor/fdroiddata](https://gitlab.com/bizzaretor/fdroiddata), branch dev.vulcanlauncher. Update this existing merge request for review or CI fixes; do not open a duplicate. The recipe is in dev.vulcanlauncher.yml beside this document.

The submission targets v0.19 (versionCode 10), commit 22115039ca0a93a7436d52a04b9a0d0672e7b9ac. Reproducible builds pin the developer signing certificate so users can switch distribution channels. Automatic updates follow version tags. Fastlane descriptions, icon, screenshots and changelogs are in the tagged source.

Validation:

- All nine [GitLab CI checks](https://gitlab.com/bizzaretor/fdroiddata/-/pipelines/3000809187) pass, including the Linux reproducible build, APK scanner, source scanner, schema, canonical formatting, lint and automatic update checks. Permission notices for contacts, calendar, local sunset/sunrise calculation and the optional accessibility service are explained in the MR and README.
- A clean unsigned JDK 17 build matches the published signed APK using apksigcopier compare --unsigned, including after F-Droid signing-configuration cleanup.
- APK SHA-256: 3ab7df9e277ca2953a6b1363408e822106eb3556b700b2b15f3218d08b049129.
- Certificate SHA-256: da454f94a59d15cb8b6119483d05cfb971a2c61c8695ba5f53f80b7c9fcaa555.
- No INTERNET permission, debuggable flag or testOnly flag.

The v0.19 APK was repackaged on 29 October 2026 to correct its embedded Git revision and omit AGP encrypted dependency metadata. App code, resources, version and signing key stayed the same, and it still matches a clean unsigned build of the tag. The GitHub release notes record this correction. The Gradle dependenciesInfo settings now disable this metadata for future releases. Build after committing and tagging; see ../BUILDING.md.

The request discloses Claude code contributions and Codex packaging assistance. Vulcan is younger than three months; external user reports and community translations are linked as evidence for F-Droid's existing-users exception. Acceptance and publication require F-Droid maintainer review.

## IzzyOnDroid

No inclusion request was filed. The current [inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/#ai-policy) rejects AI-generated application code, while Vulcan's public history credits Claude for code contributions. The app therefore conflicts with the published policy. Inclusion would require a policy change or an explicit maintainer exception with the provenance disclosed.
