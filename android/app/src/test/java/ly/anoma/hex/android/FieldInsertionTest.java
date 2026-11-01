package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FieldInsertionTest {
    @Test public void hintOnlyEditorsHaveOneKnownInsertionPosition() {
        assertEquals(0, FieldInsertion.selection(true, -1));
        assertEquals(-1, FieldInsertion.selection(false, -1));
        assertEquals(5, FieldInsertion.selection(false, 5));
    }
    @Test public void insertsAtCursorAndPreservesBothSides() {
        var result = FieldInsertion.prepare("Hello world", 5, 5, "lovely");
        assertEquals("Hello lovely world", result.text());
        assertEquals(12, result.cursor());
    }
    @Test public void replacesOnlyTheSelectedRangeIncludingBackwardsSelection() {
        assertEquals("Hello friend!", FieldInsertion.prepare("Hello world!", 11, 6, "friend").text());
    }
    @Test public void unknownCursorDoesNotReplaceTheWholeField() {
        assertNull(FieldInsertion.prepare("Keep me", -1, -1, "words"));
        assertNull(FieldInsertion.prepare("Keep me", 0, 200, "words"));
        assertNull(FieldInsertion.prepare("Keep me", 0, 0, " "));
    }
}
