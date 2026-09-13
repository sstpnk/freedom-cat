# Android TV Version Plan

This document describes a separate Android TV build for FreedomCat. The first TV release should ship as separate APK files with `-tv-` in the file name. A later release can merge phone and TV into one APK with runtime device detection.

## Current State

- `AndroidManifest.xml` already declares `LEANBACK_LAUNCHER`.
- `android.software.leanback` is optional.
- `android.hardware.touchscreen` is optional.
- `SagerNet.isTv` already detects TV mode through `UiModeManager`.
- `MainActivity` already handles some DPAD navigation for the drawer.

The project can launch on TV, but the current UI still follows the phone interaction model. It depends on dense lists, floating actions, overflow menus, touch-sized controls, and several editor screens that are hard to use with a remote.

## Goals

- Build dedicated TV APKs without changing the current phone APK flow.
- Support DPAD remote navigation for the main VPN workflow.
- Provide a TV-first home screen for selecting a profile and connecting or disconnecting.
- Keep VPN, protocol parsing, subscription updates, split tunneling, and core connection logic shared with the phone app.
- Keep the implementation small enough to ship before the future unified APK work.

## Non-Goals For The First TV Release

- Do not redesign every settings screen for TV.
- Do not require full manual protocol editing from the TV remote.
- Do not merge phone and TV into one APK yet.
- Do not duplicate VPN/core/protocol logic for TV.
- Do not change launcher icons as part of the TV build plan.

## Build Strategy

### First Release

Add a dedicated `ossTv` product flavor in the existing `vendor` flavor dimension.

Expected command:

```powershell
.\gradlew.bat --no-daemon :app:assembleOssTvRelease
```

Expected release APK names:

```text
FreedomCat-0.1.7-tv-arm64-v8a.apk
FreedomCat-0.1.7-tv-armeabi-v7a.apk
FreedomCat-0.1.7-tv-x86_64.apk
FreedomCat-0.1.7-tv-x86.apk
```

The TV flavor should use the same `versionName` and `versionCode` as the phone build unless a release task explicitly asks to bump them.

### Later Release

When the TV UX is stable, add a `device` flavor dimension or merge back into one APK. The unified APK should choose phone or TV UI at runtime using `SagerNet.isTv` and feature checks.

## Source Set Layout

Use the smallest useful TV source set:

```text
app/src/ossTv/
  AndroidManifest.xml
  res/
    drawable/
    layout/
    values/
```

Use `src/ossTv` only for TV-specific manifests and resources. Keep shared Kotlin code in `src/main` unless TV behavior needs a separate Activity or Fragment.

## Manifest Requirements

The TV flavor should:

- Keep `LEANBACK_LAUNCHER`.
- Keep `android.hardware.touchscreen` optional.
- Provide an Android TV banner if Play/launcher behavior requires it.
- Keep VPN service, import intents, foreground service permissions, and protocol links aligned with the phone build.

The TV flavor should avoid phone-only affordances when they create a poor TV launcher experience. That decision belongs in the TV manifest overlay, not in the shared manifest.

## UX Model

### TV Home Screen

The TV build should start with a dedicated home screen instead of the phone configuration list.

The screen should show:

- selected profile name, type, and group;
- VPN status;
- large Connect/Disconnect button;
- traffic/status summary;
- profile list grouped by group or subscription;
- compact actions for subscription update, import, settings, and diagnostics.

The primary path must require only DPAD and OK:

1. Open app.
2. Move focus to a profile.
3. Press OK to select it.
4. Move focus to Connect.
5. Press OK to connect or disconnect.

### Profile Selection

Use a TV-friendly list or grid:

- visible focus ring;
- large hit targets;
- no hidden swipe actions;
- no dependence on long press;
- context actions opened by Menu or a visible action row.

The first version can show only existing profiles. Creating and editing profiles can stay in the phone UI or a simplified import flow.

### Import Flow

For MVP, support at least one TV-friendly import path:

- paste/import from clipboard if available;
- manual link entry with a simple text screen;
- optional QR scanning only if camera support is reliable on the target devices.

Full protocol editor screens can stay out of scope for the first TV APK.

## Remote Navigation Rules

Every TV screen must support:

- DPAD up/down/left/right;
- OK/Center;
- Back;
- Menu where Android TV remotes expose it.

