package ly.anoma.hex.android;

/** Tap starts/stops; a press that started a recording stops on a held release. */
final class RecordGesture {
    enum Action { NONE, START, STOP, CANCEL }
    static final long HOLD_MILLIS = 350;
    private boolean startedOnPress;
    private boolean pressed;
    private long downAt;
    Action down(long time, boolean recording, boolean finishing) {
        pressed = !finishing;
        startedOnPress = pressed && !recording;
        downAt = time;
        return !pressed ? Action.NONE : recording ? Action.STOP : Action.START;
    }
    Action up(long time, boolean inside) {
        if (!pressed) return Action.NONE;
        pressed = false;
        if (!inside) return Action.CANCEL;
        return startedOnPress && time - downAt >= HOLD_MILLIS ? Action.STOP : Action.NONE;
    }
    Action cancel() {
        boolean wasPressed = pressed;
        pressed = false;
        return wasPressed ? Action.CANCEL : Action.NONE;
    }
}
