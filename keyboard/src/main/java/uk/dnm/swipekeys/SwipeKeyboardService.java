package uk.dnm.swipekeys;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwipeKeyboardService extends InputMethodService
        implements KeyboardView.Listener, SuggestionBar.Listener, EmojiPanel.Listener, NotesPanel.Listener {

    static final String PREFS = "swipekeys";
    static final String PREF_GLIDE = "glide";
    static final String PREF_ONE_HAND = "one_hand";
    static final String PREF_AUTOCORRECT = "autocorrect";
    static final String PREF_CLEAR_LEARNED = "clear_learned";

    private static final String SMART_PUNCT = ".,!?;:";
    private static final int PANEL_NONE = 0, PANEL_EMOJI = 1, PANEL_NOTES = 2;
    private static final int MAX_CLIPS = 15;

    private SharedPreferences prefs;
    private KeyboardView view;
    private SuggestionBar bar;
    private FrameLayout content;
    private EmojiPanel emojiPanel;
    private NotesPanel notesPanel;
    private int panel = PANEL_NONE;

    private Predictor predictor;
    private GlideDecoder glide;
    private List<String> glideWords;   // alternatives for the word just swiped, best first
    private String glidedWord;

    private long lastSpaceAt;
    private boolean predict;     // show suggestions in this field
    private boolean learn;       // remember words typed in this field
    private boolean secret;      // password field
    private boolean autoSpace;   // the last thing we did was add a space after a suggestion
    private boolean autocorrect; // fix typos when a word is finished

    // The last autocorrection, so backspace can undo it
    private String corrOriginal, corrFixed, corrSep, corrPrev;
    private final java.util.HashSet<String> rejected = new java.util.HashSet<>(); // undone this session

    private ClipboardManager clipboard;
    private final List<String> clips = new ArrayList<>();
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::captureClip;

    private SpeechRecognizer recognizer;
    private boolean listening;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        predictor = new Predictor(this);
        glide = new GlideDecoder(predictor);
        clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(clipListener);
    }

    @Override
    public void onDestroy() {
        if (predictor != null) predictor.saveIfDirty();
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        bar = new SuggestionBar(this);
        bar.setListener(this);
        view = new KeyboardView(this);
        view.setListener(this);
        // Panels (emoji, notes) always get exactly the height of the keys they replace
        content = new FrameLayout(this) {
            @Override
            protected void onMeasure(int ws, int hs) {
                view.measure(ws, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                super.onMeasure(ws, View.MeasureSpec.makeMeasureSpec(view.getMeasuredHeight(), View.MeasureSpec.EXACTLY));
            }
        };
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emojiPanel = null;
        notesPanel = null;
        panel = PANEL_NONE;
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        secret = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean address = var == InputType.TYPE_TEXT_VARIATION_URI
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        predict = cls == InputType.TYPE_CLASS_TEXT && !secret && !address;
        learn = predict && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
        autocorrect = predict && prefs.getBoolean(PREF_AUTOCORRECT, true)
                && (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0;
        if (prefs.getBoolean(PREF_CLEAR_LEARNED, false)) {
            predictor.clearLearned();
            rejected.clear();
            prefs.edit().remove(PREF_CLEAR_LEARNED).apply();
        }
        clearCorrection();
        autoSpace = false;
        clearGlide();
        if (!restarting) showPanel(PANEL_NONE);
        if (view != null) {
            view.setEditorInfo(info);
            view.setGlideEnabled(predict && prefs.getBoolean(PREF_GLIDE, true));
            view.setOneHand(prefs.getInt(PREF_ONE_HAND, KeyboardView.HAND_OFF));
        }
        updateShift();
        updateSuggestions();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        stopVoice(true);
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        if (predictor != null) predictor.saveIfDirty();
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
        view.setAutoShift(capsNow());
    }

    private boolean capsNow() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        return ic != null && ei != null && ei.inputType != InputType.TYPE_NULL
                && ic.getCursorCapsMode(ei.inputType) != 0;
    }

    // ------------------------------------------------------------------ panels

    private void showPanel(int p) {
        panel = p;
        if (content == null) return;
        if (p == PANEL_EMOJI && emojiPanel == null) {
            emojiPanel = new EmojiPanel(this);
            emojiPanel.setListener(this);
            content.addView(emojiPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (p == PANEL_NOTES && notesPanel == null) {
            notesPanel = new NotesPanel(this);
            notesPanel.setListener(this);
            content.addView(notesPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        // The keys stay laid out (just hidden) so the panels keep the same height
        view.setVisibility(p == PANEL_NONE ? View.VISIBLE : View.INVISIBLE);
        if (emojiPanel != null) emojiPanel.setVisibility(p == PANEL_EMOJI ? View.VISIBLE : View.GONE);
        if (notesPanel != null) notesPanel.setVisibility(p == PANEL_NOTES ? View.VISIBLE : View.GONE);
        if (p == PANEL_EMOJI) emojiPanel.refresh();
        if (p == PANEL_NOTES) {
            captureClip();
            notesPanel.refresh(clips);
        }
        if (bar != null) {
            bar.setActiveTool(p == PANEL_EMOJI ? SuggestionBar.TOOL_EMOJI : p == PANEL_NOTES ? SuggestionBar.TOOL_NOTES : -1);
            if (p == PANEL_NONE) bar.closeTools();
        }
    }

    @Override
    public void onTool(int tool) {
        switch (tool) {
            case SuggestionBar.TOOL_EMOJI:
                showPanel(panel == PANEL_EMOJI ? PANEL_NONE : PANEL_EMOJI);
                break;
            case SuggestionBar.TOOL_NOTES:
                showPanel(panel == PANEL_NOTES ? PANEL_NONE : PANEL_NOTES);
                break;
            case SuggestionBar.TOOL_ONE_HAND: {
                showPanel(PANEL_NONE);
                int side = view.getOneHand() == KeyboardView.HAND_OFF ? KeyboardView.HAND_RIGHT : KeyboardView.HAND_OFF;
                view.setOneHand(side);
                onOneHandChanged(side);
                break;
            }
            case SuggestionBar.TOOL_SETTINGS:
                openApp(null);
                break;
            default: // tools strip closed
                showPanel(PANEL_NONE);
                break;
        }
    }

    private void openApp(String extra) {
        Intent i = new Intent(this, SetupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (extra != null) i.putExtra(extra, true);
        startActivity(i);
    }

    @Override
    public void onOneHandChanged(int side) {
        prefs.edit().putInt(PREF_ONE_HAND, side).apply();
    }

    @Override
    public void onEmojiKey() {
        showPanel(PANEL_EMOJI);
    }

    @Override
    public void onEmoji(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        autoSpace = false;
        clearGlide();
        clearCorrection();
        ic.commitText(emoji, 1);
    }

    @Override
    public void onAbc() {
        showPanel(PANEL_NONE);
    }

    @Override
    public void onPaste(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        autoSpace = false;
        clearGlide();
        clearCorrection();
        ic.commitText(text, 1);
    }

    @Override
    public void onSaveField() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || secret) {
            Toast.makeText(this, "Can't save from this box", Toast.LENGTH_SHORT).show();
            return;
        }
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        CharSequence t = et != null ? et.text : null;
        if (t == null || t.toString().trim().isEmpty()) {
            Toast.makeText(this, "The text box is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Notes.add(this, t.toString().trim());
        Toast.makeText(this, "Saved to notes", Toast.LENGTH_SHORT).show();
        if (notesPanel != null) notesPanel.refresh(clips);
    }

    @Override
    public void onNewNote() {
        openApp(SetupActivity.EXTRA_NEW_NOTE);
    }

    private void captureClip() {
        if (clipboard == null) return;
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            ClipDescription d = clip.getDescription();
            if (Build.VERSION.SDK_INT >= 33 && d != null && d.getExtras() != null
                    && d.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)) {
                return; // passwords etc.
            }
            CharSequence t = clip.getItemAt(0).coerceToText(this);
            if (t == null) return;
            String s = t.toString();
            if (s.trim().isEmpty() || s.length() > 5000) return;
            clips.remove(s);
            clips.add(0, s);
            while (clips.size() > MAX_CLIPS) clips.remove(clips.size() - 1);
        } catch (SecurityException ignored) {
            // Android only lets the keyboard in use read the clipboard
        }
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
        if (bar == null || listening) return;
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
        if (glideWords != null && cx[1].equals(glidedWord)) {
            bar.setSuggestions(glideWords); // other guesses for the word just swiped
            return;
        }
        clearGlide();
        List<String> list = predictor.suggest(cx[0], cx[1]);
        String fix = autocorrect && !cx[1].isEmpty() && !rejected.contains(cx[1].toLowerCase())
                ? predictor.autocorrect(cx[0], cx[1]) : null;
        if (fix != null && !fix.equals(cx[1])) {
            // Middle: what space will turn it into. Left: keep exactly what was typed.
            List<String> withFix = new ArrayList<>();
            withFix.add(fix);
            withFix.add("“" + cx[1] + "”");
            for (String w : list) if (!w.equals(fix) && withFix.size() < 3) withFix.add(w);
            bar.setSuggestions(withFix, true);
        } else {
            bar.setSuggestions(list, false);
        }
    }

    private void clearCorrection() {
        corrOriginal = corrFixed = corrSep = corrPrev = null;
    }

    /** Fix the word before the cursor if it's a typo. Returns {original, fixed, previous word} or null. */
    private String[] applyAutocorrect(InputConnection ic) {
        String[] cx = wordContext(ic);
        String cur = cx[1];
        if (cur.isEmpty() || rejected.contains(cur.toLowerCase())) return null;
        String fix = predictor.autocorrect(cx[0], cur);
        if (fix == null || fix.equals(cur)) return null;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(cur.length(), 0);
        ic.commitText(fix, 1);
        ic.endBatchEdit();
        return new String[]{cur, fix, cx[0]};
    }

    private void clearGlide() {
        glideWords = null;
        glidedWord = null;
    }

    @Override
    public void onPick(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String[] cx = wordContext(ic);
        clearGlide();
        clearCorrection();
        boolean literal = word.length() > 2 && word.startsWith("“") && word.endsWith("”");
        if (literal) {
            // "Keep what I typed": stop correcting this word
            word = word.substring(1, word.length() - 1);
            rejected.add(word.toLowerCase());
            if (learn) predictor.markKnown(cx[0], word);
        }
        ic.beginBatchEdit();
        if (!cx[1].isEmpty()) ic.deleteSurroundingText(cx[1].length(), 0);
        CharSequence after = ic.getTextAfterCursor(1, 0);
        boolean spaceAfter = after != null && after.length() > 0 && after.charAt(0) == ' ';
        ic.commitText(spaceAfter ? word : word + " ", 1);
        ic.endBatchEdit();
        if (learn && !literal) predictor.learn(cx[0], word);
        autoSpace = !spaceAfter;
        lastSpaceAt = 0;
        updateShift();
    }

    @Override
    public void onGlide(float[] xs, float[] ys, int n, int shift) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || view == null) return;
        clearCorrection();
        String[] cx = wordContext(ic);
        String prev = cx[0];
        if (!cx[1].isEmpty()) {
            // Swiping straight after another word: that one is finished
            if (learn) predictor.learn(cx[0], cx[1]);
            prev = cx[1];
        }
        List<String> words = glide.decode(xs, ys, n, view.letterX(), view.letterY(),
                view.keyWidth(), view.keyHeight(), prev, 4);
        if (words.isEmpty()) return;

        List<String> cased = new ArrayList<>();
        for (String w : words) {
            String c = w;
            if (shift == KeyboardView.SHIFT_LOCK) c = w.toUpperCase();
            else if (shift == KeyboardView.SHIFT_ON) c = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            if (!cased.contains(c)) cased.add(c);
        }
        String best = cased.get(0);

        CharSequence before = ic.getTextBeforeCursor(1, 0);
        boolean needSpace = autoSpace ? false : before != null && before.length() > 0
                && !Character.isWhitespace(before.charAt(0)) && "([{\"'“‘/@#-".indexOf(before.charAt(0)) < 0;
        autoSpace = false;
        lastSpaceAt = 0;
        glideWords = cased;
        glidedWord = best;
        ic.commitText(needSpace ? " " + best : best, 1);
        bar.setSuggestions(cased);
        updateShift();
    }

    // ------------------------------------------------------------------ voice

    @Override
    public void onMic() {
        if (listening) {
            stopVoice(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Allow the microphone for Swipe Keys first", Toast.LENGTH_SHORT).show();
            openApp(SetupActivity.EXTRA_ASK_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice typing isn't available on this phone", Toast.LENGTH_LONG).show();
            return;
        }
        showPanel(PANEL_NONE);
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(voiceListener);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        listening = true;
        bar.setListening(true, "");
        recognizer.startListening(i);
    }

    private void stopVoice(boolean cancel) {
        if (!listening) return;
        if (recognizer != null) {
            if (cancel) recognizer.cancel(); else recognizer.stopListening();
        }
        if (cancel) {
            listening = false;
            if (bar != null) bar.setListening(false, "");
        }
    }

    private void endVoice() {
        listening = false;
        if (bar != null) bar.setListening(false, "");
        updateSuggestions();
    }

    private final RecognitionListener voiceListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override public void onPartialResults(Bundle b) {
            String t = first(b);
            if (t != null && listening) bar.setListening(true, t);
        }

        @Override public void onResults(Bundle b) {
            String t = first(b);
            InputConnection ic = getCurrentInputConnection();
            if (t != null && !t.isEmpty() && ic != null) {
                if (capsNow()) t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
                CharSequence before = ic.getTextBeforeCursor(1, 0);
                boolean needSpace = before != null && before.length() > 0 && !Character.isWhitespace(before.charAt(0));
                clearGlide();
                autoSpace = false;
                ic.commitText(needSpace ? " " + t : t, 1);
            }
            endVoice();
        }

        @Override public void onError(int error) {
            endVoice();
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Toast.makeText(SwipeKeyboardService.this, "Allow the microphone for Swipe Keys first", Toast.LENGTH_SHORT).show();
            } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    || error == SpeechRecognizer.ERROR_SERVER) {
                Toast.makeText(SwipeKeyboardService.this, "Voice typing needs a connection", Toast.LENGTH_SHORT).show();
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                Toast.makeText(SwipeKeyboardService.this, "The microphone is busy", Toast.LENGTH_SHORT).show();
            }
        }

        private String first(Bundle b) {
            if (b == null) return null;
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return r == null || r.isEmpty() ? null : r.get(0);
        }
    };

    // ------------------------------------------------------------------ keys

    @Override
    public void onText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean separator = text.length() == 1 && !isWordChar(text.charAt(0));
        clearGlide();
        clearCorrection();

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

        String[] corr = separator && autocorrect ? applyAutocorrect(ic) : null;
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
        if (corr != null) {
            corrOriginal = corr[0];
            corrFixed = corr[1];
            corrPrev = corr[2];
            corrSep = text;
        }
        updateShift();
    }

    @Override
    public void onDelete() {
        autoSpace = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (corrFixed != null) {
            // Backspace straight after an autocorrection puts back what was typed
            String fixedPart = corrFixed + corrSep;
            CharSequence before = ic.getTextBeforeCursor(fixedPart.length(), 0);
            if (before != null && before.toString().equals(fixedPart)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(fixedPart.length(), 0);
                ic.commitText(corrOriginal + corrSep, 1);
                ic.endBatchEdit();
                rejected.add(corrOriginal.toLowerCase());
                if (learn) predictor.markKnown(corrPrev, corrOriginal);
                clearCorrection();
                return;
            }
            clearCorrection();
        }
        CharSequence sel = ic.getSelectedText(0);
        if (glideWords != null && (sel == null || sel.length() == 0)) {
            // Backspace right after a swipe removes the whole word
            String[] cx = wordContext(ic);
            if (cx[1].equals(glidedWord)) {
                ic.deleteSurroundingText(glidedWord.length(), 0);
                clearGlide();
                updateShift();
                return;
            }
        }
        clearGlide();
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        updateShift();
    }

    @Override
    public void onEnter() {
        autoSpace = false;
        clearGlide();
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null) return;
        clearCorrection();
        if (autocorrect) applyAutocorrect(ic);
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
        clearGlide();
        clearCorrection();
        int code = steps > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        for (int i = 0; i < Math.abs(steps); i++) sendDownUpKeyEvents(code);
    }

    @Override
    public void onSwitchIme() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showInputMethodPicker();
    }
}
