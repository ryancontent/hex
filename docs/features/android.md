# Android phone dictation (proof of concept)

This standalone Android app/keyboard targets a personal Pixel 8 Pro experiment;
it is not part of the macOS, Linux, or Windows application. Android 12+ is the
compiled minimum. No Play Store release, physical Pixel verification, or
Parakeet backend is claimed. Build and sideload instructions:
[`android/README.md`](../../android/README.md).

```ts
Install the debug APK                     // android.setup
├── Open Hex POC -> Labs -> Experimental dictation (off by default)
├── Allow microphone -> Android's runtime permission prompt
├── Check readiness -> device, permission, service, installed offline languages
│   ├── No on-device service -> say unavailable; no default recognizer fallback
│   └── Missing language -> Prepare offline language -> Android-managed download
├── Dictate here -> partial preview -> final words in the practice field
└── Enable Hex keyboard -> Android settings -> Choose keyboard

Focus a text field with Hex selected      // android.dictation
├── Dictate -> recheck Labs and microphone permission -> on-device recognizer
│   ├── Password -> refuse
│   ├── Partial -> preview only
│   ├── Stop or Android endpointer -> final -> insert once at the current cursor
│   └── Missing language, busy service, audio error, or timeout -> explicit retry
├── Cancel / field change / keyboard hidden -> cancel; invalidate late results
└── Keyboards -> Android's picker -> return to the user's normal keyboard
```

Sources: [`SetupActivity`](../../android/app/src/main/java/ly/anoma/hex/android/SetupActivity.java),
[`SpeechSession`](../../android/app/src/main/java/ly/anoma/hex/android/SpeechSession.java),
[`HexKeyboard`](../../android/app/src/main/java/ly/anoma/hex/android/HexKeyboard.java),
[`EditorSession`](../../android/app/src/main/java/ly/anoma/hex/android/EditorSession.java),
[`FinalTranscript`](../../android/app/src/main/java/ly/anoma/hex/android/FinalTranscript.java),
and [`AndroidManifest.xml`](../../android/app/src/main/AndroidManifest.xml).

Google's on-device service can send `final_result: true` in a partial callback,
then an empty completion. Hex retains only these explicitly confirmed utterances
as a fallback for an empty completion; an ordinary partial is never inserted.
The normal completion text, when present, remains authoritative.

The manifest requests microphone access and no Internet permission. The only
recognizer factory is `createOnDeviceSpeechRecognizer`; preparation is a separate
explicit action that may download a language. Hex does not persist audio or
transcripts. The platform speech service owns recognition and model storage.

Checks: [`EditorSessionTest`](../../android/app/src/test/java/ly/anoma/hex/android/EditorSessionTest.java)
covers invalidation on editor changes/cancel, duplicate and stale final results,
and insertion spacing. [`FinalTranscriptTest`](../../android/app/src/test/java/ly/anoma/hex/android/FinalTranscriptTest.java)
covers final-marked partials, unfinished tails, and authoritative completion.
[`DeviceSmokeTest`](../../android/app/src/androidTest/java/ly/anoma/hex/android/DeviceSmokeTest.java)
checks actual app/IME startup, Labs opt-in, and the manifest/offline-request
contract. Its insertion check uses a temporary test-only recognition service,
not inference. [`OfflineSpeechTest`](../../android/app/src/androidTest/java/ly/anoma/hex/android/OfflineSpeechTest.java)
is an explicit opt-in real-model check over synthetic PCM with no active network.

## Observed verification

On November 1, 2026, on an Apple silicon Mac with Android SDK emulator 37.2.13,
Pixel 8 Pro device profile, Android 15 / API 35 Google Play ARM64 system image:

- Debug APK build, all nine JVM tests, and Android lint passed (no lint errors;
  English-only strings and dependency-version recommendations remain warnings).
- All four `DeviceSmokeTest` instrumentation checks passed, including actual
  final-text insertion through the installed keyboard and a real `InputConnection`.
  That insertion used the test-only recognition service; it was not a microphone
  recording or real-model inference. The default run skipped the separate
  real-model test as intended.
- Real-model `OfflineSpeechTest` passed after the app's preparation flow downloaded
  the `en-US` language. Both emulator Wi-Fi and mobile data were disabled; the
  test asserted `ConnectivityManager.getActiveNetwork()` was null.
- Actual recognition from the synthetic fixture was **“A quick brown fox jumps
  over the lazy Dog”**, for reference speech **“The quick brown fox jumps over the
  lazy dog.”** This establishes offline recognition, with an opening-article
  substitution; it is not a zero-error accuracy claim. The test permits that
  article substitution while requiring the rest of the phrase.

The PCM-source test bypasses a physical microphone. It does not establish that
the Pixel has the model installed or measure its speed, battery use, or quality.
Remaining physical proof: offline speech on the Pixel with networking disabled,
latency, microphone interruptions, routing, and insertion into third-party apps.
