# Android floating dictation (proof of concept)

A standalone Android 12+ experiment for a personal Pixel 8 Pro. It keeps the
user's keyboard and adds an opt-in Accessibility overlay; no Play Store release,
Parakeet backend, or physical Pixel coverage is claimed. Setup/build instructions:
[`android/README.md`](../../android/README.md).

```ts
Open Hex POC                             // android.setup
├── Labs -> Experimental dictation (off by default)
├── Allow microphone -> Android runtime permission prompt
├── Check readiness -> service and installed offline languages
│   └── Prepare offline language -> explicit Android-managed download if needed
└── Enable floating dictation -> explanation -> Android Accessibility settings
    └── Enable Hex floating dictation; leave Gboard / current keyboard selected

Focus a supported text field with keyboard open  // android.dictation
├── Small floating button -> above the keyboard, near the right edge
│   ├── Tap -> record; next tap -> finish
│   ├── Hold >= 350 ms -> record; release -> finish
│   └── Slide off the same button -> cancel (recording or processing)
├── One fixed button: green dot -> red stop symbol -> processing ellipsis
│   └── Same bounds throughout; no separate recording icon, panel, or cancel button
├── On-device recognizer -> confirmed final text (no floating transcript preview)
│   └── Same field, text and selection -> splice at cursor / replace selection
├── Changed field/text/selection, closed keyboard, lock screen, interrupted service -> cancel
├── Keyboard closed / password / unsupported editor / no focused editable field -> hide
└── Labs off -> cancel and hide; Accessibility off -> disconnect service
```

Sources: [`FloatingDictationService`](../../android/app/src/main/java/ly/anoma/hex/android/FloatingDictationService.java),
[`RecordGesture`](../../android/app/src/main/java/ly/anoma/hex/android/RecordGesture.java),
[`FieldInsertion`](../../android/app/src/main/java/ly/anoma/hex/android/FieldInsertion.java),
[`SpeechSession`](../../android/app/src/main/java/ly/anoma/hex/android/SpeechSession.java),
[`FinalTranscript`](../../android/app/src/main/java/ly/anoma/hex/android/FinalTranscript.java),
[`SetupActivity`](../../android/app/src/main/java/ly/anoma/hex/android/SetupActivity.java),
and the [`manifest`](../../android/app/src/main/AndroidManifest.xml).

The service does not alter the default input method or use the clipboard. It
examines only the focused editor's eligibility, emptiness and selection. It
snapshots the full field text after the user starts a job and retains it only until completion/cancellation,
then refreshes and validates focus, text, and selection before `ACTION_SET_TEXT`.
The returned action and a cursor-restoration action can fail. This is not an
atomic editor transaction; an app edit racing the final action remains a limit.
Custom editors must expose editable nodes, selection, and text-setting support;
rich-text preservation is unproven. Disabling Labs gates the actual capture and
removes the overlay, not just a settings row.

The app requests microphone and microphone foreground-service permissions, not
Internet permission. It uses only `createOnDeviceSpeechRecognizer`. Preparation
is an explicit action that may download a language; recording is scoped to the
gesture/toggle, a 60-second deadline, and a 15-second finish deadline. No audio or
transcript files are written. Google's `final_result: true` partials are retained
for an empty completion; ordinary partials are never promoted to inserted text.
End-of-speech does not mean the user requested stop. Explicit stop/release still
calls `stopListening` after that callback. A terminal result arriving early is
held until the user stops; it is not inserted mid-hold. A hint-only empty editor
has a uniquely known cursor at zero even if Accessibility reports `-1`.
The single floating button is 60 dp in every phase. Successful insertion briefly
shows a checkmark in that button; failures use an amber exclamation there and a
short system toast. Phase/error text remains an Accessibility state description,
not another floating view. A locked-recording stop commits on pointer release,
so sliding outside the button can cancel before insertion. A normal tap during
processing does not start another job; sliding off can still cancel it.

## Checks and observed proof

[`DeviceSmokeTest`](../../android/app/src/androidTest/java/ly/anoma/hex/android/DeviceSmokeTest.java)
exercises the installed Accessibility service and overlay over a separate
editor APK/process: tap/tap, hold/release, cursor insertion preserving existing
text, field-change cancellation, password hiding, Labs-off hiding, and keeping
the input method unchanged. A temporary test-only recognition service supplies
results; these checks do not establish microphone capture or acoustic accuracy.
JVM tests cover selection splicing, gesture semantics, stale/duplicate jobs, and
final-result handling.

