package uk.dnm.dothome;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

/**
 * Optional: lets Dot Home put one small red dot on apps that have notifications waiting.
 * Only package names are used; notification content is never read or stored.
 */
public class DotsListener extends NotificationListenerService {

    interface Listener { void onChanged(Set<String> packages); }

    static volatile Listener listener;
    private static final Set<String> active = new HashSet<>();

    static synchronized Set<String> current() { return new HashSet<>(active); }

    @Override public void onListenerConnected() { refresh(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    @Override
    public void onListenerDisconnected() {
        synchronized (DotsListener.class) { active.clear(); }
        Listener l = listener;
        if (l != null) l.onChanged(new HashSet<>());
    }

    private void refresh() {
        Set<String> pkgs = new HashSet<>();
        try {
            StatusBarNotification[] list = getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification n : list) {
                    if (n.isOngoing()) continue;
                    if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0 && list.length > 1) {
                        pkgs.add(n.getPackageName());
                        continue;
                    }
                    pkgs.add(n.getPackageName());
                }
            }
        } catch (Exception ignored) { }
        synchronized (DotsListener.class) {
            active.clear();
            active.addAll(pkgs);
        }
        Listener l = listener;
        if (l != null) l.onChanged(pkgs);
    }
}
