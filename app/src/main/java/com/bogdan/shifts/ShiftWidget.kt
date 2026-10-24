package com.bogdan.shifts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.time.LocalDateTime

class ShiftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShiftWidget::class.java))
            ids.forEach { update(ctx, mgr, it) }
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val st = if (Prefs.isConfigured(ctx)) {
                ShiftLogic.status(Prefs.load(ctx), LocalDateTime.now())
            } else {
                Status("Настройте график", "Откройте приложение", false)
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            views.setTextViewText(R.id.w_title, st.title)
            views.setTextViewText(R.id.w_sub, st.subtitle)
            views.setTextColor(
                R.id.w_title,
                if (st.onShift) Color.parseColor("#6FD3A2") else Color.WHITE
            )
            val pi = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
