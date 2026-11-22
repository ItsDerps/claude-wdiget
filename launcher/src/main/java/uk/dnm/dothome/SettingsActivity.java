package uk.dnm.dothome;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Set;
import java.util.function.Consumer;

/** One short settings page in the same black, white and red as the home screen. */
public class SettingsActivity extends Activity {

    private LinearLayout col;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = Prefs.get(this);
        getWindow().setStatusBarColor(Ui.BLACK);
        getWindow().setNavigationBarColor(Ui.BLACK);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BLACK);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dpi(this, 20), Ui.dpi(this, 28), Ui.dpi(this, 20), Ui.dpi(this, 40));
        scroll.addView(col);

        TextView title = new TextView(this);
        title.setText("SETTINGS");
        title.setTypeface(Ui.dots(this));
        title.setTextSize(32);
        title.setTextColor(Ui.WHITE);
        col.addView(title);

        // ---- default home
        boolean isHome = isDefaultHome();
        if (!isHome) {
            TextView card = text("Dot Home isn't your home screen yet. Tap here, choose Dot Home, and press the home button.", 14, Ui.WHITE);
            card.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 14), Ui.dpi(this, 16), Ui.dpi(this, 14));
            card.setBackground(round(Ui.RED, 18));
            card.setOnClickListener(v -> makeDefault());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dpi(this, 16);
            col.addView(card, lp);
        }

        group("HOME");
        toggle("Clock", "The dot-matrix clock. Tap it to open alarms.", prefs.clock(), prefs::setClock);
        toggle("Glance", "Your next event and battery under the clock.", prefs.glance(), prefs::setGlance);
        toggle("App labels", "Names under the icons.", prefs.labels(), prefs::setLabels);
        boolean cal = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        link("Next event", cal ? "Allowed: shows your next event in the coming day" : "Allow calendar access to show it", cal ? null : () ->
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 1));

        group("DRAWER");
        toggle("Keyboard opens with the drawer", "Start typing as soon as you swipe up.", prefs.keyboardOnOpen(), prefs::setKeyboardOnOpen);

        group("GESTURES");
        toggle("Swipe down for notifications", "From anywhere on home, not just the top.", prefs.swipeDownNotifications(), prefs::setSwipeDownNotifications);
        toggle("Double-tap to lock", "On empty space on home.", prefs.doubleTapLock(), prefs::setDoubleTapLock);
        if (prefs.doubleTapLock()) {
            boolean on = LockService.isOn();
            link("Lock permission", on ? "On" : "Needs Dot Home turned on under Accessibility (it reads nothing on screen)",
                    on ? null : () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
        text2("Touch & hold empty space on home to come back here.");

        group("FEEL");
        segment("Animations", new String[]{"Full", "Calm", "Off"}, prefs.motion(), prefs::setMotion);
        segment("Haptics", new String[]{"Off", "Soft", "Crisp"}, prefs.haptics(), prefs::setHaptics);

        group("EXTRAS");
        boolean dots = dotsEnabled();
        link("Notification dots", dots ? "On: a red dot on apps with notifications" : "Off: tap to allow (only app names are used)",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        group("HIDDEN APPS");
        java.util.List<Apps.App> hidden = Apps.get(this).hiddenApps();
        if (hidden.isEmpty()) text2("None. Touch & hold any app and choose Hide.");
        for (Apps.App a : hidden) {
            link(a.label, "Tap to show it again", () -> {
                Set<String> h = prefs.hidden();
                h.remove(a.key);
                prefs.setHidden(h);
                build();
            });
        }

        group("PRIVACY");
        toggle("Learn suggestions", "Remember which apps you open when, on this phone only, for the Suggested row.", prefs.memory(), v -> {
            prefs.setMemory(v);
            if (!v) prefs.clearUsage();
        });
        link("Clear app history", "Forget which apps you've opened", () -> {
            prefs.clearUsage();
            android.widget.Toast.makeText(this, "Cleared", android.widget.Toast.LENGTH_SHORT).show();
        });
        link("Reset home layout", "Go back to the default apps on home and in the dock", () -> {
            prefs.raw().edit().remove("pinned").remove("dock").apply();
            Apps.get(this).load();
            android.widget.Toast.makeText(this, "Home layout reset", android.widget.Toast.LENGTH_SHORT).show();
        });

        TextView foot = text("Dot Home 0.1 · Fonts: Doto and Space Mono (SIL Open Font Licence). Nothing is sent off this phone.", 11, 0xFF666666);
        foot.setPadding(0, Ui.dpi(this, 28), 0, 0);
        col.addView(foot);

        setContentView(scroll);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        build();
    }

    private boolean isDefaultHome() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void makeDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 2);
                return;
            }
        }
        startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private boolean dotsEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    // ------------------------------------------------------------------ rows

    private void group(String name) {
        TextView t = text(name, 10, Ui.RED);
        t.setTypeface(Ui.mono(this));
        t.setLetterSpacing(0.18f);
        t.setPadding(0, Ui.dpi(this, 26), 0, Ui.dpi(this, 6));
        col.addView(t);
    }

    private void text2(String s) {
        TextView t = text(s, 12, 0xFF777777);
        t.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 4));
        col.addView(t);
    }

    private void toggle(String title, String sub, boolean on, Consumer<Boolean> set) {
        LinearLayout row = row();
        LinearLayout text = labels(title, sub);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, v) -> {
            set.accept(v);
            Ui.tick(b);
            col.post(this::build);
        });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        col.addView(row);
    }

    private void link(String title, String sub, Runnable run) {
        LinearLayout row = row();
        row.addView(labels(title, sub), new LinearLayout.LayoutParams(0, -2, 1f));
        if (run != null) {
            android.widget.ImageView chev = new android.widget.ImageView(this);
            chev.setImageDrawable(new Glyph(Glyph.CHEVRON, Ui.dpi(this, 14), 0xFF666666));
            row.addView(chev, new LinearLayout.LayoutParams(Ui.dpi(this, 14), Ui.dpi(this, 14)));
            row.setOnClickListener(v -> run.run());
        }
        col.addView(row);
    }

    private void segment(String title, String[] options, int current, Consumer<Integer> set) {
        LinearLayout row = row();
        TextView t = text(title, 15, Ui.WHITE);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(round(0xFF161616, 14));
        seg.setPadding(Ui.dpi(this, 2), Ui.dpi(this, 2), Ui.dpi(this, 2), Ui.dpi(this, 2));
        for (int i = 0; i < options.length; i++) {
            TextView o = text(options[i], 12, i == current ? Ui.BLACK : 0xFF999999);
            o.setPadding(Ui.dpi(this, 11), Ui.dpi(this, 5), Ui.dpi(this, 11), Ui.dpi(this, 5));
            if (i == current) {
                o.setBackground(round(Ui.WHITE, 12));
                o.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            final int v = i;
            o.setOnClickListener(x -> {
                set.accept(v);
                Ui.tick(x);
                build();
            });
            seg.addView(o);
        }
        row.addView(seg);
        col.addView(row);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dpi(this, 12), 0, Ui.dpi(this, 12));
        return row;
    }

    private LinearLayout labels(String title, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, Ui.dpi(this, 12), 0);
        l.addView(text(title, 15, Ui.WHITE));
        if (sub != null) {
            TextView s = text(sub, 12, 0xFF888888);
            s.setPadding(0, Ui.dpi(this, 2), 0, 0);
            l.addView(s);
        }
        return l;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dp(this, r));
        return g;
    }
}
