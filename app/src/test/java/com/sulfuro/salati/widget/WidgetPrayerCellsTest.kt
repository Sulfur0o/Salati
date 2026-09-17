package com.sulfuro.salati.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sulfuro.salati.core.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The tables binding each prayer to its three views in a widget layout.
 *
 * These were three parallel arrays of ids per provider, matched to a prayer only by
 * position, and the snapshot pointed at the active one with an Int index. Getting a row out
 * of order would have painted one prayer's time under another's name, lit the wrong pill,
 * and compiled without complaint - and widgets are the one surface with no instrumented
 * coverage, so nothing would have caught it before a phone did.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class WidgetPrayerCellsTest {

    private val tables = mapOf(
        "grid" to SalatiAppWidgetProvider.cells,
        "bar" to SalatiMinimalBarWidgetProvider.cells
    )

    @Test
    fun everyLayoutCoversThePrayedFiveInOrder() {
        for ((name, cells) in tables) {
            assertEquals("$name widget", Prayer.prayed, cells.map { it.prayer })
        }
    }

    /** Three distinct views per row, and no id serving two prayers. */
    @Test
    fun noViewIdIsUsedTwiceWithinALayout() {
        for ((name, cells) in tables) {
            val ids = cells.flatMap { listOf(it.cellId, it.labelId, it.timeId) }
            assertEquals("$name widget reuses a view id", ids.size, ids.toSet().size)
        }
    }

    /** The two layouts are separate XML, so they must not share ids either. */
    @Test
    fun theTwoLayoutsDoNotShareViewIds() {
        val grid = SalatiAppWidgetProvider.cells.flatMap { listOf(it.cellId, it.labelId, it.timeId) }
        val bar = SalatiMinimalBarWidgetProvider.cells.flatMap { listOf(it.cellId, it.labelId, it.timeId) }
        assertEquals(emptySet<Int>(), grid.toSet() intersect bar.toSet())
    }

    /** The snapshot names the active prayer, so a widget looks it up rather than counting. */
    @Test
    fun theSnapshotPointsAtAPrayerAndItsTimeCanBeFound() {
        val times = WidgetPrayerTimes(
            mapOf(
                Prayer.FAJR to "05:13",
                Prayer.DHUHR to "13:40",
                Prayer.ASR to "17:22",
                Prayer.MAGHRIB to "20:08",
                Prayer.ISHA to "21:58"
            )
        )
        val snapshot = WidgetDataSnapshot(
            city = "Grimbergen",
            hijriDate = "1 Rajab 1448",
            times = times,
            nextPrayerName = "Asr",
            nextPrayerTime = "17:22",
            activePrayer = Prayer.ASR
        )

        val active = SalatiAppWidgetProvider.cells.single { it.prayer == snapshot.activePrayer }
        assertEquals("17:22", snapshot.times?.get(active.prayer))
        // Sunrise has no cell and no time, and asking for it is not an error.
        assertNull(times[Prayer.SUNRISE])
    }
}
