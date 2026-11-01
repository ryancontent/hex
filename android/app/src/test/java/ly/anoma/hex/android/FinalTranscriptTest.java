package ly.anoma.hex.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class FinalTranscriptTest {
    @Test public void ordinaryPartialIsNeverPromotedAtCompletion() {
        FinalTranscript transcript = new FinalTranscript();
        assertEquals("unfinished", transcript.partial("unfinished", false));
        assertEquals("", transcript.complete(""));
    }
    @Test public void googleExplicitFinalSurvivesEmptyCompletion() {
        FinalTranscript transcript = new FinalTranscript();
        transcript.partial("draft", false);
        transcript.partial("Final words.", true);
        assertEquals("Final words.", transcript.complete(""));
    }
    @Test public void terminalResultIsAuthoritative() {
        FinalTranscript transcript = new FinalTranscript();
        transcript.partial("old words", true);
        assertEquals("Corrected words.", transcript.complete("Corrected words."));
    }
    @Test public void confirmedUtterancesJoinWithoutAcceptingUnfinishedTail() {
        FinalTranscript transcript = new FinalTranscript();
        transcript.partial("First sentence.", true);
        assertEquals("First sentence. next draft", transcript.partial("next draft", false));
        transcript.partial("Second sentence.", true);
        transcript.partial("unfinished", false);
        assertEquals("First sentence. Second sentence.", transcript.complete(""));
    }
}
