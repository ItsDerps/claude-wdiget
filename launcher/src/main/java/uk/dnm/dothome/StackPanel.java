package uk.dnm.dothome;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** An open stack: the stack icon grows into a card holding its apps. Swipe down or tap outside to close. */
final class StackPanel {

    interface Host extends AppIconView.Listener {
        void renameStack(String slot, String name);
        void setBackdrop(boolean blurred);
        void onStackClosed();
    }

    final String slot;
    private final FrameLayout root, overlay;
    private final LinearLayout card;
    private final View dim;
    private final Host host;
    private final Spring spring;
    private float fromScale, fromDx, fromDy;
    private boolean closing;
    private float downY;

    private StackPanel(FrameLayout root, String slot, Host host) {
        this.root = root;
        this.slot = slot;
        this.host = host;
        Context c = root.getContext();
        overlay = new FrameLayout(c);
        dim = new View(c);
        dim.setBackgroundColor(0xB3000000);
        dim.setOnClickListener(v -> close());
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dpi(c, 12), Ui.dpi(c, 18), Ui.dpi(c, 12), Ui.dpi(c, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141414);
        bg.setStroke(Ui.dpi(c, 1), Ui.LINE2);
        bg.setCornerRadius(Ui.dp(c, 30));
        card.setBackground(bg);
        card.setClickable(true);
        spring = new Spring(450, 0.8f, 0f, this::apply);
        spring.damping = 0.8f;
    }

    static StackPanel show(FrameLayout root, AppIconView src, String name, List<Apps.App> apps, boolean labels, Host host) {
        Context c = root.getContext();
        StackPanel p = new StackPanel(root, src.slot, host);

        TextView title = new TextView(c);
        title.setText(name);
        title.setTypeface(Ui.dots(c));
        title.setTextSize(24);
        title.setTextColor(Ui.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Ui.dpi(c, 12));
        title.setOnClickListener(v -> p.rename(name));
        p.card.addView(title);

        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(c);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = i == 0 ? 0 : Ui.dpi(c, 8);
                p.card.addView(row, rl);
            }
            AppIconView v = new AppIconView(c, 48, labels);
            v.bind(apps.get(i), host);
            v.slot = apps.get(i).key;
            row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (row != null) for (int i = row.getChildCount(); i < 3; i++) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView hint = new TextView(c);
        hint.setText("Tap the name to rename · hold an app for more");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(10.5f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dpi(c, 14), 0, 0);
        p.card.addView(hint);

        int w = root.getWidth() - Ui.dpi(c, 44);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, -2, Gravity.CENTER);
        p.overlay.addView(p.card, cl);
        root.addView(p.overlay, new FrameLayout.LayoutParams(-1, -1));
        host.setBackdrop(true);

        // Drag the card down to close
        p.card.setOnTouchListener((v, e) -> p.drag(e));

        // Start the card exactly over the stack icon, then grow it
        p.card.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int ch = p.card.getMeasuredHeight();
        int[] rp = new int[2], sp = new int[2];
        root.getLocationOnScreen(rp);
        src.getLocationOnScreen(sp);
        RectF d = src.discBounds();
        float iconCx = sp[0] - rp[0] + d.centerX(), iconCy = sp[1] - rp[1] + d.centerY();
        p.fromScale = d.width() / w;
        p.fromDx = iconCx - root.getWidth() / 2f;
        p.fromDy = iconCy - root.getHeight() / 2f;
        if (Ui.motion(c) == Ui.MOTION_FULL) {
            p.apply(0);
            p.spring.animateTo(1f, 0);
        } else {
            p.spring.set(1f);
        }
        p.card.setPivotX(w / 2f);
        p.card.setPivotY(ch / 2f);
        return p;
    }

    private void apply(float v) {
        float s = Ui.lerp(fromScale, 1f, v);
        card.setScaleX(s);
        card.setScaleY(Ui.lerp(fromScale * 1.0f, 1f, v));
        card.setTranslationX(Ui.lerp(fromDx, 0, v));
        card.setTranslationY(Ui.lerp(fromDy, 0, v) + dragY);
        float contentAlpha = Ui.clamp((v - 0.45f) / 0.45f, 0, 1);
        for (int i = 0; i < card.getChildCount(); i++) card.getChildAt(i).setAlpha(contentAlpha);
        dim.setAlpha(Ui.clamp(v, 0, 1));
    }

    private float dragY;

    private boolean drag(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                dragY = Math.max(0, e.getRawY() - downY);
                apply(spring.value);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragY > Ui.dp(root.getContext(), 100)) {
                    close();
                } else {
                    dragY = 0;
                    apply(spring.value);
                }
                return true;
            default:
                return true;
        }
    }

    boolean isOpen() { return overlay.getParent() != null && !closing; }

    void close() {
        if (closing) return;
        closing = true;
        host.setBackdrop(false);
        Runnable done = () -> {
            root.removeView(overlay);
            host.onStackClosed();
        };
        if (Ui.motion(root.getContext()) == Ui.MOTION_FULL) {
            dragY = 0;
            spring.stiffness = 600;
            spring.damping = 1f;
            spring.animateTo(0f, 0);
            root.postDelayed(done, 260);
        } else {
            done.run();
        }
    }

    private void rename(String current) {
        Context c = root.getContext();
        EditText input = new EditText(c);
        input.setText(current);
        input.setSelection(current.length());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(Ui.dpi(c, 20), Ui.dpi(c, 8), Ui.dpi(c, 20), 0);
        wrap.addView(input);
        new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Name this stack")
                .setView(wrap)
                .setPositiveButton("Save", (dlg, which) -> {
                    host.renameStack(slot, input.getText().toString());
                    close();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
