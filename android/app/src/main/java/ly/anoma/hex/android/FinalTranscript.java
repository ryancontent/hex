package ly.anoma.hex.android;

/** Never promotes an ordinary partial into inserted text. */
final class FinalTranscript {
    // Observed on Google's API 35 on-device service: finalized utterances arrive in
    // onPartialResults with this explicit marker, followed by an empty onResults.
    // This is a returned vendor extra, not a private API or a stability heuristic.
    static final String GOOGLE_FINAL_RESULT = "final_result";
    private final StringBuilder confirmed = new StringBuilder();

    String partial(String text, boolean explicitlyFinal) {
        String cleaned = text.trim();
        if (explicitlyFinal && !cleaned.isEmpty()) {
            if (confirmed.length() > 0) confirmed.append(' ');
            confirmed.append(cleaned);
            return confirmed.toString();
        }
        return confirmed + (confirmed.length() > 0 && !cleaned.isEmpty() ? " " : "") + cleaned;
    }

    String complete(String terminalResult) {
        String cleaned = terminalResult.trim();
        return cleaned.isEmpty() ? confirmed.toString() : cleaned;
    }
}
