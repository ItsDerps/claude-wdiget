package uk.dnm.claudewidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView t = new TextView(this);
        t.setText("Claude Widget is installed.\n\nLong-press your home screen → Widgets → Claude Widget, then drag it where you want it.");
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(64, 64, 64, 64);
        t.setBackgroundColor(Color.BLACK);
        setContentView(t);
    }
}
