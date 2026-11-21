package uk.dnm.swipekeys;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class SetupActivity extends Activity {

    static final String EXTRA_ASK_MIC = "ask_mic";
    static final String EXTRA_NEW_NOTE = "new_note";
    private static final int REQ_MIC = 1;

    private SharedPreferences prefs;
    private TextView status;
    private Button enable;
    private Button choose;
    private Button mic;
    private EditText noteInput;
    private LinearLayout noteList;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences(SwipeKeyboardService.PREFS, MODE_PRIVATE);
        int pad = px(24);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad);
        col.setBackgroundColor(Color.BLACK);

        col.addView(text("Swipe Keys", 28));

        TextView how = text("Tap a key for the letter. Pull a key DOWN to type the small character "
                + "shown above it — numbers on the top row, symbols on the rest.\n\n"
                + "• Slide across the letters to swipe-write a word\n"
                + "• Tap a suggestion above the keys to finish a word — it learns the words you use\n"
                + "• The squares button on the left of that strip opens emoji, notes, one-handed mode and settings\n"
                + "• The mic on the right is voice typing\n"
                + "• Tap shift once for a capital, twice quickly for caps lock\n"
                + "• Slide along the space bar to move the cursor\n"
                + "• Double-tap space for a full stop\n"
                + "• Hold the space bar to switch keyboards", 16);
        how.setTextColor(0xFFBBBBBB);
        how.setPadding(0, px(12), 0, px(24));
        col.addView(how);

        // --- Setup
        col.addView(section("Set up"));
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

        mic = new Button(this);
        mic.setOnClickListener(v -> askMic());
        col.addView(mic);

        EditText test = new EditText(this);
        test.setHint("Try it here…");
        test.setTextColor(Color.WHITE);
        test.setHintTextColor(0xFF777777);
        test.setMinLines(3);
        test.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(16);
        col.addView(test, lp);

        // --- Settings
        col.addView(section("Settings"));
        Switch glide = new Switch(this);
        glide.setText("Swipe to write");
        glide.setTextColor(Color.WHITE);
        glide.setTextSize(16);
        glide.setChecked(prefs.getBoolean(SwipeKeyboardService.PREF_GLIDE, true));
        glide.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(SwipeKeyboardService.PREF_GLIDE, on).apply());
        glide.setPadding(0, px(8), 0, px(8));
        col.addView(glide);

        // --- Notes
        col.addView(section("Notes"));
        TextView notesHelp = text("Notes show up under the notes button on the keyboard. Tap one there to paste it.", 14);
        notesHelp.setTextColor(0xFFBBBBBB);
        col.addView(notesHelp);
        noteInput = new EditText(this);
        noteInput.setHint("Write a note…");
        noteInput.setTextColor(Color.WHITE);
        noteInput.setHintTextColor(0xFF777777);
        noteInput.setMinLines(2);
        noteInput.setGravity(Gravity.TOP);
        col.addView(noteInput);
        Button add = new Button(this);
        add.setText("Save note");
        add.setOnClickListener(v -> {
            String s = noteInput.getText().toString().trim();
            if (s.isEmpty()) return;
            Notes.add(this, s);
            noteInput.setText("");
            refreshNotes();
        });
        col.addView(add);
        noteList = new LinearLayout(this);
        noteList.setOrientation(LinearLayout.VERTICAL);
        col.addView(noteList);

        TextView credits = text("Word list: wordfreq (CC BY-SA 4.0) and SCOWL.", 12);
        credits.setTextColor(0xFF777777);
        credits.setPadding(0, px(32), 0, 0);
        col.addView(credits);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(col);
        setContentView(scroll);
        handleExtras(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleExtras(intent);
    }

    private void handleExtras(Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra(EXTRA_ASK_MIC, false)) askMic();
        if (i.getBooleanExtra(EXTRA_NEW_NOTE, false)) {
            noteInput.post(() -> {
                scroll.smoothScrollTo(0, noteInput.getTop());
                noteInput.requestFocus();
            });
        }
    }

    private void askMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return;
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                && prefs.getBoolean("mic_asked", false)) {
            // Android won't ask again; send them to the app's settings page
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        prefs.edit().putBoolean("mic_asked", true).apply();
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshNotes();
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

        boolean micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        mic.setText(micOk ? "✓ Microphone allowed (voice typing)" : "3. Allow microphone for voice typing");
        mic.setEnabled(!micOk);
    }

    private void refreshNotes() {
        noteList.removeAllViews();
        List<String> notes = Notes.load(this);
        for (String n : notes) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1E1E20);
            bg.setCornerRadius(px(10));
            row.setBackground(bg);
            row.setPadding(px(14), px(6), px(4), px(6));

            TextView t = text(n, 15);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = text("Delete", 14);
            del.setTextColor(0xFFFF6B6B);
            del.setPadding(px(12), px(10), px(12), px(10));
            del.setOnClickListener(v -> {
                Notes.remove(this, n);
                refreshNotes();
            });
            row.addView(del);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(8);
            noteList.addView(row, lp);
        }
    }

    private TextView section(String s) {
        TextView t = text(s.toUpperCase(), 13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(0xFF0A84FF);
        t.setPadding(0, px(28), 0, px(8));
        return t;
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
