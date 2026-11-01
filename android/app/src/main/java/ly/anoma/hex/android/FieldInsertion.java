package ly.anoma.hex.android;

/** Splices only at a known selection; never guesses a cursor or replaces unrelated edits. */
final class FieldInsertion {
    record Result(String text, int cursor) { }
    static Result prepare(String original, int start, int end, String words) {
        if (start < 0 || end < 0 || start > original.length() || end > original.length()) return null;
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        String before = original.substring(0, left);
        String after = original.substring(right);
        String insertion = EditorSession.insertion(words, before, after);
        if (insertion.isEmpty()) return null;
        return new Result(before + insertion + after, left + insertion.length());
    }
}
