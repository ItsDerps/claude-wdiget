package uk.dnm.swipekeys;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public class SwipeKeyboardService extends InputMethodService implements KeyboardView.Listener {

    private KeyboardView view;
    private long lastSpaceAt;

    @Override
    public View onCreateInputView() {
        view = new KeyboardView(this);
        view.setListener(this);
        return view;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // never take over the whole screen in landscape
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (view != null) view.setEditorInfo(info);
        updateShift();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        updateShift();
    }

    private void updateShift() {
        if (view == null) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        boolean caps = ic != null && ei != null && ei.inputType != InputType.TYPE_NULL
                && ic.getCursorCapsMode(ei.inputType) != 0;
        view.setAutoShift(caps);
    }

    @Override
    public void onText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
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
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        updateShift();
    }

    @Override
    public void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null) return;
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
        int code = steps > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        for (int i = 0; i < Math.abs(steps); i++) sendDownUpKeyEvents(code);
    }

    @Override
    public void onSwitchIme() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showInputMethodPicker();
    }
}
