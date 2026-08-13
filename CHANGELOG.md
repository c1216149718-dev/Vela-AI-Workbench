# Changelog

All notable public releases of Vela AI Workbench are documented here.

## [1.20.0] - 2026-08-13

First public source release.

### Added

- A right-edge tool handle that sleeps mostly off-screen, wakes on touch, and opens by tap or left drag.
- Explicit, regression-tested navigation for focus, focus history, daily reflections, DeepSeek, APIKEY.FUN, and key management.
- A unified set of compact linear icons for the tools panel.
- Public project documentation, build instructions, privacy boundaries, asset attribution, and a reproducible source layout.

### Included baseline

- Local-first tasks, reminders, focus sessions, daily reflections, and Room v4 persistence.
- DeepSeek balance snapshots with clearly labeled delta-based spend estimates.
- APIKEY.FUN multi-key usage aggregation with partial-failure cache retention and currency separation.
- A resizable `3 × 2` RemoteViews home-screen widget.
- Compose-based workbench UI, light/dark themes, reduced-motion behavior, Vico charts, and SceneView celestial focus themes.

### Verification

- 54 JVM tests passed.
- 23 Android 16 connected tests passed.
- Android Lint completed with zero errors.
- Debug APK v2 signature and package metadata were verified.

### Distribution note

The v1.20.0 APK is signed with the standard Android debug certificate and is intended for evaluation, not production distribution.
