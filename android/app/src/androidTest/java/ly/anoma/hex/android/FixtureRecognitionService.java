package ly.anoma.hex.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.List;

/** Test APK only. Exercises real system binding and IME delivery without pretending to recognize audio. */
public final class FixtureRecognitionService extends RecognitionService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override protected void onStartListening(Intent request, Callback callback) {
        try { callback.readyForSpeech(new Bundle()); }
        catch (RemoteException disconnected) { return; }
        handler.postDelayed(() -> {
            try {
                Bundle partial = new Bundle();
                partial.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                        new ArrayList<>(List.of("Hex keyboard works.")));
                partial.putBoolean("final_result", true);
                callback.partialResults(partial);
                callback.results(new Bundle());
            } catch (RemoteException disconnected) { /* The editor may have gone away. */ }
        }, 750);
    }
    @Override protected void onStopListening(Callback callback) { }
    @Override protected void onCancel(Callback callback) { handler.removeCallbacksAndMessages(null); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
