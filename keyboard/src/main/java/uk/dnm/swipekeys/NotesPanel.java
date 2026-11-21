package uk.dnm.swipekeys;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Saved notes and recently copied text. Tap to paste. Takes the place of the keys. */
public class NotesPanel extends LinearLayout {

    public interface Listener {
        void onPaste(String text);
        void onAbc();
        /** Save whatever is in the text box being typed into. */
        void onSaveField();
        /** Open the app to write a new note. */
        void onNewNote();
    }

    private Listener listener;
    private final LinearLayout list;
    private List<String> clips;
    private String armedDelete; // a note waiting for a second tap to delete
    private int colBg, colText, colDim, colCard, colBar, colDanger;

    public NotesPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        applyTheme();
        setBackgroundColor(colBg);

        ScrollView scroll = new ScrollView(context);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        int p = px(10);
        list.setPadding(p, px(4), p, p);
        scroll.addView(list);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setBackgroundColor(colBar);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(barButton("ABC", v -> { if (listener != null) listener.onAbc(); }),
                new LayoutParams(px(64), ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(barButton("Save text box", v -> { if (listener != null) listener.onSaveField(); }),
                new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(barButton("New note…", v -> { if (listener != null) listener.onNewNote(); }),
                new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46)));
    }

    public void setListener(Listener l) { listener = l; }

    /** Rebuild the list. {@code clipboard} is newest first. */
    public void refresh(List<String> clipboard) {
        clips = clipboard;
        armedDelete = null;
        rebuild();
    }

    private void rebuild() {
        list.removeAllViews();
        List<String> notes = Notes.load(getContext());

        list.addView(header("Notes"));
        if (notes.isEmpty()) {
            list.addView(hint("Save something you type often, like your address or email. "
                    + "Tap a note to paste it; hold it to delete."));
        }
        for (String n : notes) {
            boolean armed = n.equals(armedDelete);
            TextView row = row(armed ? "Tap again to delete" : n, armed);
            row.setOnClickListener(v -> {
                if (n.equals(armedDelete)) {
                    Notes.remove(getContext(), n);
                    armedDelete = null;
                    rebuild();
                } else if (armedDelete != null) {
                    armedDelete = null;
                    rebuild();
                } else if (listener != null) {
                    listener.onPaste(n);
                }
            });
            row.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                armedDelete = n;
                rebuild();
                return true;
            });
            list.addView(row);
        }

        list.addView(header("Recently copied"));
        if (clips == null || clips.isEmpty()) {
            list.addView(hint("Text you copy shows up here while the keyboard is on. Hold one to save it as a note."));
        } else {
            for (String c : clips) {
                TextView row = row(c, false);
                row.setOnClickListener(v -> { if (listener != null) listener.onPaste(c); });
                row.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    Notes.add(getContext(), c);
                    Toast.makeText(getContext(), "Saved to notes", Toast.LENGTH_SHORT).show();
                    rebuild();
                    return true;
                });
                list.addView(row);
            }
        }
    }

    private TextView header(String s) {
        TextView t = new TextView(getContext());
        t.setText(s.toUpperCase());
        t.setTextSize(13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(colDim);
        t.setPadding(px(4), px(10), px(4), px(6));
        return t;
    }

    private TextView hint(String s) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(colDim);
        t.setPadding(px(4), 0, px(4), px(6));
        return t;
    }

    private TextView row(String s, boolean danger) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(danger ? Color.WHITE : colText);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(14), px(11), px(14), px(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(danger ? colDanger : colCard);
        bg.setCornerRadius(px(10));
        t.setBackground(bg);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView barButton(String s, View.OnClickListener l) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(colText);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        if (tv.resourceId != 0) t.setBackgroundResource(tv.resourceId);
        t.setOnClickListener(l);
        return t;
    }

    private void applyTheme() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            colBg = 0xFF1F1F21; colText = Color.WHITE; colDim = 0xFF9C9CA1; colCard = 0xFF3A3A3D; colBar = 0xFF2A2A2D;
        } else {
            colBg = 0xFFD1D4DA; colText = Color.BLACK; colDim = 0xFF6C6C70; colCard = Color.WHITE; colBar = 0xFFC3C7CE;
        }
        colDanger = 0xFFE5484D;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
