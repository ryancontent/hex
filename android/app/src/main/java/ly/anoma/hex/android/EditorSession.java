package ly.anoma.hex.android;

/** A result belongs to the editor and generation that accepted the recording. */
final class EditorSession {
    private long generation;
    private boolean accepting;

    long begin() { accepting = true; return ++generation; }
    void invalidate() { accepting = false; generation++; }
    boolean accepts(long token) { return accepting && token == generation; }
    boolean consume(long token) {
        if (!accepts(token)) return false;
        accepting = false;
        return true;
    }

    static String insertion(String text, CharSequence before, CharSequence after) {
        String result = text.trim();
        if (result.isEmpty()) return result;
        if (before != null && before.length() > 0) {
            char last = before.charAt(before.length() - 1);
            if (!Character.isWhitespace(last) && "([{\"“".indexOf(last) < 0
                    && ".,!?;:)]}”".indexOf(result.charAt(0)) < 0) result = " " + result;
        }
        if (after != null && after.length() > 0) {
            char first = after.charAt(0);
            if (!Character.isWhitespace(first) && ".,!?;:)]}”".indexOf(first) < 0) result += " ";
        }
        return result;
    }
}
