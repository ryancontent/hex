# Hex for Android — floating dictation proof of concept

An independent native Android experiment for a Pixel 8 Pro (Android 12+).
Keep Gboard or your preferred keyboard. Hex shows a floating button only when a
supported text field has input focus **and its keyboard is open**, using an
explicitly enabled Accessibility service. Version `0.5.0-poc` makes the H bubble
the direct recorder and lets you drag it freely across the screen. Its saved
position is not snapped to an edge. The APK does not register an input method.

## Try it

Install the debug APK, open **Hex POC**, then:

1. Turn on **Labs → Experimental dictation** (off on a fresh install).
2. **Allow microphone**. Use **Check readiness** to inspect the on-device speech
   service and installed offline languages. Start with `en-US`, or save another
   BCP-47 language tag. **Prepare offline language** requests an Android-managed
   model download; recheck readiness after it finishes.
3. Read the Accessibility explanation and tap **Enable floating dictation**.
   Under Android Accessibility settings, leave **Use Hex floating dictation**
   **on** and turn **Hex floating dictation shortcut** **off**. The shortcut is
   Android's extra H launcher: tapping it toggles the service, so it can create
   the unwanted H → record-button flow. Hex's own H is the recorder. The app
   cannot change that system shortcut selection for you.
   A sideloaded APK can encounter Android's restricted-settings protection:
   if blocked, open Hex POC's App info, use the menu's **Allow restricted
   settings** option if offered, then return to Accessibility settings.
4. Open a text field in another app and show its keyboard. Hex's green **H**
   appears above the keyboard on first use. **Drag it anywhere in the usable
   screen**, including over the keyboard if you prefer. Its position is remembered
   when hidden, when the service restarts, and across launches. There is no side
   docking. **Reset bubble position** in Hex restores the initial location.
   Dismiss the keyboard or leave the field and the H disappears entirely.
5. **Tap H once** to start and **tap again** to finish. Or **hold still** until
   it turns red (350 ms), speak, and **release** to finish. A short vibration
   when the microphone is ready respects Android's haptic settings.
   Movement before the hold threshold is a drag, with no recording. Moving after
   a hold starts, or dragging a locked recording/processing attempt, cancels that
   attempt without insertion and moves the bubble instead.
   The same button changes from H to a red stop symbol while recording,
   then an ellipsis while processing. It never expands or adds another floating
   control. A brief checkmark confirms insertion; an amber exclamation and a
   system toast explain a failure.
6. Final words are inserted at the cursor, replacing only selected text. If you
   change fields, close the keyboard, edit text, move the cursor, or lock the screen during capture,
   Hex cancels rather than applying the result elsewhere. Password fields hide
   the button. Disable Labs to hide it everywhere, or disable the Accessibility
   service to disconnect Hex from other apps.

Language setup may use the network. Dictation exclusively uses Android's
`createOnDeviceSpeechRecognizer`; there is no default/cloud recognizer fallback
and no Internet permission. Hex writes no recordings or transcripts. The speech
service and models belong to Android. Try with Wi-Fi and mobile data off after
the offline language has been installed.

## Accessibility and limits

Accessibility is needed to detect the focused editable field, draw a
`TYPE_ACCESSIBILITY_OVERLAY`, and use `ACTION_SET_TEXT` to insert words while
preserving the surrounding text. No clipboard or keyboard switching is used.
The service examines the focused field's emptiness and cursor to determine
eligibility. **After you start recording**, it snapshots that field's text and
selection, retains them only until that job finishes/cancels, and checks them
again immediately before insertion. It does not log or send them. A microphone
foreground-service notification is used only while dictation is active.

Only visible, enabled, non-password editable fields exposing `ACTION_SET_TEXT`
and a valid selection are supported. An empty/hint-only field has one possible
insertion position (zero), even when Android reports no cursor. Some custom editors/WebViews do not expose
these APIs, so the bubble can be absent or insertion can be refused. Accessibility
text replacement is not an atomic editor transaction: the fresh focus/text/
selection check reduces stale insertion risk, but an app edit racing the final
action cannot be made atomic with this API. Large fields require replacing their
full accessible text; no universal rich-text preservation is claimed.

No Parakeet, desktop sync, always-listening microphone, or Play Store release.
Physical Pixel microphone behavior, latency, battery use, Bluetooth routing,
and compatibility with the user's apps remain to be checked.

The recording only finishes on your stop tap or held release. Android's
end-of-speech notification no longer disables that stop gesture. If the speech
service returns a final result early, Hex holds it until you finish instead of
inserting while your finger is still down. Ordinary partials are never treated
as final text. Once you stop, the button shows an ellipsis while waiting for the
final result. Recording and processing use the same button's color/symbol, with
no separate status panel or transcript preview. Accessibility state descriptions
retain the phase/error information for screen readers.

## Build and install

JDK 17, Android SDK platform 35, build-tools 35.0.0, and platform-tools are needed.
Set `JAVA_HOME` and `ANDROID_HOME`, or set `sdk.dir` in an untracked
`local.properties`. The Gradle wrapper pins 8.11.1 with a SHA-256 checksum.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ly.anoma.hex.android.poc/ly.anoma.hex.android.SetupActivity
```

Package: `ly.anoma.hex.android.poc`. Updating the earlier debug APK preserves
its settings and removes the old keyboard component. Android chooses an available
keyboard if the removed Hex keyboard was previously selected; Hex does not set
the default input method. Uninstall Hex POC to remove it. Android-managed speech
models may remain.

## SDK verification

```sh
./gradlew :app:connectedDebugAndroidTest
```

`DeviceSmokeTest` preserves the selected input method and existing Accessibility
settings. It enables Hex's service **on the test device**, keeps other
Accessibility services running, opens an editor in a separate APK/process, and
tests actual overlay tap/hold gestures and cursor insertion. A test-APK-only
recognition service supplies known results through Android's temporary test
hook, reset in cleanup (and automatically expired after 60 seconds). These UI
checks are not real-microphone or inference proof. JVM tests cover gestures,
selection splicing, generations/cancellation, and final-text handling.

An optional `DeviceSmokeTest#realOnDeviceServiceCanOpenEmulatorMicrophoneFromOverlay`
check, enabled with `-Pandroid.testInstrumentationRunnerArguments.microphoneProof=true`,
uses the real service to reach listening from the overlay over the external
editor, then cancels. Run it on a prepared emulator with host audio disabled;
it is virtual-microphone startup proof, not a physical recording or accuracy check.

For a separate real-model test, prepare `en-US`, disable emulator Wi-Fi and mobile
data, then explicitly opt in:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ly.anoma.hex.android.OfflineSpeechTest \
  -Pandroid.testInstrumentationRunnerArguments.offlineProof=true
```

It asserts no active network and feeds synthetic PCM to the real on-device
recognizer via the public audio-source API. It bypasses the microphone. The
observed transcript and proof limits are in
[`docs/features/android.md`](../docs/features/android.md).

To exercise actual offline model results through the production `SpeechSession`
and overlay insertion together, use the prepared emulator with networking off:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ly.anoma.hex.android.DeviceSmokeTest#realOnDevicePcmIsInsertedThroughProductionSessionOnStop \
  -Pandroid.testInstrumentationRunnerArguments.pipelineProof=true
```

A test-only audio-source service forwards the fixture to the platform's actual
on-device recognition component and returns its unmodified callbacks. Both
tap/stop and hold/release must insert the recognized phrase while preserving
surrounding text. This joins recognition and insertion proof but still bypasses
the physical microphone; no canned transcript is supplied by that test service.
