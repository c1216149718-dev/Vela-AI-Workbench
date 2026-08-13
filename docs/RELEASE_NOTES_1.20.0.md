# Vela 1.20.0 — First Public Release

Vela is a local-first Android workbench that brings tasks, focus sessions, daily reflections, DeepSeek balance insights, APIKEY.FUN multi-key usage, and a compact home-screen widget into one calm interface.

## Highlights

- New right-edge tool handle that sleeps mostly off-screen, wakes on touch, and opens by tap or left drag.
- Stable explicit navigation across focus, focus history, daily reflections, DeepSeek, APIKEY.FUN, and key management.
- Unified compact linear icons throughout the tools panel.
- Local-first task, reminder, focus, reflection, and AI-usage data backed by Room v4 and DataStore.
- Multi-key APIKEY.FUN aggregation with partial-failure recovery and strict currency separation.
- DeepSeek balance snapshots with estimated balance-delta spending clearly distinguished from official usage.
- Resizable `3 × 2` RemoteViews widget, light/dark themes, reduced-motion support, Vico charts, and SceneView celestial focus themes.

## Verified baseline

- 54/54 JVM tests passed.
- 23/23 connected tests passed on an Android 16 `medium_phone` emulator.
- Android Lint passed with zero errors.
- Package metadata, AppWidget declarations, v2 debug signature, and release-file SHA-256 were verified.
- Light, dark, reduced-motion, tap, drag, close, and six-destination tool-panel journeys were visually checked.

## APK

`Vela-1.20.0-debug.apk` is signed with the standard Android debug certificate and is intended for evaluation only. It is not a production or Play Store build.

SHA-256:

```text
E32AA68A8E1F426C11525FDFC1E2FEB80CC08679B6E28AE7E9387DAAD0AC3697
```

## Known limitations

- Real-account DeepSeek/APIKEY.FUN values still require field-by-field verification with user-owned credentials.
- Provider credentials use private Preferences DataStore in this version; Keystore-backed encryption is planned before production-signed distribution.
- Manufacturer devices, landscape, three-button navigation, Doze behavior, and a second launcher require broader validation.
- WorkManager reminders are inexact and may be delayed by device power-management policies.

See the [README](../README.md) for setup, privacy boundaries, architecture, and source-build instructions.