The overlay revision passed six emulator UI checks and all 16 JVM tests on
November 1, 2026, with the API 35 Google Play ARM64 image. Tap/tap and hold/release
both inserted `lovely` into `Hello world` at its cursor to produce `Hello lovely
world`, while Gboard stayed selected. Field-change cancellation, password hiding,
Labs-off hiding, the Labs default, and the manifest contract were checked.
The APK build and lint passed (no lint errors; localization/dependency warnings
remain). A separate explicit `microphoneProof` run used Google's real on-device
service from the overlay while the external editor was foreground: it reached
the listening callback and cancellation left the field untouched. The emulator
was launched with host audio disabled, so this proves virtual-microphone/service
startup, not physical audio capture or transcription of spoken words.
The final combined run passed all eight instrumentation tests (six UI/contract
checks plus the real-service virtual-microphone and real-model PCM checks), all
16 JVM tests, APK assembly/signature verification, and lint with no errors.

The earlier real-model `OfflineSpeechTest` passed on November 1, 2026, with Android
SDK emulator 37.2.13, an Android 15 / API 35 Google Play ARM64 image and a Pixel 8
Pro device profile. After Android downloaded `en-US`, Wi-Fi and mobile data were
disabled and the test asserted no active network. Actual output from synthetic
PCM was **“A quick brown fox jumps over the lazy Dog”**, for reference **“The quick
brown fox jumps over the lazy dog.”** The test permits that article substitution
while requiring the remaining phrase; this is not a zero-error accuracy claim.
The speech backend remains the same in the overlay revision. This PCM-source
check bypasses the physical microphone and does not prove Pixel speed or quality.

Remaining physical proof: offline Pixel microphone capture, routing, latency,
battery use, interruptions, gesture comfort, and insertion in the user's apps.
Android may require **Allow restricted settings** in App info before a sideloaded
Accessibility service can be enabled. The desktop Rune UI and its goldens do not
cover this independent Android surface.

## Version 0.3 regression verification

The earlier overlay tests omitted the service's end-of-speech-before-stop
sequence and keyboard dismissal while a field stays focused. Both added
regressions failed against 0.2 before the fixes. The expanded tests also caught
Hex's own practice field becoming invisible behind the IME; the form now insets
the scroll viewport and scrolls the focused editor above the keyboard. Empty
hint-only selection is normalized to zero rather than treating the hint as text.

On November 1, 2026, the final 0.3 run passed all **17 JVM tests and 12 emulator
instrumentation tests**, with the three model-dependent flags enabled
(`microphoneProof`, `offlineProof`, `pipelineProof`). APK assembly and lint passed
with no lint errors. The checks cover both gesture modes after end-of-speech,
holding an early final until explicit stop, Hex's blank practice field, keyboard
dismissal/reappearance, non-editor and password hiding, cancellation, Labs gates,
and preservation of Gboard.

The new real-model joined check uses
[`AudioFixtureRecognitionService`](../../android/app/src/androidTest/java/ly/anoma/hex/android/AudioFixtureRecognitionService.java)
only as a supplied-PCM audio source, forwarding actual platform on-device ASR
callbacks through the production `SpeechSession` and Accessibility insertion.
Both tap/stop and hold/release inserted **“Hello A quick brown fox jumps over the
lazy Dog world”** from `Hello world` with its cursor after `Hello`. Networking
was disabled and the test asserted no active network. Unlike the original
separate checks, this establishes model-result-to-editor delivery in one flow.
It still bypasses live microphone acquisition.

A separate local SDK gRPC virtual-microphone injection experiment did **not**
produce a final usable transcript: only the partial “The quick” appeared before
no-match. Enabling the emulator's host-audio backend also hung its QEMU loop.
That experiment is not passing microphone-to-field proof, and its cause was not
established. Physical voice capture and compatibility with Messages/T3 Code on
the user's Pixel remain unverified by these automated runs; the supplied-PCM
check must not be presented as proof of that path.

## Version 0.4 single-button verification

On November 1, 2026, the final 0.4 build passed all **19 JVM tests and 15 emulator
instrumentation tests**, including the real-model supplied-PCM tap/hold flow,
offline recognizer test, and real-service virtual-microphone startup/cancellation.
APK assembly and lint passed with no lint errors. The added UI check counts
exactly one clickable control in exactly one Hex overlay window and verifies
identical visible bounds while idle, recording, processing, and after insertion.
It also verifies the old status panel and cancel control are absent. Separate
tests cancel locked recording and pending processing by sliding off that same
button. A delayed test-only recognizer keeps processing pending so cancellation
is tested before a final result can commit.

Idle, recording, and processing emulator screenshots were visually reviewed:
one green dot, red stop symbol, and grey ellipsis respectively, all in the same
circular button above Gboard. Android's system microphone privacy indicator
remains system-owned; Hex does not create a second recording overlay. These UI
checks do not expand the physical-microphone proof stated above.
