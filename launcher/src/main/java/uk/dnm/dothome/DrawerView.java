package uk.dnm.dothome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The app drawer: search on top, suggested apps, then every app A–Z with a dot scrubber. */
public class DrawerView extends FrameLayout {

    interface Host extends AppIconView.Listener {
        void launchIntent(Intent i, View from);
        void launchApp(Apps.App a, View from);
        Set<String> badges();
    }

    private final Host host;
    private final EditText search;
    private final ImageView clear;
    private final ScrollView list, results;
    private final LinearLayout listBody, resultsBody;
    private final ScrubberView scrubber;
    private final Map<Character, View> sectionRows = new HashMap<>();
    private final List<View> revealRows = new java.util.ArrayList<>();
    private SearchEngine.Results last;
    private int insetTop, insetBottom;

    public DrawerView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(0xFF050505);
        setClickable(true);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- search pill
        FrameLayout pill = new FrameLayout(c);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF151515);
        pbg.setStroke(Ui.dpi(c, 1), Ui.LINE);
        pbg.setCornerRadius(Ui.dp(c, 24));
        pill.setBackground(pbg);
        pillBg = pbg;
        search = new EditText(c);
        search.setBackground(null);
        search.setHint("Search apps, settings, web");
        search.setHintTextColor(0xFF6A6A6A);
        search.setTextColor(Ui.WHITE);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setPadding(Ui.dpi(c, 44), 0, Ui.dpi(c, 44), 0);
        search.setCompoundDrawablePadding(0);
        pill.addView(search, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dpi(c, 46)));
        ImageView glass = new ImageView(c);
        glass.setImageDrawable(new Glyph(Glyph.SEARCH, Ui.dpi(c, 18), 0xFF888888));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(Ui.dpi(c, 18), Ui.dpi(c, 18), Gravity.CENTER_VERTICAL | Gravity.START);
        glp.leftMargin = Ui.dpi(c, 16);
        pill.addView(glass, glp);
        clear = new ImageView(c);
        clear.setImageDrawable(new Glyph(Glyph.CLEAR, Ui.dpi(c, 18), 0xFF888888));
        clear.setPadding(Ui.dpi(c, 12), Ui.dpi(c, 12), Ui.dpi(c, 12), Ui.dpi(c, 12));
        clear.setVisibility(GONE);
        clear.setOnClickListener(v -> search.setText(""));
        pill.addView(clear, new FrameLayout.LayoutParams(Ui.dpi(c, 44), Ui.dpi(c, 44), Gravity.CENTER_VERTICAL | Gravity.END));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dpi(c, 46));
        plp.setMargins(Ui.dpi(c, 14), Ui.dpi(c, 10), Ui.dpi(c, 14), Ui.dpi(c, 4));
        column.addView(pill, plp);

        // ---- list + results share the space below
        FrameLayout body = new FrameLayout(c);
        column.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        list = scroll(c);
        listBody = new LinearLayout(c);
        listBody.setOrientation(LinearLayout.VERTICAL);
        listBody.setClipChildren(false);
        list.addView(listBody);
        body.addView(list);
        results = scroll(c);
        resultsBody = new LinearLayout(c);
        resultsBody.setOrientation(LinearLayout.VERTICAL);
        results.addView(resultsBody);
        results.setVisibility(GONE);
        body.addView(results);

        scrubber = new ScrubberView(c);
        scrubber.setListener(this::jumpTo);
        body.addView(scrubber, new FrameLayout.LayoutParams(Ui.dpi(c, 140), LayoutParams.MATCH_PARENT, Gravity.END));
        scrubber.setVisibility(GONE); // the app dial on the right edge replaces the A–Z strip

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) { onQuery(s.toString()); }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                runTopHit();
                return true;
            }
            return false;
        });
    }

    private ScrollView scroll(Context c) {
        ScrollView s = new ScrollView(c);
        s.setVerticalScrollBarEnabled(false);
        s.setOverScrollMode(OVER_SCROLL_NEVER);
        s.setClipToPadding(false);
        return s;
    }

    private GradientDrawable pillBg;

    /** Background, search box and labels follow the theme. */
    void applyTheme() {
        Theme t = Theme.get(getContext());
        setBackgroundColor(t.light ? 0xFFF4F4F1 : 0xFF050505);
        if (pillBg != null) {
            pillBg.setColor(t.light ? Ui.WHITE : 0xFF151515);
            pillBg.setStroke(Ui.dpi(getContext(), 1), t.surfaceLine);
        }
        search.setTextColor(t.text);
        search.setHintTextColor(t.textFaint);
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        setPadding(0, top, 0, 0);
        int pad = bottom + Ui.dpi(getContext(), 16);
        list.setPadding(0, 0, 0, pad);
        results.setPadding(0, 0, 0, pad);
        ((FrameLayout.LayoutParams) scrubber.getLayoutParams()).bottomMargin = bottom;
        scrubber.requestLayout();
    }

    // ------------------------------------------------------------------ list

    void build(List<Apps.App> apps, List<Apps.App> suggested, boolean labels) {
        Context c = getContext();
        listBody.removeAllViews();
        sectionRows.clear();
        revealRows.clear();
        Set<String> badges = host.badges();

        listBody.addView(sectionLabel("SUGGESTED"));
        LinearLayout sugg = new LinearLayout(c);
        sugg.setPadding(Ui.dpi(c, 8), 0, Ui.dpi(c, 30), 0);
        for (Apps.App a : suggested) sugg.addView(icon(a, labels, badges), new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        for (int i = suggested.size(); i < 4; i++) sugg.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        listBody.addView(sugg);
        revealRows.add(sugg);

        Set<Character> present = new HashSet<>();
        char current = 0;
        LinearLayout row = null;
        int inRow = 0;
        for (Apps.App a : apps) {
            if (a.section != current) {
                current = a.section;
                present.add(current);
                row = newRow(c, String.valueOf(current));
                sectionRows.put(current, row);
                inRow = 0;
            } else if (inRow == 4) {
                row = newRow(c, "");
                inRow = 0;
            }
            row.addView(icon(a, labels, badges), new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            inRow++;
            padRow(row);
        }
        scrubber.setPresent(present);
    }

    private LinearLayout newRow(Context c, String letter) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dpi(c, 4), Ui.dpi(c, 6), Ui.dpi(c, 30), 0);
        row.setClipChildren(false);
        TextView l = new TextView(c);
        l.setText(letter);
        l.setTypeface(Ui.dots(c));
        l.setTextSize(18);
        l.setTextColor(Ui.DIM);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, Ui.dpi(c, 14), 0, 0);
        row.addView(l, new LinearLayout.LayoutParams(Ui.dpi(c, 26), LayoutParams.WRAP_CONTENT));
        listBody.addView(row);
        if (revealRows.size() < 9) revealRows.add(row);
        return row;
    }

    /** Keep every row's cells the same width, even when it holds fewer than four apps. */
    private void padRow(LinearLayout row) {
        for (int i = row.getChildCount() - 1; i >= 1; i--) {
            if ("pad".equals(row.getChildAt(i).getTag())) row.removeViewAt(i);
        }
        int apps = row.getChildCount() - 1;
        for (int i = apps; i < 4; i++) {
            View pad = new View(getContext());
            pad.setTag("pad");
            row.addView(pad, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private AppIconView icon(Apps.App a, boolean labels, Set<String> badges) {
        AppIconView v = new AppIconView(getContext(), 42, labels);
        v.bind(a, host);
        v.setBadge(badges.contains(a.pkg()));
        return v;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTypeface(Ui.mono(getContext()));
        t.setTextSize(9.5f);
        t.setLetterSpacing(0.18f);
        t.setTextColor(0xFF777777);
        t.setPadding(Ui.dpi(getContext(), 18), Ui.dpi(getContext(), 14), 0, Ui.dpi(getContext(), 4));
        return t;
    }

    private void jumpTo(char letter) {
        View row = sectionRows.get(letter);
        if (row != null) list.smoothScrollTo(0, Math.max(0, row.getTop() - Ui.dpi(getContext(), 6)));
    }

    void updateBadges(Set<String> pkgs) {
        updateBadges(listBody, pkgs);
    }

    private void updateBadges(ViewGroup g, Set<String> pkgs) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof AppIconView) {
                AppIconView a = (AppIconView) v;
                if (a.app != null) a.setBadge(pkgs.contains(a.app.pkg()));
            } else if (v instanceof ViewGroup) {
                updateBadges((ViewGroup) v, pkgs);
            }
        }
    }

    /** While the drawer is being pulled open: rows rise in one after another. */
    void setReveal(float p) {
        boolean full = Ui.motion(getContext()) != Ui.MOTION_FULL;
        for (int i = 0; i < revealRows.size(); i++) {
            float r = full ? 1 : Ui.clamp((p - 0.3f - i * 0.06f) / 0.3f, 0, 1);
            View v = revealRows.get(i);
            v.setAlpha(r);
            v.setTranslationY((1 - r) * Ui.dp(getContext(), 18));
        }
    }

    // ------------------------------------------------------------------ search

    /** Pull-down-to-close only when the visible list is scrolled to the top. */
    boolean atTop() {
        ScrollView s = results.getVisibility() == VISIBLE ? results : list;
        return s.getScrollY() <= 0;
    }

    void opened(boolean keyboard) {
        if (keyboard) {
            search.requestFocus();
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            search.post(() -> imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    void closed() {
        hideKeyboard();
        search.setText("");
        search.clearFocus();
        list.scrollTo(0, 0);
    }

    void hideKeyboard() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void onQuery(String q) {
        clear.setVisibility(q.isEmpty() ? GONE : VISIBLE);
        if (q.trim().isEmpty()) {
            results.setVisibility(GONE);
            list.setVisibility(VISIBLE);
            last = null;
            return;
        }
        Apps repo = Apps.get(getContext());
        last = SearchEngine.search(q, repo.visible(), repo);
        renderResults(last);
        list.setVisibility(GONE);
        scrubber.setVisibility(GONE);
        results.setVisibility(VISIBLE);
        results.scrollTo(0, 0);
    }

    private void runTopHit() {
        if (last == null || last.topHit == null) return;
        if (last.topHit instanceof Apps.App) host.launchApp((Apps.App) last.topHit, search);
        else if (last.topHit instanceof SearchEngine.Action) runAction((SearchEngine.Action) last.topHit);
    }

    private void runAction(SearchEngine.Action a) {
        host.launchIntent(a.intent, search);
    }

    private void renderResults(SearchEngine.Results r) {
        Context c = getContext();
        resultsBody.removeAllViews();
        boolean labels = Prefs.get(c).labels();

        // Top hit card
        if (r.topHit instanceof Apps.App) {
            Apps.App a = (Apps.App) r.topHit;
            resultsBody.addView(topHit(a));
        }

        if (r.answer != null) resultsBody.addView(answerCard(r));

        if (!r.actions.isEmpty()) {
            for (SearchEngine.Action a : r.actions) {
                resultsBody.addView(actionRow(a, a.title.startsWith("Start") ? Glyph.TIMER : a.title.startsWith("Set") ? Glyph.ALARM : Glyph.CALL, true));
            }
        }

        List<Apps.App> rest = new java.util.ArrayList<>(r.apps);
        if (r.topHit instanceof Apps.App) rest.remove(r.topHit);
        if (!rest.isEmpty()) {
            resultsBody.addView(sectionLabel("APPS"));
            LinearLayout row = null;
            for (int i = 0; i < rest.size(); i++) {
                if (i % 4 == 0) {
                    row = new LinearLayout(c);
                    row.setPadding(Ui.dpi(c, 8), 0, Ui.dpi(c, 8), 0);
                    resultsBody.addView(row);
                }
                row.addView(icon(rest.get(i), labels, host.badges()), new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            }
            int fill = rest.size() % 4 == 0 ? 0 : 4 - rest.size() % 4;
            for (int i = 0; i < fill; i++) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        }

        if (!r.settings.isEmpty()) {
            resultsBody.addView(sectionLabel("SETTINGS"));
            for (SearchEngine.Action a : r.settings) resultsBody.addView(actionRow(a, Glyph.SETTINGS, false));
        }
        resultsBody.addView(sectionLabel("WEB"));
        resultsBody.addView(actionRow(r.web, Glyph.WEB, false));
    }

    private View topHit(Apps.App a) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dpi(c, 6), Ui.dpi(c, 4), Ui.dpi(c, 14), Ui.dpi(c, 4));
        card.setBackground(rounded(0xFF141414, 0xFF262626, 20));
        AppIconView icon = new AppIconView(c, 44, false);
        icon.bind(a, host);
        card.addView(icon);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(a.label);
        t.setTextColor(Ui.WHITE);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView s = new TextView(c);
        s.setText("Press go to open");
        s.setTextColor(0xFF888888);
        s.setTextSize(11);
        text.addView(t);
        text.addView(s);
        card.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView go = new TextView(c);
        go.setText("GO");
        go.setTypeface(Ui.mono(c), Typeface.BOLD);
        go.setTextSize(10);
        go.setLetterSpacing(0.1f);
        go.setTextColor(Ui.WHITE);
        go.setPadding(Ui.dpi(c, 10), Ui.dpi(c, 4), Ui.dpi(c, 10), Ui.dpi(c, 4));
        go.setBackground(rounded(Ui.RED, 0, 12));
        card.addView(go);
        card.setOnClickListener(v -> host.launchApp(a, icon));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dpi(c, 14), Ui.dpi(c, 10), Ui.dpi(c, 14), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private View answerCard(SearchEngine.Results r) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dpi(c, 18), Ui.dpi(c, 16), Ui.dpi(c, 18), Ui.dpi(c, 16));
        card.setBackground(rounded(Ui.WHITE, 0, 24));
        TextView q = new TextView(c);
        q.setText(r.answerLabel);
        q.setTypeface(Ui.mono(c));
        q.setTextSize(10.5f);
        q.setLetterSpacing(0.08f);
        q.setTextColor(0xFF666666);
        q.setSingleLine(true);
        q.setEllipsize(TextUtils.TruncateAt.START);
        TextView a = new TextView(c);
        a.setText(r.answer);
        a.setTypeface(Ui.dots(c));
        a.setTextSize(r.answer.length() > 10 ? 30 : 46);
        a.setTextColor(Ui.BLACK);
        a.setPadding(0, Ui.dpi(c, 4), 0, Ui.dpi(c, 8));
        TextView copy = new TextView(c);
        copy.setText("  Copy");
        copy.setTextColor(Ui.WHITE);
        copy.setTextSize(11.5f);
        copy.setCompoundDrawables(new Glyph(Glyph.COPY, Ui.dpi(c, 13), Ui.WHITE), null, null, null);
        copy.setPadding(Ui.dpi(c, 12), Ui.dpi(c, 6), Ui.dpi(c, 14), Ui.dpi(c, 6));
        copy.setBackground(rounded(Ui.BLACK, 0, 16));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = c.getSystemService(ClipboardManager.class);
            String plain = r.answer.replace(",", "");
            cm.setPrimaryClip(ClipData.newPlainText("Answer", plain));
            Ui.tap(v);
            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(c, "Copied", Toast.LENGTH_SHORT).show();
        });
        card.addView(q);
        card.addView(a);
        LinearLayout wrap = new LinearLayout(c);
        wrap.addView(copy);
        card.addView(wrap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dpi(c, 14), Ui.dpi(c, 10), Ui.dpi(c, 14), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private View actionRow(SearchEngine.Action a, int glyph, boolean card) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dpi(c, 16), Ui.dpi(c, 11), Ui.dpi(c, 14), Ui.dpi(c, 11));
        if (card) row.setBackground(rounded(Ui.SURFACE, 0xFF1F1F1F, 18));
        else row.setBackground(ripple());
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(new Glyph(glyph, Ui.dpi(c, 18), card ? Ui.WHITE : 0xFFAAAAAA));
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dpi(c, 18), Ui.dpi(c, 18)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dpi(c, 14), 0, 0, 0);
        TextView t = new TextView(c);
        t.setText(a.title);
        t.setTextColor(Ui.WHITE);
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(t);
        if (a.subtitle != null) {
            TextView s = new TextView(c);
            s.setText(a.subtitle);
            s.setTextColor(0xFF888888);
            s.setTextSize(11);
            text.addView(s);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        ImageView chev = new ImageView(c);
        chev.setImageDrawable(new Glyph(Glyph.CHEVRON, Ui.dpi(c, 14), 0xFF666666));
        row.addView(chev, new LinearLayout.LayoutParams(Ui.dpi(c, 14), Ui.dpi(c, 14)));
        row.setOnClickListener(v -> runAction(a));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        if (card) lp.setMargins(Ui.dpi(c, 14), Ui.dpi(c, 10), Ui.dpi(c, 14), 0);
        row.setLayoutParams(lp);
        return row;
    }

    private GradientDrawable rounded(int color, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        if (stroke != 0) g.setStroke(Ui.dpi(getContext(), 1), stroke);
        g.setCornerRadius(Ui.dp(getContext(), radiusDp));
        return g;
    }

    private android.graphics.drawable.Drawable ripple() {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, new android.graphics.drawable.ColorDrawable(Ui.WHITE));
    }
}
