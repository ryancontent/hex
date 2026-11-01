package ly.anoma.hex.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** All access, including callbacks, is on the main thread. Never creates a cloud recognizer. */
final class SpeechSession {
    interface Listener {
        void status(String text);
        void partial(String text);
        void result(String text, long finishMillis);
        void finished();
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private long generation;
    private long stoppedAt;
    private boolean stopping;
    private Runnable timeout;

    SpeechSession(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    boolean active() { return recognizer != null; }

    static Intent request(String language) {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    void start(String language) {
        if (active()) return;
        if (!new Preferences(context).enabled()) {
            listener.status("Turn on dictation in Hex’s Labs first.");
            listener.finished();
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.status("Open Hex setup and allow the microphone.");
            listener.finished();
            return;
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            listener.status("No on-device speech service. Open Hex setup for details.");
            listener.finished();
            return;
        }
        long token = ++generation;
        stoppedAt = 0;
        stopping = false;
        FinalTranscript transcript = new FinalTranscript();
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() { return generation == token && active(); }
                @Override public void onReadyForSpeech(Bundle params) {
                    if (current() && !stopping) listener.status("Listening… Speak, then tap Stop.");
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    if (current()) {
                        if (stoppedAt == 0) stoppedAt = SystemClock.elapsedRealtime();
                        stopping = true;
                        listener.status("Transcribing on-device…");
                        armTimeout(token, 15_000);
                    }
                }
                @Override public void onError(int error) {
                    if (!current()) return;
                    release();
                    listener.status(errorText(error));
                    listener.finished();
                }
                @Override public void onResults(Bundle results) {
                    if (!current()) return;
                    String text = transcript.complete(first(results));
                    long elapsed = stoppedAt == 0 ? -1 : SystemClock.elapsedRealtime() - stoppedAt;
                    release();
                    if (text.isBlank()) listener.status("No words heard. Try again.");
                    else listener.result(text, elapsed);
                    listener.finished();
                }
                @Override public void onPartialResults(Bundle results) {
                    if (current()) listener.partial(transcript.partial(first(results),
                            results.getBoolean(FinalTranscript.GOOGLE_FINAL_RESULT, false)));
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            listener.status("Starting on-device microphone…");
            armTimeout(token, 60_000);
            recognizer.startListening(request(language));
        } catch (RuntimeException failure) {
            release();
            listener.status("Couldn’t start on-device speech. Check microphone and language setup.");
            listener.finished();
        }
    }

    void stop() {
        if (!active() || stopping) return;
        stopping = true;
        stoppedAt = SystemClock.elapsedRealtime();
        listener.status("Transcribing on-device…");
        armTimeout(generation, 15_000);
        try { recognizer.stopListening(); }
        catch (RuntimeException failure) {
            release();
            listener.status("Speech service stopped. Try again.");
            listener.finished();
        }
    }

    void cancel() {
        release();
        listener.finished();
    }

    private void armTimeout(long token, long millis) {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = () -> {
            if (token != generation || !active()) return;
            release();
            listener.status("Recording timed out. Tap Dictate to try again.");
            listener.finished();
        };
        handler.postDelayed(timeout, millis);
    }

    private void release() {
        generation++;
        if (timeout != null) handler.removeCallbacks(timeout);
        SpeechRecognizer previous = recognizer;
        recognizer = null;
        if (previous != null) {
            try { previous.cancel(); } catch (RuntimeException ignored) { }
            try { previous.destroy(); } catch (RuntimeException ignored) { }
        }
    }

    private static String first(Bundle result) {
        ArrayList<String> matches = result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return matches == null || matches.isEmpty() ? "" : matches.get(0);
    }

    private static String errorText(int code) {
        return switch (code) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone in Hex setup.";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This speech service doesn’t support that language.";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Download the language pack in Hex setup, then try again.";
            case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No words heard. Try again.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Try again in a moment.";
            case SpeechRecognizer.ERROR_AUDIO -> "Microphone unavailable. End any call or other recording and retry.";
            default -> "On-device speech failed (" + code + "). Try again; no cloud fallback was used.";
        };
    }
}
