package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class BubblePositionTest {
    private final BubblePosition.Area screen = new BubblePosition.Area(10, 40, 990, 1960);
    @Test public void centerPlacementDoesNotSnapToAnEdge() {
        assertEquals(new BubblePosition.Point(470, 970),
                BubblePosition.restore(new BubblePosition.Saved(0.5f, 0.5f), screen, 60));
    }
    @Test public void arbitraryPointRoundTripsThroughPersistence() {
        var point = new BubblePosition.Point(321, 789);
        assertEquals(point, BubblePosition.restore(BubblePosition.save(point, screen, 60), screen, 60));
    }
    @Test public void systemBarsAndDisplayEdgesBoundTheWholeButton() {
        assertEquals(new BubblePosition.Point(10, 40), BubblePosition.clamp(-100, -100, screen, 60));
        assertEquals(new BubblePosition.Point(930, 1900), BubblePosition.clamp(3000, 3000, screen, 60));
    }
    @Test public void rotationRestoresRelativePositionRatherThanAnOldScreenEdge() {
        var saved = new BubblePosition.Saved(0.25f, 0.75f);
        assertEquals(new BubblePosition.Point(500, 710),
                BubblePosition.restore(saved, new BubblePosition.Area(20, 20, 2000, 1000), 60));
    }
    @Test public void badSavedValuesAndTinyScreensStayBounded() {
        assertEquals(new BubblePosition.Point(930, 970),
                BubblePosition.restore(new BubblePosition.Saved(Float.NaN, Float.POSITIVE_INFINITY), screen, 60));
        var tiny = new BubblePosition.Area(5, 10, 25, 30);
        assertEquals(new BubblePosition.Point(5, 10),
                BubblePosition.restore(new BubblePosition.Saved(1, 1), tiny, 60));
        assertEquals(new BubblePosition.Saved(0, 0), BubblePosition.save(new BubblePosition.Point(50, 50), tiny, 60));
    }
}
