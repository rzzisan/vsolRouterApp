package com.zyrotech.vsolrouter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zyrotech.vsolrouter.R
import com.zyrotech.vsolrouter.data.Prefs
import com.zyrotech.vsolrouter.ui.MainActivity
import com.zyrotech.vsolrouter.work.Background
import java.text.DateFormat
import java.util.Date

/** Home-screen widget: router model, optical Rx power and number of connected devices. */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids)
        Background.schedule(ctx)
    }

    override fun onEnabled(ctx: Context) {
        Background.schedule(ctx)
        Background.refreshNow(ctx)
    }

    override fun onDisabled(ctx: Context) { Background.schedule(ctx) }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) Background.refreshNow(ctx)
    }

    companion object {
        const val ACTION_REFRESH = "com.zyrotech.vsolrouter.WIDGET_REFRESH"

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            render(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, StatusWidget::class.java)))
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val s = Prefs(ctx).snapshot
            val v = RemoteViews(ctx.packageName, R.layout.widget_status)
            v.setTextViewText(R.id.w_model, s?.model ?: "VSOL Router")
            v.setTextViewText(R.id.w_rx, s?.rx ?: "—")
            v.setTextViewText(R.id.w_devices, s?.devices?.toString() ?: "—")
            v.setTextViewText(
                R.id.w_time,
                if (s == null) "Open the app once to load" else "Updated " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.time)),
            )
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            val refresh = PendingIntent.getBroadcast(
                ctx, 2, Intent(ctx, StatusWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_refresh, refresh)
            mgr.updateAppWidget(ids, v)
        }
    }
}
