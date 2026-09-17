package com.sulfuro.salati.widget

import android.content.Context
import android.widget.RemoteViews
import com.sulfuro.salati.R
import com.sulfuro.salati.core.prayer.Prayer

/**
 * One prayer's three views in a widget layout.
 *
 * The ids cannot come from [Prayer] - they belong to a particular layout - but binding all
 * three to the prayer in one place is what stops them drifting apart. They used to be three
 * parallel `intArrayOf(...)` blocks matched only by position, in two providers, so getting
 * one row out of order would have painted the wrong prayer's pill and still compiled.
 */
internal data class PrayerCell(
    val prayer: Prayer,
    val cellId: Int,
    val labelId: Int,
    val timeId: Int
)

/**
 * Writes each prayer's time into the layout and lights the one that is next.
 *
 * Both grid widgets do exactly this and differ only in their ids, so they share it.
 */
internal fun RemoteViews.applyPrayerCells(
    context: Context,
    cells: List<PrayerCell>,
    snapshot: WidgetDataSnapshot
) {
    for (cell in cells) {
        snapshot.times?.get(cell.prayer)?.let { setTextViewText(cell.timeId, it) }

        val isActive = cell.prayer == snapshot.activePrayer
        setInt(
            cell.cellId,
            "setBackgroundResource",
            if (isActive) R.drawable.salati_widget_active_pill else 0
        )
        SalatiWidgetData.setViewTextColor(
            this,
            context,
            cell.labelId,
            if (isActive) R.color.salati_widget_on_pill else R.color.salati_widget_on_surface_variant
        )
        SalatiWidgetData.setViewTextColor(
            this,
            context,
            cell.timeId,
            if (isActive) R.color.salati_widget_on_pill else R.color.salati_widget_on_surface
        )
    }
}
