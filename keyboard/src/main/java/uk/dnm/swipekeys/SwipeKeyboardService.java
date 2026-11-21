package uk.dnm.swipekeys;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import java.util.Collections;

public class SwipeKeyboardService extends InputMethodService
        implements KeyboardView.Listener, SuggestionBar.Listener {

    private static final String SMART_PUNCT = ".,!?;:";

    private KeyboardView view;
    private SuggestionBar bar;
    private Predictor predictor;
    private long lastSpaceAt;
    private boolean predict;     // show suggestions in this field
    private boolean learn;       // remember words typed in this field
    private boolean autoSpace;   // the last thing we did was add a space after a suggestion

    @Override
    public void onCreate() {
        super.onCreate();
        predictor = new Predictor(this);
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        bar = new SuggestionBar(this);
        bar.setListener(this);
        view = new KeyboardView(this);
        view.setListener(this);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // never take over the whole screen in landscape
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean secret = var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        boolean address = var == InputType.TYPE_TEXT_VARIATION_URI
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        predict = cls == InputType.TYPE_CLASS_TEXT && !secret && !address;
        learn = predict && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
        autoSpace = false;
        if (bar != null) bar.setVisibility(predict ? View.VISIBLE : View.GONE);
        if (view != null) view.setEditorInfo(info);
        updateShift();
        updateSuggestions();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        if (predictor != null) predictor.saveIfDirty();
    }

    @Override
    public void onDestroy() {
        if (predictor != null) predictor.saveIfDirty();
        super.onDestroy();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        updateShift();
        if (newSelStart != newSelEnd) {
            if (bar != null) bar.setSuggestions(Collections.emptyList());
        } else {
            updateSuggestions();
        }
    }

    private void updateShift() {
        if (view == null) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        boolean caps = ic != null && ei != null && ei.inputType != InputType.TYPE_NULL
                && ic.getCursorCapsMode(ei.inputType) != 0;
        view.setAutoShift(caps);
    }

    // ------------------------------------------------------------------ predictions

    private static boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'' || c == '’';
    }

    /** {previous word or null, partial word right before the cursor ("" if none)} */
    private String[] wordContext(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(64, 0);
        if (b == null) return new String[]{null, ""};
        String s = b.toString();
        int i = s.length();
        while (i > 0 && isWordChar(s.charAt(i - 1))) i--;
        while (i < s.length() && (s.charAt(i) == '\'' || s.charAt(i) == '’')) i++; // opening quote isn't part of the word
        String cur = s.substring(i);
        int j = i;
        while (j > 0 && s.charAt(j - 1) == ' ') j--;
        String prev = null;
        if (j < i) {
            int k = j;
            while (k > 0 && isWordChar(s.charAt(k - 1))) k--;
            if (k < j) prev = s.substring(k, j);
        }
        return new String[]{prev, cur};
    }

    private void updateSuggestions() {
        if (bar == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (!predict || ic == null) {
            bar.setSuggestions(Collections.emptyList());
            return;
        }
        CharSequence after = ic.getTextAfterCursor(1, 0);
        if (after != null && after.length() > 0 && isWordChar(after.charAt(0))) {
            bar.setSuggestions(Collections.emptyList()); // cursor is in the middle of a word
            return;
        }
        String[] cx = wordContext(ic);
        bar.setSuggestions(predictor.suggest(cx[0], cx[1]));
    }

    @Override
    public void onPick(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] cx = wordContext(ic);
        ic.beginBatchEdit();
        if (!cx[1].isEmpty()) ic.deleteSurroundingText(cx[1].length(), 0);
        CharSequence after = ic.getTextAfterCursor(1, 0);
        boolean spaceAfter = after != null && after.length() > 0 && after.charAt(0) == ' ';
        ic.commitText(spaceAfter ? word : word + " ", 1);
        ic.endBatchEdit();
        if (learn) predictor.learn(cx[0], word);
        autoSpace = !spaceAfter;
        lastSpaceAt = 0;
        updateShift();
    }

    // ------------------------------------------------------------------ keys

    @Override
    public void onText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean separator = text.length() == 1 && !isWordChar(text.charAt(0));

        // "word " + "," → "word, " after picking a suggestion
        if (autoSpace && SMART_PUNCT.contains(text)) {
            CharSequence before = ic.getTextBeforeCursor(1, 0);
            if (before != null && before.length() == 1 && before.charAt(0) == ' ') {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(1, 0);
                ic.commitText(text + " ", 1);
                ic.endBatchEdit();
                autoSpace = false;
                lastSpaceAt = 0;
                updateShift();
                return;
            }
        }
        autoSpace = false;

        if (separator && learn) {
            String[] cx = wordContext(ic);
            if (!cx[1].isEmpty()) predictor.learn(cx[0], cx[1]);
        }

        if (" ".equals(text)) {
            long now = SystemClock.uptimeMillis();
            // Double-tap space types ". " like on iOS
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (now - lastSpaceAt < 450 && before != null && before.length() == 2
                    && before.charAt(1) == ' ' && Character.isLetterOrDigit(before.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                lastSpaceAt = 0;
            } else {
                ic.commitText(" ", 1);
                lastSpaceAt = now;
            }
        } else {
            ic.commitText(text, 1);
            lastSpaceAt = 0;
        }
        updateShift();
    }

    @Override
    public void onDelete() {
        autoSpace = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        updateShift();
    }

    @Override
    public void onEnter() {
        autoSpace = false;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null) return;
        if (learn) {
            String[] cx = wordContext(ic);
            if (!cx[1].isEmpty()) predictor.learn(cx[0], cx[1]);
        }
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        updateShift();
    }

    @Override
    public void onCursor(int steps) {
        autoSpace = false;
        int code = steps > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        for (int i = 0; i < Math.abs(steps); i++) sendDownUpKeyEvents(code);
    }

    @Override
    public void onSwitchIme() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showInputMethodPicker();
    }
}
