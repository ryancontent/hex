package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;
import static ly.anoma.hex.android.RecordGesture.Action.*;

public class RecordGestureTest {
    @Test public void tapStartsThenNextTapStopsWithoutRestartingOnRelease() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(START, gesture.down(0, false, false));
        assertEquals(NONE, gesture.up(100, true));
        assertEquals(NONE, gesture.down(500, true, false));
        assertEquals(STOP, gesture.up(600, true));
    }
    @Test public void holdStopsOnRelease() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(START, gesture.down(0, false, false));
        assertEquals(STOP, gesture.up(400, true));
    }
    @Test public void slidingOffOrSystemCancelCancelsTheRecording() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, false, false);
        assertEquals(CANCEL, gesture.up(100, false));
        gesture.down(200, false, false);
        assertEquals(CANCEL, gesture.cancel());
        assertEquals(NONE, gesture.up(700, true));
    }
    @Test public void finishingDoesNotStartAnotherJob() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(NONE, gesture.down(0, true, true));
        assertEquals(NONE, gesture.up(800, true));
    }
    @Test public void slidingOffTheSameButtonCancelsALockedRecordingBeforeStopCommits() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(NONE, gesture.down(0, true, false));
        assertEquals(CANCEL, gesture.up(200, false));
        assertEquals(NONE, gesture.up(300, true));
    }
    @Test public void slidingOffAlsoCancelsProcessingWithoutStartingAnotherRecording() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(NONE, gesture.down(0, true, true));
        assertEquals(CANCEL, gesture.up(200, false));
    }
}
