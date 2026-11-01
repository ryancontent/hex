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
}
