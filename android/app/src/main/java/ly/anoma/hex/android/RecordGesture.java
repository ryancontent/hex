package ly.anoma.hex.android;

/** Tap toggles; a stationary hold records; dragging never commits a transcript. */
final class RecordGesture {
    enum Action { NONE, START, STOP, CANCEL }
    static final long HOLD_MILLIS = 350;
    private boolean wasRecording;
    private boolean pressed;
    private boolean finishingOnPress;
    private boolean holdStarted;
    private boolean dragging;
    Action down(long time, boolean recording, boolean finishing) {
        pressed = true;
        finishingOnPress = finishing;
        wasRecording = recording;
        holdStarted = false;
        dragging = false;
        return Action.NONE;
    }
    Action hold() {
        if (!pressed || dragging || wasRecording || finishingOnPress || holdStarted) return Action.NONE;
        holdStarted = true;
        return Action.START;
    }
    boolean isHolding() { return pressed && holdStarted && !dragging; }
    Action drag() {
        if (!pressed || dragging) return Action.NONE;
        dragging = true;
        return wasRecording || finishingOnPress || holdStarted ? Action.CANCEL : Action.NONE;
    }
    Action up(long time, boolean inside) {
        if (!pressed) return Action.NONE;
        pressed = false;
        if (dragging) return Action.NONE;
        if (!inside) return wasRecording || finishingOnPress || holdStarted ? Action.CANCEL : Action.NONE;
        if (finishingOnPress) return Action.NONE;
        return wasRecording || holdStarted ? Action.STOP : Action.START;
    }
    Action cancel() {
        boolean cancelRecording = pressed && (wasRecording || finishingOnPress || holdStarted);
        pressed = false;
        return cancelRecording ? Action.CANCEL : Action.NONE;
    }
}
