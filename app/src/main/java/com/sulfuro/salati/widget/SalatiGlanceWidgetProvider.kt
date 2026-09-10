package com.sulfuro.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.sulfuro.salati.R

class SalatiGlanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        SalatiWidgetData.updateAllWidgets(context, pendingResult)
    }

    companion object {
        fun applySnapshot(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetDataSnapshot,
            pendingIntent: PendingIntent
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.salati_widget_glance_pill)
                views.setOnClickPendingIntent(R.id.widget_glance_root, pendingIntent)

                views.setTextViewText(R.id.widget_glance_location, snapshot.city)
                views.setTextViewText(R.id.widget_glance_prayer_name, snapshot.nextPrayerName)
                views.setTextViewText(R.id.widget_glance_prayer_time, snapshot.nextPrayerTime)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
