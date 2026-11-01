# Android development fork

This is Ryan's development fork of [Hex](https://github.com/anomalyco/hex).
The default branch, `android`, contains the native Android proof of concept.
The original desktop source and commit history are retained. Upstream's licence
and notices still apply. No upstream contribution or Android release is implied.

## What is included

The independent `android/` Gradle project keeps your existing keyboard and adds
one movable Accessibility button. Tap to start/stop, or hold and release.
It appears only for a supported focused field with the keyboard visible, inserts
at the cursor, and cancels if the target changes. The button uses the desktop Hex
mark, pearl listening scope and blue processing flow. Recognition uses Android's
on-device service; this is not a port of the desktop Rust inference engine.

See [setup and build instructions](android/README.md) and the
[behavior and verification map](docs/features/android.md). The current APK is
`0.6.0-poc`; generated APKs, local SDK paths and build output are ignored.

## Repository workflow

- `origin`: `https://github.com/ryancontent/hex`
- `upstream`: `https://github.com/anomalyco/hex`
- Fork default: `android`. Keep upstream's `dev` and `production` branches intact.
- Work and review locally; push to this fork when requested. No PR was opened.
- Upstream's current contribution guide does not accept external PRs. Re-read
  it before proposing any contribution; do not assume this fork should be submitted.

The six Android implementation commits are preserved rather than squashed, so
upstream desktop history remains traceable. The obsolete keyboard-replacement
prototype exists only in history; the current app does not register an input method.

## Validation

From the repository root, with Android SDK 35 and JDK 17 configured:

```sh
android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Emulator checks and optional real-model PCM checks are documented in the Android
guide. The 0.6 change passed 27 JVM tests and 13 emulator UI checks; two optional
recognition/microphone checks were skipped in that run. Synthetic RMS verifies
animation, not live microphone input. Physical-device and T3 limitations remain
explicit in the feature map.
