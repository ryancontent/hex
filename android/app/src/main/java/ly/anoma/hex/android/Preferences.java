package ly.anoma.hex.android;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
    private final SharedPreferences values;
    Preferences(Context context) { values = context.getSharedPreferences("labs", Context.MODE_PRIVATE); }
    boolean enabled() { return values.getBoolean("dictation", false); }
    void setEnabled(boolean enabled) { values.edit().putBoolean("dictation", enabled).apply(); }
    String language() { return values.getString("language", "en-US"); }
    void setLanguage(String language) { values.edit().putString("language", language).apply(); }
    boolean hasBubblePosition() { return values.contains("bubble_x") && values.contains("bubble_y"); }
    BubblePosition.Saved bubblePosition() {
        return new BubblePosition.Saved(values.getFloat("bubble_x", 1), values.getFloat("bubble_y", 0.5f));
    }
    void setBubblePosition(BubblePosition.Saved position) {
        values.edit().putFloat("bubble_x", position.x()).putFloat("bubble_y", position.y()).apply();
    }
    void resetBubblePosition() { values.edit().remove("bubble_x").remove("bubble_y").apply(); }
}
