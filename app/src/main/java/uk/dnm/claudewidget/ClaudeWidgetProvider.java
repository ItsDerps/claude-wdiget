package uk.dnm.claudewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class ClaudeWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setOnClickPendingIntent(R.id.btn_chat, action(ctx, ActionActivity.CHAT, 1));
            v.setOnClickPendingIntent(R.id.btn_camera, action(ctx, ActionActivity.CAMERA, 2));
            v.setOnClickPendingIntent(R.id.btn_voice, action(ctx, ActionActivity.VOICE, 3));
            mgr.updateAppWidget(id, v);
        }
    }

    private static PendingIntent action(Context ctx, String which, int code) {
        Intent i = new Intent(ctx, ActionActivity.class)
                .setAction(which)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
