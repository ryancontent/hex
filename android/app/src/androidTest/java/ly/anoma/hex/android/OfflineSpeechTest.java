package ly.anoma.hex.android;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit real-model proof: prepare en-US, disable networking, then opt in with -e offlineProof true. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 33)
public class OfflineSpeechTest {
    @Test public void realOnDeviceRecognizerTranscribesPcmFixture() throws Exception {
        assumeTrue("Optional real-model proof requires an installed language and disabled networking",
                InstrumentationRegistry.getArguments().getString("offlineProof", "false").equals("true"));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.ACCESS_NETWORK_STATE");
        try {
            assertNull("Disable emulator Wi-Fi and mobile data before running this proof",
                    context.getSystemService(ConnectivityManager.class).getActiveNetwork());
        } finally { instrumentation.getUiAutomation().dropShellPermissionIdentity(); }
        instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),
                "android.permission.RECORD_AUDIO");
        instrumentation.startActivitySync(new Intent(context, SetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue("An on-device recognition service is required",
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context));
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>("");
        AtomicReference<SpeechRecognizer> recognizer = new AtomicReference<>();
        FinalTranscript transcript = new FinalTranscript();
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        byte[] audio;
        try (var input = instrumentation.getContext().getAssets().open("quick-brown-fox.pcm")) {
            audio = input.readAllBytes();
        }
        Thread feed = new Thread(() -> {
            try (var output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                for (int offset = 0; offset < audio.length; offset += 640) {
                    output.write(audio, offset, Math.min(640, audio.length - offset));
                    Thread.sleep(20);
                }
                // Give the service's endpointer a clear two-second quiet tail.
                for (int i = 0; i < 100; i++) { output.write(new byte[640]); Thread.sleep(20); }
            } catch (Exception failure) {
                Log.i("HexOfflineProof", "Audio pipe closed: " + failure.getClass().getSimpleName());
            }
        }, "hex-proof-pcm");
        try {
            instrumentation.runOnMainSync(() -> {
                SpeechRecognizer speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                recognizer.set(speech);
                speech.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { Log.i("HexOfflineProof", "Ready"); }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float value) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onError(int code) { error.set("Speech error " + code); complete.countDown(); }
                    @Override public void onResults(Bundle bundle) {
                        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        result.set(transcript.complete(matches == null || matches.isEmpty() ? "" : matches.get(0)));
                        Log.i("HexOfflineProof", "Actual transcript: " + result.get());
                        complete.countDown();
                    }
                    @Override public void onPartialResults(Bundle bundle) {
                        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        transcript.partial(matches == null || matches.isEmpty() ? "" : matches.get(0),
                                bundle.getBoolean(FinalTranscript.GOOGLE_FINAL_RESULT, false));
                    }
                    @Override public void onEvent(int type, Bundle bundle) { }
                });
                Intent request = SpeechSession.request("en-US")
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                speech.startListening(request);
            });
            feed.start();
            assertTrue("No result within 30 seconds", complete.await(30, TimeUnit.SECONDS));
            assertEquals("Real service rejected the fixture: " + error.get(), "", error.get());
            String normalized = result.get().toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").trim();
            // Feasibility check, not a zero-WER claim: Google's service hears the
            // synthetic opening article as either "the" or "a" on this fixture.
            assertTrue("Unexpected actual transcript: " + result.get(),
                    normalized.matches("(the|a) quick brown fox jumps over the lazy dog"));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (recognizer.get() != null) recognizer.get().destroy();
            });
            feed.interrupt();
            pipe[0].close();
            pipe[1].close();
            feed.join(1_000);
        }
    }
}
