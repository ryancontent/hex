package ly.anoma.hex.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public final class SetupActivity extends Activity {
    private Preferences preferences;
    private TextView readiness;
    private TextView status;
    private TextView preview;
    private Button record;
    private EditText practice;
    private SpeechSession speech;
    private SpeechRecognizer probe;
    private long probeGeneration;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = new Preferences(this);
        LinearLayout root = Surface.column(this);
        // Target 35 draws edge-to-edge: keep every control clear of system bars and the IME.
        root.addView(Surface.text(this, "Hex for Android", 30, true));
        root.addView(Surface.text(this, "Pixel proof of concept", 17, false));
        root.addView(Surface.text(this,
                "Keep your keyboard. Dictate from a floating button above it in other apps. Recognition runs on this device; "
                + "Hex does not save recordings or transcripts.", 15, false));
        root.addView(Surface.text(this, "Labs", 20, true));
        Switch enable = new Switch(this);
        enable.setText("Experimental dictation");
        enable.setTextColor(Surface.INK);
        enable.setChecked(preferences.enabled());
        enable.setOnCheckedChangeListener((button, enabled) -> {
            preferences.setEnabled(enabled);
            if (!enabled && speech != null) speech.cancel();
            refresh();
        });
        root.addView(enable);
        root.addView(Surface.text(this, "Off by default. Uses Android’s on-device speech service.", 14, false));
        readiness = Surface.text(this, "Checking device…", 15, false);
        root.addView(readiness);
        root.addView(Surface.button(this, "Allow microphone", view ->
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1)));

        root.addView(Surface.text(this, "Speech language", 18, true));
        EditText language = new EditText(this);
        language.setSingleLine(true);
        language.setTextColor(Surface.INK);
        language.setText(preferences.language());
        language.setHint("Language tag, e.g. en-US");
        language.setHintTextColor(Surface.MUTED);
        language.setContentDescription("Speech language tag");
        root.addView(language);
        root.addView(Surface.button(this, "Save language", view -> {
            String tag = language.getText().toString().trim();
            try {
                Locale parsed = new Locale.Builder().setLanguageTag(tag).build();
                if (parsed.getLanguage().isEmpty() || parsed.getLanguage().equals("und"))
                    throw new IllegalArgumentException();
                if (speech.active()) speech.cancel();
                preferences.setLanguage(parsed.toLanguageTag());
                refresh();
            } catch (RuntimeException invalid) {
                readiness.setText("Enter a language tag such as en-US or en-GB.");
            }
        }));
        root.addView(Surface.button(this, "Prepare offline language", view -> downloadModel()));
        root.addView(Surface.button(this, "Check readiness", view -> refresh()));

        root.addView(Surface.text(this, "Try dictation", 20, true));
        status = Surface.text(this, "Turn on experimental dictation, allow the microphone, then tap Dictate.", 15, false);
        root.addView(status);
        preview = Surface.text(this, "", 17, true);
        root.addView(preview);
        practice = new EditText(this);
        practice.setId(R.id.practice);
        practice.setSaveEnabled(false);
        practice.setTextColor(Surface.INK);
        practice.setHintTextColor(Surface.MUTED);
        practice.setHint("Words appear here. You can also use the floating Hex button.");
        practice.setContentDescription("Dictation practice field");
        practice.setMinLines(2);
        practice.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(practice);
        speech = new SpeechSession(this, new SpeechSession.Listener() {
            @Override public void status(String text) { status.setText(text); }
            @Override public void partial(String text) { preview.setText(text); }
            @Override public void result(String text, long millis) {
                practice.append((practice.length() == 0 ? "" : "\n") + text);
                preview.setText("");
                status.setText("Transcribed on-device" + (millis < 0 ? "" : " · " + millis + " ms after stop"));
            }
            @Override public void finished() { record.setText("Dictate here"); }
        });
        record = Surface.button(this, "Dictate here", view -> {
            if (speech.active()) speech.stop();
            else {
                preview.setText("");
                record.setText("Stop");
                speech.start(preferences.language());
            }
        });
        root.addView(record);
        root.addView(Surface.button(this, "Cancel", view -> {
            speech.cancel();
            preview.setText("");
            status.setText("Cancelled");
        }));

        root.addView(Surface.text(this, "Use in other apps", 20, true));
        root.addView(Surface.text(this,
                "Enable Hex floating dictation in Accessibility settings. Your keyboard stays selected. "
                + "Focus a text field: tap the floating button to start and tap again to finish, "
                + "or hold it while speaking and release to finish. Slide off the button to cancel.", 15, false));
        root.addView(Surface.text(this,
                "Accessibility lets Hex find the selected field and insert at its cursor. "
                + "When you start recording, Hex reads that field’s text and selection to preserve existing words. "
                + "It does not save or send them. Password fields are excluded. "
                + "If you edit or change fields during recording, Hex cancels the result.", 14, false));
        root.addView(Surface.button(this, "Enable floating dictation", view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(Surface.text(this, "Proof limits", 20, true));
        root.addView(Surface.text(this,
                "No Parakeet or desktop sync in this build. Some custom text fields do not support Accessibility insertion. "
                + "Speech availability and languages depend on the phone’s installed speech service. "
                + "Language setup may download a model; dictation has no cloud fallback. "
                + "An emulator without an on-device recognizer can only test setup and overlay behavior.", 14, false));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Surface.BACKGROUND);
        scroll.addView(root);
        // Insets belong to the viewport, not the scrolling content. Padding the
        // content itself left the practice editor behind the IME after focus.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            scroll.post(() -> {
                android.view.View focused = getCurrentFocus();
                if (focused != null && focused != scroll) {
                    android.graphics.Rect rectangle = new android.graphics.Rect();
                    focused.getDrawingRect(rectangle);
                    scroll.offsetDescendantRectToMyCoords(focused, rectangle);
                    scroll.scrollTo(0, Math.max(0, rectangle.top - scroll.getPaddingTop()));
                }
            });
            return insets;
        });
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onPause() {
        speech.cancel();
        closeProbe();
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }

    private String summary() {
        return Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + "\nLabs: " + (preferences.enabled() ? "on" : "off")
                + " · Microphone: " + (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED ? "allowed" : "not allowed");
    }

    private void refresh() {
        closeProbe();
        String base = summary();
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            readiness.setText(base + "\nOn-device speech unavailable. On your Pixel, update Speech Recognition "
                    + "& Synthesis and Android System Intelligence, then check again. "
                    + "This emulator may not provide an on-device recognizer.");
            return;
        }
        readiness.setText(base + "\nOn-device speech service available · " + preferences.language());
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            long token = probeGeneration;
            probe.checkRecognitionSupport(SpeechSession.request(preferences.language()), getMainExecutor(),
                    new RecognitionSupportCallback() {
                        @Override public void onSupportResult(RecognitionSupport support) {
                            if (probeGeneration != token) return;
                            readiness.setText(base + "\nOffline languages installed: "
                                    + support.getInstalledOnDeviceLanguages()
                                    + "\nPending downloads: " + support.getPendingOnDeviceLanguages()
                                    + "\nSelected: " + preferences.language());
                        }
                        @Override public void onError(int error) {
                            if (probeGeneration == token) readiness.setText(base
                                    + "\nOn-device service available; language readiness could not be checked ("
                                    + error + "). Try Prepare offline language.");
                        }
                    });
        } catch (RuntimeException unavailable) {
            readiness.setText(base + "\nCouldn’t query the speech service. Try again after updating it.");
            closeProbe();
        }
    }

    private void downloadModel() {
        if (!preferences.enabled()) { readiness.setText("Turn on experimental dictation first."); return; }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) { refresh(); return; }
        if (Build.VERSION.SDK_INT < 33) {
            readiness.setText("Install offline languages in your phone’s speech-service settings.");
            return;
        }
        closeProbe();
        try {
            probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            long token = probeGeneration;
            readiness.setText("Requesting offline model for " + preferences.language() + "…");
            if (Build.VERSION.SDK_INT >= 34) {
                probe.triggerModelDownload(SpeechSession.request(preferences.language()), getMainExecutor(),
                        new ModelDownloadListener() {
                            @Override public void onProgress(int percent) {
                                if (probeGeneration == token) readiness.setText("Downloading offline language · " + percent + "%");
                            }
                            @Override public void onSuccess() {
                                if (probeGeneration == token) readiness.setText("Offline language ready. Try dictation with Wi-Fi and mobile data off.");
                            }
                            @Override public void onScheduled() {
                                if (probeGeneration == token) readiness.setText("Download scheduled by Android. Check readiness again shortly.");
                            }
                            @Override public void onError(int error) {
                                if (probeGeneration == token) readiness.setText("Language download unavailable (" + error
                                        + "). Check network and speech-service settings, then retry.");
                            }
                        });
            } else {
                probe.triggerModelDownload(SpeechSession.request(preferences.language()));
                readiness.setText("Download requested. Check readiness again when the language is installed.");
            }
        } catch (RuntimeException unavailable) {
            readiness.setText("Couldn’t request a language download. Check speech-service settings and retry.");
            closeProbe();
        }
    }

    private void closeProbe() {
        probeGeneration++;
        if (probe != null) {
            try { probe.destroy(); } catch (RuntimeException ignored) { }
            probe = null;
        }
    }
}
