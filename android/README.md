# Hex for Android — Pixel proof of concept

An independent native Android app and voice keyboard, aimed first at a Pixel 8
Pro. Android 12 / API 31 or later is required. This is a sideloadable experiment,
not an Android release or a port of the desktop runtime.

## What this proves

- An app with a practice field and a system-wide voice input method.
- Explicit Labs opt-in, off on a fresh install, checked at recognition start.
- Microphone permission requested in the app, never implicitly from a keyboard.
- `SpeechRecognizer.createOnDeviceSpeechRecognizer` exclusively. If Android has
  no on-device service or selected language, report it; never create the default
  potentially network-backed recognizer. The APK has no Internet permission.
- Partial text shown in Hex; only final text committed through `InputConnection`.
  Google's explicitly final-marked partials are retained until completion; an
  ordinary partial is never promoted when completion contains no text.
- Cancel, keyboard hiding, and editor changes invalidate the recording. Late
  results cannot be inserted into a newly focused field. Password fields refuse
  dictation. Recognition owns audio capture; Hex writes no audio or transcript
  files and has no transcript history.
- Recording has a 60-second deadline; finishing has a 15-second deadline. Android
  may endpoint sooner. The status reports stop-to-result time when available.

The existing iPhone Swift/Core ML code is not linked. This does **not** establish
Parakeet quality, Pixel performance, long-form recording, background recording,
Bluetooth behavior, formatting styles, desktop sync, or broad Android support.
The speech service and its installed languages are supplied by the device.

## Build

Install JDK 17 and the Android SDK (platform 35, build-tools 35.0.0,
platform-tools). Set `ANDROID_HOME` to the SDK directory, or set `sdk.dir` in an
untracked `local.properties`. The Gradle wrapper pins 8.11.1 and verifies its
distribution with SHA-256.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# A connected emulator is sufficient for setup/keyboard smoke checks:
./gradlew :app:connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Package: `ly.anoma.hex.android.poc` (separate from other Hex builds).

## Try it on the Pixel

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ly.anoma.hex.android.poc/ly.anoma.hex.android.SetupActivity
```

1. Turn on **Labs → Experimental dictation** and **Allow microphone**.
2. Start with `en-US` (or save another BCP-47 language tag). **Check readiness**
   lists installed offline languages when the device supports that query.
3. If necessary, **Prepare offline language** requests a download through
   Android. Model preparation can use the network; a request is not proof that
   the model has finished downloading. Recheck readiness afterward.
4. Tap **Dictate here**, speak, and tap **Stop** if Android has not already
   detected the end of the sentence. Confirm the words appear in the field.
5. Tap **Enable Hex keyboard**, enable Hex in Android settings, then use
   **Choose keyboard**. Open a text field in another app and tap **Dictate**.
   Use **Keyboards** in Hex to switch back to Gboard.
6. With the offline language ready, turn off Wi-Fi and mobile data and repeat.
   This is the physical offline-recognition proof, not just an API flag check.
7. Cancel a recording, switch apps or fields mid-recording, and hide the
   keyboard. Confirm no late text appears in another field.

To remove: switch back to Gboard and uninstall Hex POC. It creates no external
files. Android may keep language packs managed by its own speech service.

## Emulator and proof limits

SDK emulators may lack the on-device service found on a Pixel. In that case the
correct outcome is an unavailable-service message, not a cloud fallback.
`DeviceSmokeTest` exercises the installed app and real input method, Labs gate,
and manifest/request contract. Its insertion test temporarily substitutes a
recognition service from the **test APK only** through Android's test-service
hook, sends a known final result, and confirms that the real IME commits it to
the practice field. That service does not recognize audio and is reset in a
`finally` block (Android also expires it after 60 seconds). JVM tests cover editor
generations, cancellation, duplicate results, insertion spacing, and final-text
handling.

`OfflineSpeechTest` is a separate opt-in **real-model** check. Prepare `en-US`
through the app, disable the emulator's Wi-Fi and mobile data, then run:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ly.anoma.hex.android.OfflineSpeechTest \
  -Pandroid.testInstrumentationRunnerArguments.offlineProof=true
```

It asserts there is no active network, feeds a synthetic 16 kHz PCM fixture to
Android's actual on-device service through the public audio-source API, and
requires the expected phrase. The opening article may be recognized as `the`
or `a`; the actual observed transcript is recorded in the feature map. The
default test run skips this check because models and service implementations
are device-managed. It proves offline inference from supplied audio, not the
physical microphone path, Pixel speed, or general accuracy.

Observed runs and remaining proof belong in
[`docs/features/android.md`](../docs/features/android.md).
