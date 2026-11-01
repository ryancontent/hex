package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;
import static ly.anoma.hex.android.RecordGesture.Action.*;

public class RecordGestureTest {
    @Test public void tapStartsOnReleaseAndTheNextTapStops() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(NONE, gesture.down(0, false, false));
        assertEquals(START, gesture.up(100, true));
        assertEquals(NONE, gesture.down(500, true, false));
        assertEquals(STOP, gesture.up(600, true));
    }
    @Test public void stationaryHoldStartsOnceAndStopsOnRelease() {
        RecordGesture gesture = new RecordGesture();
        assertEquals(NONE, gesture.down(0, false, false));
        assertEquals(START, gesture.hold());
        assertEquals(NONE, gesture.hold());
        assertTrue(gesture.isHolding());
        assertEquals(STOP, gesture.up(600, true));
        assertFalse(gesture.isHolding());
    }
    @Test public void idleDragNeverStartsOrStopsRecording() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, false, false);
        assertEquals(NONE, gesture.drag());
        assertEquals(NONE, gesture.hold());
        assertEquals(NONE, gesture.up(700, true));
    }
    @Test public void movingAfterAHoldCancelsInsteadOfInserting() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, false, false);
        gesture.hold();
        assertEquals(CANCEL, gesture.drag());
        assertEquals(NONE, gesture.up(700, true));
        assertFalse(gesture.isHolding());
    }
    @Test public void draggingALockedRecordingCancelsBeforeStopCommits() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, true, false);
        assertEquals(CANCEL, gesture.drag());
        assertEquals(NONE, gesture.up(300, true));
    }
    @Test public void draggingProcessingCancelsWithoutStartingAnotherJob() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, true, true);
        assertEquals(CANCEL, gesture.drag());
        assertEquals(NONE, gesture.up(300, true));
    }
    @Test public void tappingProcessingDoesNothing() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, true, true);
        assertEquals(NONE, gesture.hold());
        assertEquals(NONE, gesture.up(700, true));
    }
    @Test public void cancelledPendingTouchCannotStartOnRelease() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, false, false);
        assertEquals(NONE, gesture.cancel());
        assertEquals(NONE, gesture.hold());
        assertEquals(NONE, gesture.up(700, true));
    }
    @Test public void systemCancellationEndsAHeldCapture() {
        RecordGesture gesture = new RecordGesture();
        gesture.down(0, false, false);
        gesture.hold();
        assertEquals(CANCEL, gesture.cancel());
        assertEquals(NONE, gesture.up(700, true));
    }
}
