package ly.anoma.hex.android;

import android.content.ComponentName;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

/** Test-only audio source, forwarding actual on-device model callbacks through the production client. */
public final class AudioFixtureRecognitionService extends RecognitionService {
    private SpeechRecognizer engine;
    private ParcelFileDescriptor[] pipe;
    private Thread feeder;

    @Override protected void onStartListening(Intent request, Callback callback) {
        try { callback.readyForSpeech(new Bundle()); } catch (RemoteException disconnected) { }
    }
    @Override protected void onStopListening(Callback callback) {
        if (engine != null) return;
        try {
            int id = getResources().getIdentifier("config_defaultOnDeviceSpeechRecognitionService", "string", "android");
            ComponentName configured = ComponentName.unflattenFromString(getString(id));
            if (configured == null) throw new IllegalStateException("No configured on-device service");
            // Explicit component avoids recursing through the temporary test service.
            // This is the platform's actual on-device component; networking is off.
            engine = SpeechRecognizer.createSpeechRecognizer(this, configured);
            pipe = ParcelFileDescriptor.createPipe();
            byte[] audio;
            try (var input = getAssets().open("quick-brown-fox.pcm")) { audio = input.readAllBytes(); }
            engine.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float value) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    try { callback.endOfSpeech(); } catch (RemoteException disconnected) { }
                }
                @Override public void onError(int code) {
                    try { callback.error(code); } catch (RemoteException disconnected) { }
                }
                @Override public void onResults(Bundle result) {
                    try { callback.results(result); } catch (RemoteException disconnected) { }
                }
                @Override public void onPartialResults(Bundle result) {
                    try { callback.partialResults(result); } catch (RemoteException disconnected) { }
                }
                @Override public void onEvent(int type, Bundle params) { }
            });
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            engine.startListening(request);
            ParcelFileDescriptor writer = pipe[1];
            feeder = new Thread(() -> {
                try (var output = new ParcelFileDescriptor.AutoCloseOutputStream(writer)) {
                    for (int offset = 0; offset < audio.length; offset += 640) {
                        output.write(audio, offset, Math.min(640, audio.length - offset));
                        Thread.sleep(20);
                    }
                    for (int i = 0; i < 100; i++) { output.write(new byte[640]); Thread.sleep(20); }
                } catch (Exception cancelled) { /* Cancellation closes the pipe. */ }
            }, "hex-fixture-audio");
            feeder.start();
        } catch (Exception unavailable) {
            try { callback.error(SpeechRecognizer.ERROR_CLIENT); } catch (RemoteException disconnected) { }
        }
    }
    @Override protected void onCancel(Callback callback) { close(); }
    private void close() {
        if (engine != null) { engine.destroy(); engine = null; }
        if (feeder != null) feeder.interrupt();
        if (pipe != null) for (var descriptor : pipe) {
            try { descriptor.close(); } catch (Exception ignored) { }
        }
        pipe = null;
    }
    @Override public void onDestroy() { close(); super.onDestroy(); }
}
