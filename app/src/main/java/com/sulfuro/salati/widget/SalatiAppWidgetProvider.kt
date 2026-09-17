package com.sulfuro.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.widget.RemoteViews
import com.sulfuro.salati.R
import com.sulfuro.salati.core.prayer.Prayer

class SalatiAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        // Only this provider's own widgets: the broadcast reaches all four providers, and
        // each redrawing everything meant the same work four times over.
        SalatiWidgetData.updateWidgets(context, pendingResult, appWidgetIds) { snapshot, intent ->
            applySnapshot(context, appWidgetManager, appWidgetIds, snapshot, intent)
        }
    }

    companion object {
        /** This layout's three views for each prayer, in the order of the day. */
        internal val cells = listOf(
            PrayerCell(Prayer.FAJR, R.id.widget_cell_fajr, R.id.widget_label_fajr, R.id.widget_time_fajr),
            PrayerCell(Prayer.DHUHR, R.id.widget_cell_dhuhr, R.id.widget_label_dhuhr, R.id.widget_time_dhuhr),
            PrayerCell(Prayer.ASR, R.id.widget_cell_asr, R.id.widget_label_asr, R.id.widget_time_asr),
            PrayerCell(Prayer.MAGHRIB, R.id.widget_cell_maghrib, R.id.widget_label_maghrib, R.id.widget_time_maghrib),
            PrayerCell(Prayer.ISHA, R.id.widget_cell_isha, R.id.widget_label_isha, R.id.widget_time_isha)
        )

        fun updateAllWidgets(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
            SalatiWidgetData.updateAllWidgets(context, pendingResult)
        }

        fun applySnapshot(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetDataSnapshot,
            pendingIntent: PendingIntent
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.salati_widget_layout)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                views.setTextViewText(R.id.widget_location, snapshot.city)
                views.setTextViewText(R.id.widget_hijri_date, snapshot.hijriDate)
                views.setTextViewText(R.id.widget_next_prayer_name, snapshot.nextPrayerName)
                views.setTextViewText(R.id.widget_next_prayer_time, snapshot.nextPrayerTime)

                views.applyPrayerCells(context, cells, snapshot)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
