package ly.anoma.hex.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Surface {
    static final int BACKGROUND = Color.rgb(17, 17, 20);
    static final int INK = Color.rgb(242, 242, 239);
    static final int MUTED = Color.rgb(161, 177, 190);
    static final int ACCENT = Color.rgb(179, 199, 230);
    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(BACKGROUND);
        int pad = dp(context, 20);
        layout.setPadding(pad, pad, pad, pad);
        return layout;
    }
    static TextView text(Context context, String text, int size, boolean title) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(title ? INK : MUTED);
        if (title) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(context, 6), 0, dp(context, 10));
        return view;
    }
    static Button button(Context context, String label, View.OnClickListener action) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(BACKGROUND);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        button.setOnClickListener(action);
        return button;
    }
}
