package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorSessionTest {
    @Test public void switchingEditorsRejectsOldTranscript() {
        EditorSession session = new EditorSession();
        long previous = session.begin();
        session.invalidate();
        long current = session.begin();
        assertFalse(session.consume(previous));
        assertTrue(session.consume(current));
    }
    @Test public void cancelledAndHiddenSessionsNeverInsert() {
        EditorSession session = new EditorSession();
        long token = session.begin();
        session.invalidate();
        assertFalse(session.consume(token));
    }
    @Test public void finalResultInsertsOnlyOnce() {
        EditorSession session = new EditorSession();
        long token = session.begin();
        assertTrue(session.consume(token));
        assertFalse(session.consume(token));
    }
    @Test public void startingAgainRejectsWindingDownResult() {
        EditorSession session = new EditorSession();
        long old = session.begin();
        long current = session.begin();
        assertFalse(session.consume(old));
        assertTrue(session.consume(current));
    }
    @Test public void insertionRespectsCursorAndPunctuation() {
        assertEquals(" hello ", EditorSession.insertion("hello", "a", "b"));
        assertEquals("hello", EditorSession.insertion("hello", "(", ")"));
        assertEquals("hello", EditorSession.insertion("hello", " ", "."));
        assertEquals(".", EditorSession.insertion(".", "a", ""));
        assertEquals("", EditorSession.insertion("  ", "a", "b"));
    }
}
