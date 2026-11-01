package ly.anoma.hex.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.Toast;

/** A system-bound Accessibility overlay. Never changes the selected input method or clipboard. */
public final class FloatingDictationService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EditorSession generation = new EditorSession();
    private final RecordGesture gesture = new RecordGesture();
    private final Runnable refresh = this::refreshTarget;
    private SpeechSession speech;
    private Button bubble;
    private String status = "Ready to dictate";
    private Button microphone;
    private GradientDrawable buttonBackground;
    private int buttonColor;
    private WindowManager manager;
    private WindowManager.LayoutParams placement;
    private AccessibilityNodeInfo target;
    private AccessibilityNodeInfo recordingTarget;
    private String original;
    private int selectionStart;
    private int selectionEnd;
    private long token;
    private boolean attached;
    private boolean connected;
    private boolean pointerClick;
    private long feedbackUntil;
    private boolean errorFeedback;
    private int lastX = -1, lastY = -1, lastWidth = -1, lastHeight = -1;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChange = (prefs, key) -> scheduleRefresh();
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { cancelRecording(); hide(); }
    };

    @Override protected void onServiceConnected() {
        if (connected) return;
        connected = true;
        manager = getSystemService(WindowManager.class);
        preferences = getSharedPreferences("labs", MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceChange);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                "dictation", "Dictation microphone", NotificationManager.IMPORTANCE_LOW));
        createBubble();
        speech = new SpeechSession(this, new SpeechSession.Listener() {
            @Override public void status(String text) {
                status = text;
                if (!speech.active()) renderMessage(); else render();
            }
            @Override public void partial(String text) { /* Final text is handled by SpeechSession; no separate preview. */ }
            @Override public void result(String text, long millis) { insert(text); }
            @Override public void finished() {
                stopForeground(STOP_FOREGROUND_REMOVE);
                clearRecordingTarget();
                render();
            }
        });
        refreshTarget();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only inspect the focused node; never scan or retain surrounding window contents.
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!connected) return;
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 80);
    }

    private AccessibilityNodeInfo focusedEditor() {
        if (getSystemService(KeyguardManager.class).isKeyguardLocked() || !keyboardVisible()) return null;
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || (!window.isActive() && !window.isFocused())) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            root.recycle();
            if (node != null && node.refresh() && supported(node)) return node;
            if (node != null) node.recycle();
        }
        return null;
    }

    private boolean supported(AccessibilityNodeInfo node) {
        return node.isEditable() && node.isFocused() && node.isEnabled() && node.isVisibleToUser()
                && !node.isPassword()
                && selectionStart(node) >= 0 && selectionEnd(node) >= 0
                && node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);
    }

    private boolean keyboardVisible() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) return true;
        }
        return false;
    }

    private void refreshTarget() {
        if (!connected || speech == null) return;
        AccessibilityNodeInfo next = new Preferences(this).enabled() ? focusedEditor() : null;
        if (speech.active() && (next == null || recordingTarget == null || !recordingTarget.equals(next)
                || !unchanged(next))) {
            cancelRecording();
            if (next != null) {
                status = "Field or cursor changed. Dictation cancelled.";
                renderMessage();
            }
        }
        if (target != null) target.recycle();
        target = next;
        if (target == null) hide(); else show();
    }

    private boolean unchanged(AccessibilityNodeInfo node) {
        return original != null && original.contentEquals(text(node))
                && selectionStart(node) == selectionStart && selectionEnd(node) == selectionEnd;
    }

    private static boolean empty(AccessibilityNodeInfo node) {
        return node.isShowingHintText() || node.getText() == null || node.getText().length() == 0;
    }
    private static int selectionStart(AccessibilityNodeInfo node) {
        return FieldInsertion.selection(empty(node), node.getTextSelectionStart());
    }
    private static int selectionEnd(AccessibilityNodeInfo node) {
        return FieldInsertion.selection(empty(node), node.getTextSelectionEnd());
    }

    private static String text(AccessibilityNodeInfo node) {
        // An empty EditText can expose its hint as text on some implementations.
        return node.isShowingHintText() || node.getText() == null ? "" : node.getText().toString();
    }

    private void startRecording() {
        refreshTarget();
        if (target == null || !new Preferences(this).enabled()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Allow microphone in Hex setup.";
            renderMessage();
            return;
        }
        original = text(target);
        selectionStart = selectionStart(target);
        selectionEnd = selectionEnd(target);
        if (FieldInsertion.prepare(original, selectionStart, selectionEnd, "test") == null) {
            original = null;
            status = "This field doesn’t expose its cursor. Try another field.";
            renderMessage();
            return;
        }
        recordingTarget = AccessibilityNodeInfo.obtain(target);
        feedbackUntil = 0;
        token = generation.begin();
        try {
            PendingIntent setup = PendingIntent.getActivity(this, 0, new Intent(this, SetupActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, "dictation")
                    .setSmallIcon(R.drawable.hex_icon).setContentTitle("Hex dictation")
                    .setContentText("Tap the floating button to finish; slide off it to cancel.")
                    .setOngoing(true).setContentIntent(setup).build();
            startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            speech.start(new Preferences(this).language());
        } catch (RuntimeException unavailable) {
            cancelRecording();
            status = "Microphone couldn’t start. Open Hex setup and check permission.";
            renderMessage();
        }
        render();
    }

    private void insert(String words) {
        AccessibilityNodeInfo current = focusedEditor();
        try {
            if (!generation.consume(token) || !new Preferences(this).enabled()) return;
            if (current == null || !current.refresh() || !supported(current) || recordingTarget == null
                    || !recordingTarget.equals(current) || !unchanged(current)) {
                status = "Field or cursor changed. Nothing inserted.";
                renderMessage();
                return;
            }
            FieldInsertion.Result result = FieldInsertion.prepare(original, selectionStart, selectionEnd, words);
            if (result == null) return;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text());
            if (!current.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                status = "This app refused insertion. Try another field.";
                renderMessage();
                return;
            }
            Bundle cursor = new Bundle();
            cursor.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, result.cursor());
            cursor.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, result.cursor());
            current.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursor);
            status = "Inserted · on-device";
            renderMessage();
        } finally { if (current != null) current.recycle(); }
    }

    private void clearRecordingTarget() {
        generation.invalidate();
        if (recordingTarget != null) recordingTarget.recycle();
        recordingTarget = null;
        original = null;
    }

    private void cancelRecording() {
        clearRecordingTarget();
        if (speech != null) speech.cancel();
        gesture.cancel();
    }

    private void act(RecordGesture.Action action) {
        switch (action) {
            case START -> startRecording();
            case STOP -> { if (speech != null) speech.stop(); }
            case CANCEL -> cancelRecording();
            case NONE -> { }
        }
    }

    private void createBubble() {
        microphone = new Button(this);
        bubble = microphone; // The overlay contains exactly this one control.
        microphone.setAllCaps(false);
        microphone.setGravity(Gravity.CENTER);
        microphone.setPadding(0, 0, 0, 0);
        microphone.setTextColor(Surface.BACKGROUND);
        microphone.setElevation(Surface.dp(this, 8));
        buttonBackground = new GradientDrawable();
        buttonBackground.setShape(GradientDrawable.OVAL);
        buttonBackground.setStroke(Surface.dp(this, 2), Surface.BACKGROUND);
        microphone.setBackground(buttonBackground);
        microphone.setOnClickListener(view -> {
            if (!pointerClick && speech != null && !speech.finishing()) {
                if (speech.active()) speech.stop(); else startRecording();
            }
        });
        microphone.setTextSize(26);
        microphone.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> act(gesture.down(event.getEventTime(), speech.active(), speech.finishing()));
                case MotionEvent.ACTION_UP -> {
                    boolean inside = event.getX() >= 0 && event.getY() >= 0
                            && event.getX() < view.getWidth() && event.getY() < view.getHeight();
                    act(gesture.up(event.getEventTime(), inside));
                    pointerClick = true;
                    try { view.performClick(); } finally { pointerClick = false; }
                }
                case MotionEvent.ACTION_CANCEL -> act(gesture.cancel());
                default -> { }
            }
            return true;
        });
        placement = new WindowManager.LayoutParams(Surface.dp(this, 60),
                Surface.dp(this, 60), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        placement.gravity = Gravity.TOP | Gravity.RIGHT;
        placement.setTitle("Hex floating dictation");
        render();
    }

    private void render() {
        boolean active = speech != null && speech.active();
        boolean finishing = active && speech.finishing();
        boolean feedback = SystemClock.uptimeMillis() < feedbackUntil;
        String label = finishing ? "…" : active ? "■" : feedback ? (errorFeedback ? "!" : "✓") : "●";
        if (!label.contentEquals(microphone.getText())) microphone.setText(label);
        String description = finishing ? "Hex is transcribing" : active ? "Stop Hex dictation" : "Start Hex dictation";
        if (!description.contentEquals(microphone.getContentDescription() == null ? "" : microphone.getContentDescription()))
            microphone.setContentDescription(description);
        int color = finishing ? Surface.MUTED : active ? Color.rgb(255, 145, 145)
                : feedback && errorFeedback ? Color.rgb(244, 200, 136) : Surface.ACCENT;
        if (buttonColor != color) { buttonBackground.setColor(color); buttonColor = color; }
        String state = active || feedback ? status : "Ready to dictate";
        if (!state.contentEquals(microphone.getStateDescription() == null ? "" : microphone.getStateDescription()))
            microphone.setStateDescription(state);
        position();
    }

    private void renderMessage() {
        errorFeedback = !status.equals("Inserted · on-device");
        long duration = errorFeedback ? 4_000 : 1_200;
        feedbackUntil = SystemClock.uptimeMillis() + duration;
        if (errorFeedback) Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        render();
        handler.postDelayed(() -> { if (connected) render(); }, duration + 10);
    }

    private void position() {
        if (manager == null || placement == null) return;
        Rect screen = manager.getCurrentWindowMetrics().getBounds();
        int bottom = screen.height() - Surface.dp(this, 64);
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect keyboard = new Rect();
                window.getBoundsInScreen(keyboard);
                if (!keyboard.isEmpty()) bottom = Math.min(bottom, keyboard.top);
            }
        }
        bubble.measure(View.MeasureSpec.makeMeasureSpec(placement.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(placement.height, View.MeasureSpec.EXACTLY));
        placement.x = Surface.dp(this, 12);
        placement.y = Math.max(Surface.dp(this, 48), bottom - bubble.getMeasuredHeight() - Surface.dp(this, 12));
        int height = bubble.getMeasuredHeight();
        if (attached && (lastX != placement.x || lastY != placement.y || lastWidth != placement.width || lastHeight != height))
            manager.updateViewLayout(bubble, placement);
        lastX = placement.x; lastY = placement.y; lastWidth = placement.width; lastHeight = height;
    }

    private void show() {
        render();
        if (!attached) {
            try { manager.addView(bubble, placement); attached = true; }
            catch (RuntimeException unavailable) { /* System may be closing the service/window. */ }
        }
    }
    private void hide() {
        if (attached) { manager.removeView(bubble); attached = false; }
    }
    @Override public void onInterrupt() { cancelRecording(); hide(); }
    @Override public void onDestroy() {
        connected = false;
        handler.removeCallbacksAndMessages(null);
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceChange);
            unregisterReceiver(screenOff);
        }
        cancelRecording();
        hide();
        if (target != null) target.recycle();
        super.onDestroy();
    }
}
