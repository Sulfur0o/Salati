package com.sulfuro.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.widget.RemoteViews
import com.sulfuro.salati.R

class SalatiAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        SalatiWidgetData.updateAllWidgets(context, pendingResult)
    }

    companion object {
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
            val cellIds = intArrayOf(
                R.id.widget_cell_fajr,
                R.id.widget_cell_dhuhr,
                R.id.widget_cell_asr,
                R.id.widget_cell_maghrib,
                R.id.widget_cell_isha
            )
            val labelIds = intArrayOf(
                R.id.widget_label_fajr,
                R.id.widget_label_dhuhr,
                R.id.widget_label_asr,
                R.id.widget_label_maghrib,
                R.id.widget_label_isha
            )
            val timeIds = intArrayOf(
                R.id.widget_time_fajr,
                R.id.widget_time_dhuhr,
                R.id.widget_time_asr,
                R.id.widget_time_maghrib,
                R.id.widget_time_isha
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.salati_widget_layout)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                views.setTextViewText(R.id.widget_location, snapshot.city)
                views.setTextViewText(R.id.widget_hijri_date, snapshot.hijriDate)
                views.setTextViewText(R.id.widget_next_prayer_name, snapshot.nextPrayerName)
                views.setTextViewText(R.id.widget_next_prayer_time, snapshot.nextPrayerTime)

                snapshot.times?.let { t ->
                    views.setTextViewText(R.id.widget_time_fajr, t.fajr)
                    views.setTextViewText(R.id.widget_time_dhuhr, t.dhuhr)
                    views.setTextViewText(R.id.widget_time_asr, t.asr)
                    views.setTextViewText(R.id.widget_time_maghrib, t.maghrib)
                    views.setTextViewText(R.id.widget_time_isha, t.isha)
                }

                for (i in cellIds.indices) {
                    val isActive = (i == snapshot.activePrayerIndex)
                    if (isActive) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.salati_widget_active_pill)
                        SalatiWidgetData.setViewTextColor(views, context, labelIds[i], R.color.salati_widget_accent)
                        SalatiWidgetData.setViewTextColor(views, context, timeIds[i], R.color.salati_widget_accent)
                    } else {
                        views.setInt(cellIds[i], "setBackgroundResource", 0)
                        SalatiWidgetData.setViewTextColor(views, context, labelIds[i], R.color.salati_widget_on_surface_variant)
                        SalatiWidgetData.setViewTextColor(views, context, timeIds[i], R.color.salati_widget_on_surface)
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
