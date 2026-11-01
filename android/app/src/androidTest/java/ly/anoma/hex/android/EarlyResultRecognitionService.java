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

/** A standard service can finish before the user releases a hold or taps Stop. */
public final class EarlyResultRecognitionService extends RecognitionService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override protected void onStartListening(Intent request, Callback callback) {
        try { callback.readyForSpeech(new Bundle()); } catch (RemoteException disconnected) { return; }
        handler.postDelayed(() -> {
            try {
                callback.endOfSpeech();
                Bundle result = new Bundle();
                result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(List.of("lovely")));
                callback.results(result);
            } catch (RemoteException disconnected) { }
        }, 80);
    }
    @Override protected void onStopListening(Callback callback) { }
    @Override protected void onCancel(Callback callback) { handler.removeCallbacksAndMessages(null); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