Implementation rules:

- make important controls focusable;
- add explicit `nextFocus*` only where Android focus search fails;
- add a clear focused state for cards and buttons;
- avoid controls that are reachable only by touch;
- avoid transient UI as the only way to perform a primary action.

## Technical Design

### Shared Layer

Keep these shared:

- profile database;
- group and subscription managers;
- protocol parsing and export;
- VPN service lifecycle;
- split tunneling configuration;
- traffic and status updates;
- AmneziaWG/WireGuard handling.

### TV Layer

Add TV-specific UI classes only where the phone UI is structurally wrong for TV:

- `TvMainActivity` or a TV-specific `MainActivity` branch;
- `TvHomeFragment`;
- TV profile card layout;
- TV action menu/dialogs.

The TV UI should call existing managers rather than reimplementing behavior.

## Build Output Changes

Update the output naming logic in `buildSrc/src/main/kotlin/Helpers.kt` so TV flavor APKs include `-tv-`.

Rules:

- phone OSS output remains unchanged;
- TV output gets `FreedomCat-{version}-tv-{abi}.apk`;
- preview and non-TV outputs keep their current naming behavior.

## Suggested Phases

### Phase 1: Build Variant

Goal: produce installable TV APKs without UI redesign.

- Add `ossTv` flavor.
- Add TV manifest overlay if needed.
- Add output naming for `-tv-`.
- Verify `assembleOssTvRelease`.

Acceptance:

- Phone `assembleOssRelease` still works.
- TV `assembleOssTvRelease` creates APKs with `-tv-`.
- Version stays unchanged unless explicitly bumped.

Estimate: 0.5-1 day.

### Phase 2: Remote-Safe Existing UI

Goal: make the current main screen usable enough with a remote.

- Audit focus on the drawer, profile list, group cards, service button, and stats bar.
- Add visible focus states.
- Fix DPAD traversal.
- Map Menu/OK/Back behavior for common actions.

Acceptance:

- A user can select a profile and connect or disconnect using only a remote.
- Drawer navigation works without touch.
- No primary action requires swipe or touch.

Estimate: 1-2 days.

### Phase 3: TV Home Screen

Goal: replace the phone-first main screen with a TV-first launcher screen.

- Add a large status/connect area.
- Add a focusable profile list.
- Add actions for update subscription, import link, settings, and diagnostics.
- Reuse existing service/profile managers.

Acceptance:

- The default TV entry screen is readable at 10-foot distance.
- Connect/disconnect and profile selection take at most a few remote clicks.
- Current phone UI remains unchanged.

Estimate: 2-4 days.

### Phase 4: Import And Subscription Operations

Goal: cover realistic TV setup flows.

- Add clipboard/link import path.
- Add subscription update action.
- Add clear error feedback for invalid links.
- Keep QR/manual protocol editor as optional follow-up.

Acceptance:

- A user can add a subscription or profile link without touch.
- Import failures show readable TV-scale errors.

Estimate: 1-2 days.

### Phase 5: TV QA

Goal: validate on real Android TV behavior.

- Test Android TV emulator.
- Test at least one real TV box if available.
- Verify VPN permission flow.
- Verify notification/foreground service behavior.
- Verify APK naming and install/update behavior.

Acceptance:

- TV APK installs and launches from Android TV launcher.
- VPN permission flow can be completed with a remote.
- Main profile and connection workflows pass.

Estimate: 1-2 days.

## Risks

- Android TV VPN permission dialogs may behave differently across vendor firmware.
- Existing Material dialogs and preferences may have weak focus behavior.
- Text input on TV is slow; manual protocol editing can become unusable.
- The existing single `vendor` flavor dimension may become awkward when TV expands beyond OSS.

## Open Questions

- Should the first TV build support only `ossTv`, or should it also support `fdroidTv` and `playTv` later?
- Which Android TV devices should define the test baseline?
- Should TV import prioritize subscription links over single profile links?
- Should editing profiles on TV be disabled, read-only, or partially supported?

## Recommended First Implementation

Start with Phase 1 and Phase 2. They create a separate TV artifact and make the current app operable with a remote. Then build `TvHomeFragment` in Phase 3 once the build and navigation base is stable.
