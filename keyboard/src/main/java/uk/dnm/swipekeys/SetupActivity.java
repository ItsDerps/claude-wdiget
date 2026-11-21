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
    private TextView personal;

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
        col.addView(label("Keyboard height"));
        col.addView(choice(SwipeKeyboardService.PREF_HEIGHT, "Small", "Normal", "Large"));
        col.addView(label("Pull down for numbers & symbols"));
        col.addView(choice(SwipeKeyboardService.PREF_FLICK, "Short", "Normal", "Long"));

        Switch vibrate = new Switch(this);
        vibrate.setText("Vibrate on key press");
        vibrate.setTextColor(Color.WHITE);
        vibrate.setTextSize(16);
        vibrate.setChecked(prefs.getBoolean(SwipeKeyboardService.PREF_VIBRATE, true));
        vibrate.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(SwipeKeyboardService.PREF_VIBRATE, on).apply());
        vibrate.setPadding(0, px(14), 0, px(8));
        col.addView(vibrate);

        Switch glide = new Switch(this);
        glide.setText("Swipe to write");
        glide.setTextColor(Color.WHITE);
        glide.setTextSize(16);
        glide.setChecked(prefs.getBoolean(SwipeKeyboardService.PREF_GLIDE, true));
        glide.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(SwipeKeyboardService.PREF_GLIDE, on).apply());
        glide.setPadding(0, px(8), 0, px(8));
        col.addView(glide);

        Switch fix = new Switch(this);
        fix.setText("Autocorrect");
        fix.setTextColor(Color.WHITE);
        fix.setTextSize(16);
        fix.setChecked(prefs.getBoolean(SwipeKeyboardService.PREF_AUTOCORRECT, true));
        fix.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(SwipeKeyboardService.PREF_AUTOCORRECT, on).apply());
        fix.setPadding(0, px(8), 0, px(8));
        col.addView(fix);
        TextView fixHelp = text("Fixes typos when you press space or punctuation. The fix is shown in blue "
                + "above the keys first. Press backspace straight after to undo it, or tap the word in quotes "
                + "to keep what you typed — it won't be corrected again.", 14);
        fixHelp.setTextColor(0xFFBBBBBB);
        col.addView(fixHelp);

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

        // --- Personalisation & memory
        col.addView(section("Personalisation"));
        Switch memory = new Switch(this);
        memory.setText("Keyboard memory");
        memory.setTextColor(Color.WHITE);
        memory.setTextSize(16);
        memory.setChecked(prefs.getBoolean(SwipeKeyboardService.PREF_MEMORY, true));
        memory.setPadding(0, px(8), 0, px(8));
        col.addView(memory);
        TextView memHelp = text("", 14);
        memHelp.setTextColor(0xFFBBBBBB);
        Runnable memText = () -> memHelp.setText(memory.isChecked()
                ? "On: Swipe Keys learns your words, which words you use together, where your fingers land on "
                + "each key, how much you like autocorrect, your recent emoji and recently copied text. "
                + "It all stays on this phone."
                : "Off: nothing new is learned or saved — no words, taps, emoji or copied text. What it already "
                + "knows still helps (delete it below if you want). Notes still save, because you save them on purpose. "
                + "You can also flip this from the eye button in the keyboard's tools.");
        memText.run();
        memory.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(SwipeKeyboardService.PREF_MEMORY, on).apply();
            memText.run();
        });
        col.addView(memHelp);
        personal = text("", 14);
        personal.setPadding(0, px(12), 0, 0);
        col.addView(personal);

        // --- Data
        col.addView(section("Your data"));
        TextView data = text("Everything stays on this phone, inside Swipe Keys' private storage — other apps "
                + "can't read it, it isn't backed up, and uninstalling deletes it.\n\n"
                + "• Words you type often and which words follow which: a file called learned.txt "
                + "(never from password boxes, or apps that ask for incognito typing)\n"
                + "• Where you tap each key, autocorrect history, notes, recent emoji and settings: "
                + "the app's preferences file\n"
                + "• Recently copied text: only in memory, gone when the keyboard restarts; "
                + "copies Android marks as sensitive (like passwords) are skipped\n"
                + "• The dictionary is built into the app\n\n"
                + "Voice typing is the one exception: the mic hands your speech to your phone's speech "
                + "service (usually Google), which may process it online.", 14);
        data.setTextColor(0xFFBBBBBB);
        col.addView(data);
        Button clear = new Button(this);
        clear.setText("Delete everything it has learned");
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Delete everything learned?")
                .setMessage("Learned words, your tap positions, autocorrect history, recent emoji and copied text "
                        + "are deleted. The keyboard goes back to how it was when installed. Notes are kept.")
                .setPositiveButton("Delete", (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    new java.io.File(getFilesDir(), "learned.txt").delete();
                    prefs.edit().remove(SwipeKeyboardService.PREF_TOUCH_MODEL)
                            .remove(SwipeKeyboardService.PREF_FIXES_KEPT)
                            .remove(SwipeKeyboardService.PREF_FIXES_UNDONE)
                            .remove("recent_emoji")
                            .putBoolean(SwipeKeyboardService.PREF_CLEAR_LEARNED, true).apply();
                    android.widget.Toast.makeText(this, "Deleted", android.widget.Toast.LENGTH_SHORT).show();
                    refreshPersonal();
                })
                .setNegativeButton("Cancel", null)
                .show());
        col.addView(clear);

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
        refreshPersonal();
    }

    /** A summary of how well the keyboard knows you. */
    private void refreshPersonal() {
        int words = 0, pairs = 0;
        java.io.File f = new java.io.File(getFilesDir(), "learned.txt");
        if (f.exists()) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("w\t")) words++;
                    else if (line.startsWith("n\t")) pairs++;
                }
            } catch (java.io.IOException ignored) { }
        }
        int keys = 0;
        for (String part : prefs.getString(SwipeKeyboardService.PREF_TOUCH_MODEL, "").split(";")) {
            String[] p = part.split(",");
            try {
                if (p.length == 4 && Integer.parseInt(p[3]) >= 5) keys++;
            } catch (NumberFormatException ignored) { }
        }
        int kept = prefs.getInt(SwipeKeyboardService.PREF_FIXES_KEPT, 0);
        int undone = prefs.getInt(SwipeKeyboardService.PREF_FIXES_UNDONE, 0);
        personal.setText("How well it knows you:\n"
                + "• " + words + " of your words and " + pairs + " word pairs learned\n"
                + "• " + keys + " of 26 letter keys tuned to where your fingers land\n"
                + "• Autocorrect: " + kept + " fixes kept, " + undone + " undone"
                + (undone > 0 ? " (it gets more careful when you undo)" : ""));
    }

    private TextView label(String s) {
        TextView t = text(s, 15);
        t.setTextColor(0xFFDDDDDD);
        t.setPadding(0, px(10), 0, px(4));
        return t;
    }

    /** Three-way choice saved as 0/1/2 under {@code key} (default 1). */
    private android.widget.RadioGroup choice(String key, String... options) {
        android.widget.RadioGroup g = new android.widget.RadioGroup(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        int current = prefs.getInt(key, 1);
        for (int i = 0; i < options.length; i++) {
            android.widget.RadioButton b = new android.widget.RadioButton(this);
            b.setText(options[i]);
            b.setTextColor(Color.WHITE);
            b.setId(View.generateViewId());
            g.addView(b, new android.widget.RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == current) b.setChecked(true);
            final int value = i;
            b.setOnCheckedChangeListener((btn, on) -> { if (on) prefs.edit().putInt(key, value).apply(); });
        }
        return g;
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
