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
    └── Use Hex floating dictation: on; Hex floating dictation shortcut: off
        └── Android's extra shortcut H toggles the service; Hex's own nested-hexagon button records

Focus a supported text field with keyboard open  // android.dictation
├── Hex bubble -> first placed above keyboard; user can drag it freely
│   ├── Tap Hex once -> record; next tap -> finish (no intermediate control)
│   ├── Hold still >= 350 ms -> record; release -> finish
│   └── Drag -> reposition; cancel any current capture/processing without insertion
├── One fixed-size button: Hex mark -> warm listening dots -> blue processing flow
│   └── Same bounds across phases, except deliberate dragging; no second control
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
shows a checkmark in that button; failures use a coral exclamation there and a
short system toast. Phase/error text remains an Accessibility state description,
not another floating view. A normal tap starts on release. A stationary hold
starts after 350 ms, with a haptic when the recognizer is ready. Crossing Android's
touch-slop threshold first selects dragging and never starts an idle recording.
Dragging after a hold begins, during locked recording, or during processing
cancels that attempt and moves the H; release does not insert. Changing fields
also invalidates a pending hold before it can open the microphone.

```ts
Move H                                  // android.bubble-position
├── Drag to any usable screen coordinate, including the keyboard region
├── Release -> save normalized x/y in app preferences; no edge snapping
├── Hide/show or service start -> restore saved position
├── Screen size/orientation change -> rescale, clamp clear of bars/cutout
└── Reset bubble position in Hex -> return above the keyboard
```

Position math lives in [`BubblePosition`](../../android/app/src/main/java/ly/anoma/hex/android/BubblePosition.java)
and storage in [`Preferences`](../../android/app/src/main/java/ly/anoma/hex/android/Preferences.java).
Android's Accessibility shortcut is a separate system-owned bubble. Hex does
not request or reconfigure that shortcut; setup explains leaving the service on
and its shortcut off. Removing that extra H requires the user to change the
shortcut setting; the APK alone cannot silently remove it.

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

## Version 0.5 direct H and placement verification

On November 1, 2026, the user reported dictation working on their phone, with
the remaining problem being a two-stage H/record-button interaction. The app's
0.4 overlay was a non-draggable dot, so the separate draggable H is consistent
with Android's service-toggle shortcut rather than Hex's recorder. The phone's
shortcut settings were not inspected directly. Setup now explains **Use Hex
floating dictation on; Hex floating dictation shortcut off**. Hex does not write
those system settings itself.

The 0.5 build passed **27 JVM tests and 16 emulator instrumentation tests**,
including the real-model supplied-PCM tap/hold flow, offline recognizer, and
virtual-microphone startup checks. Build, signature verification, and lint
passed (no lint errors; existing dependency/localization warnings remain).
The new UI check verifies the idle label is H, dragging it to the screen center
does not dock to either edge or insert text, its saved position returns after
Labs hides/shows it, and one tap on that moved H starts recording directly.
The single-control/bounds and recording/processing cancellation checks still
pass. Position unit tests cover persistence, display/cutout clamping, orientation
rescaling, invalid stored floats, and small displays. Gesture tests distinguish
pending taps, stationary holds, drag cancellation, and interrupted touches.

The user's successful dictation report is separate from the automated proof:
these runs do not independently verify the updated gesture timing, haptic,
placement comfort, or removal of the system shortcut on their physical Pixel.

## Version 0.6 desktop branding

The Android floating control adapts the desktop nested Hex mark and Dot scope
([desktop palette](../../src/rune_hud/dot_scope/scope.rs),
[desktop glyph proportions](../../src/rune_ui/hero.wgsl)) to a single fixed-size
Canvas button ([HexBubble](../../android/app/src/main/java/ly/anoma/hex/android/HexBubble.java)).
Idle shows the mark on a dark plate; listening shows warm pearl braided dots;
processing uses blue flowing dots; errors use coral. The app icon and setup
palette use the same branding. Existing tap, hold, free drag and visibility rules
remain in effect. This is an Android adaptation, not the desktop GPU renderer.

SpeechSession forwards current recording RMS callbacks to the button. A smoothed
envelope decays after 250 ms without a fresh callback. With no input the trace
stays flat; its motion is not evidence of microphone audio. Android's disabled
animation setting uses a static scope. Drawing requests stop when idle or
detached; no second overlay or recording view is created.

T3's agent-device UI automation was observed suppressing the Accessibility
service: Android listed Hex enabled but unbound with Ui Automation active;
closing our agent-device session restored the bound service. For manual Device
panel tests, leave that automation session closed. T3 currently launches the
emulator with `-no-audio`, so live host microphone testing is unavailable there.

Version 0.6 verification on November 1, 2026: assembly, 27 JVM tests and lint
passed (no lint errors). The emulator DeviceSmokeTest suite passed 13 checks;
the optional real-model and virtual-microphone checks were skipped in this run.
The single-control check now compares two recording frames with test-only RMS
callbacks and confirms the scope animates within unchanged bounds. The fixture
RMS is synthetic and is not live microphone proof. Idle, listening and processing
screenshots were visually reviewed. Test setup now requests a fresh Accessibility
binding after instrumentation restarts an already-enabled app. No desktop Rune
drawing changed, so desktop golden images are unaffected.
