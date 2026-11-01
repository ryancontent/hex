package ly.anoma.hex.android;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class HexKeyboard extends InputMethodService {
    private final EditorSession editor = new EditorSession();
    private SpeechSession speech;
    private TextView status;
    private TextView preview;
    private Button dictate;
    private long token;
    private InputConnection destination;
    private boolean sensitive;

    @Override public void onCreate() {
        super.onCreate();
        speech = new SpeechSession(this, new SpeechSession.Listener() {
            @Override public void status(String text) { if (status != null) status.setText(text); }
            @Override public void partial(String text) { if (preview != null) preview.setText(text); }
            @Override public void result(String text, long finishMillis) {
                InputConnection current = getCurrentInputConnection();
                if (!editor.consume(token) || destination == null || destination != current
                        || !isInputViewShown() || !new Preferences(HexKeyboard.this).enabled()) return;
                String insertion = EditorSession.insertion(text,
                        current.getTextBeforeCursor(1, 0), current.getTextAfterCursor(1, 0));
                boolean inserted = current.commitText(insertion, 1);
                preview.setText(inserted ? "" : text);
                status.setText(inserted ? "Inserted · on-device" + timing(finishMillis)
                        : "Couldn’t insert. Text is shown here; retry in the field.");
            }
            @Override public void finished() { if (dictate != null) dictate.setText("Dictate"); }
        });
    }

    @Override public View onCreateInputView() {
        LinearLayout root = Surface.column(this);
        root.addView(Surface.text(this, "Hex  /  Labs", 22, true));
        status = Surface.text(this, "On-device dictation · " + new Preferences(this).language(), 14, false);
        root.addView(status);
        preview = Surface.text(this, "", 18, true);
        preview.setMinHeight(Surface.dp(this, 45));
        root.addView(preview);
        dictate = Surface.button(this, "Dictate", view -> toggle());
        root.addView(dictate);
        LinearLayout actions = new LinearLayout(this);
        addAction(actions, "Cancel", () -> {
            cancel();
            preview.setText("");
            status.setText("Cancelled");
        });
        addAction(actions, "Keyboards", () -> {
            cancel();
            getSystemService(InputMethodManager.class).showInputMethodPicker();
        });
        addAction(actions, "Setup", () -> {
            cancel();
            startActivity(new Intent(this, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        root.addView(actions);
        return root;
    }

    private void addAction(LinearLayout row, String label, Runnable action) {
        Button button = Surface.button(this, label, view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, Surface.dp(this, 52), 1));
    }

    private void toggle() {
        if (speech.active()) { speech.stop(); return; }
        if (sensitive) { status.setText("Dictation is off in password fields."); return; }
        destination = getCurrentInputConnection();
        if (destination == null) { status.setText("Tap a text field first."); return; }
        token = editor.begin();
        preview.setText("");
        dictate.setText("Stop");
        speech.start(new Preferences(this).language());
    }

    private static String timing(long millis) {
        return millis < 0 ? "" : " · " + millis + " ms after stop";
    }

    private void cancel() {
        editor.invalidate();
        destination = null;
        if (speech != null) speech.cancel();
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        cancel();
        super.onStartInput(info, restarting);
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        sensitive = (type == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (preview != null) preview.setText("");
        if (status != null) status.setText(sensitive ? "Dictation is off in password fields."
                : "On-device dictation · " + new Preferences(this).language());
    }
    @Override public void onFinishInput() { cancel(); super.onFinishInput(); }
    @Override public void onWindowHidden() { cancel(); super.onWindowHidden(); }
    @Override public void onDestroy() { cancel(); super.onDestroy(); }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
}
