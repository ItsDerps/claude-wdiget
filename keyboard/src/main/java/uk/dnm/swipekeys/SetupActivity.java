package uk.dnm.swipekeys;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SetupActivity extends Activity {

    private TextView status;
    private Button enable;
    private Button choose;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        int pad = px(24);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad);
        col.setBackgroundColor(Color.BLACK);

        TextView title = text("Swipe Keys", 28);
        col.addView(title);

        TextView how = text("Tap a key for the letter. Pull a key DOWN to type the small character "
                + "shown above it — numbers on the top row, symbols on the rest.\n\n"
                + "• Tap shift once for a capital, twice quickly for caps lock\n"
                + "• Slide along the space bar to move the cursor\n"
                + "• Double-tap space for a full stop\n"
                + "• Hold the space bar to switch keyboards", 16);
        how.setTextColor(0xFFBBBBBB);
        how.setPadding(0, px(12), 0, px(24));
        col.addView(how);

        status = text("", 16);
        status.setPadding(0, 0, 0, px(12));
        col.addView(status);

        enable = new Button(this);
        enable.setText("1. Turn on Swipe Keys");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        col.addView(enable);

        choose = new Button(this);
        choose.setText("2. Switch to Swipe Keys");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.showInputMethodPicker();
        });
        col.addView(choose);

        EditText test = new EditText(this);
        test.setHint("Try it here…");
        test.setTextColor(Color.WHITE);
        test.setHintTextColor(0xFF777777);
        test.setMinLines(3);
        test.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(24);
        col.addView(test, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refresh(); // the keyboard picker is a dialog, so re-check when it closes
    }

    private void refresh() {
        boolean enabled = false;
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getPackageName().equals(getPackageName())) enabled = true;
            }
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = current != null && current.startsWith(getPackageName() + "/");

        enable.setEnabled(!enabled);
        choose.setEnabled(enabled && !active);
        status.setText(active ? "✓ Swipe Keys is your keyboard."
                : enabled ? "Turned on — now switch to it." : "Not turned on yet.");
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        return t;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
