package uk.dnm.dothome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Only exists so double-tap can lock the screen (Android allows this only through an
 * accessibility service). It reads nothing on screen and does nothing else.
 */
public class LockService extends AccessibilityService {

    private static LockService instance;

    static boolean isOn() { return instance != null; }

    /** Locks the screen if the service is on. Returns false if it isn't. */
    static boolean lock() {
        if (instance == null) return false;
        return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() { instance = this; }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
