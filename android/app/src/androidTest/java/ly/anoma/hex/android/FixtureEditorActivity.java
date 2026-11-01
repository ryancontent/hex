package ly.anoma.hex.android;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A different APK/process keeps the Hex setup activity out of the foreground during tests. */
public final class FixtureEditorActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 140, 40, 40);
        TextView title = new TextView(this);
        title.setText("External editor fixture");
        title.setTextSize(24);
        root.addView(title);
        EditText first = field(root, "External first field", "Hello world");
        field(root, "External second field", "Keep this text");
        EditText password = field(root, "External password field", "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button leave = new Button(this);
        leave.setText("Leave text fields");
        leave.setFocusableInTouchMode(true);
        leave.setOnClickListener(view -> leave.requestFocus());
        root.addView(leave);
        setContentView(root);
        first.requestFocus();
        first.setSelection(5);
    }
    private EditText field(LinearLayout root, String label, String text) {
        EditText field = new EditText(this);
        field.setContentDescription(label);
        field.setText(text);
        root.addView(field);
        return field;
    }
}
