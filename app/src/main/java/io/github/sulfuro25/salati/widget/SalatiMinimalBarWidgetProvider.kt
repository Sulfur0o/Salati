package io.github.sulfuro25.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.sulfuro25.salati.R

class SalatiMinimalBarWidgetProvider : AppWidgetProvider() {

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
            val cellIds = intArrayOf(
                R.id.widget_bar_cell_fajr,
                R.id.widget_bar_cell_dhuhr,
                R.id.widget_bar_cell_asr,
                R.id.widget_bar_cell_maghrib,
                R.id.widget_bar_cell_isha
            )
            val labelIds = intArrayOf(
                R.id.widget_bar_label_fajr,
                R.id.widget_bar_label_dhuhr,
                R.id.widget_bar_label_asr,
                R.id.widget_bar_label_maghrib,
                R.id.widget_bar_label_isha
            )
            val timeIds = intArrayOf(
                R.id.widget_bar_time_fajr,
                R.id.widget_bar_time_dhuhr,
                R.id.widget_bar_time_asr,
                R.id.widget_bar_time_maghrib,
                R.id.widget_bar_time_isha
            )

            val accentColor = context.getColor(R.color.salati_widget_accent)
            val onSurfaceVariantColor = context.getColor(R.color.salati_widget_on_surface_variant)
            val onSurfaceColor = context.getColor(R.color.salati_widget_on_surface)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.salati_widget_minimal_bar)
                views.setOnClickPendingIntent(R.id.widget_bar_root, pendingIntent)

                snapshot.times?.let { t ->
                    views.setTextViewText(R.id.widget_bar_time_fajr, t.fajr)
                    views.setTextViewText(R.id.widget_bar_time_dhuhr, t.dhuhr)
                    views.setTextViewText(R.id.widget_bar_time_asr, t.asr)
                    views.setTextViewText(R.id.widget_bar_time_maghrib, t.maghrib)
                    views.setTextViewText(R.id.widget_bar_time_isha, t.isha)
                }

                for (i in cellIds.indices) {
                    val isActive = (i == snapshot.activePrayerIndex)
                    if (isActive) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.salati_widget_active_pill)
                        views.setTextColor(labelIds[i], accentColor)
                        views.setTextColor(timeIds[i], accentColor)
                    } else {
                        views.setInt(cellIds[i], "setBackgroundResource", 0)
                        views.setTextColor(labelIds[i], onSurfaceVariantColor)
                        views.setTextColor(timeIds[i], onSurfaceColor)
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
