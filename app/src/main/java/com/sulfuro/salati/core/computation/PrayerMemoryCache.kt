package com.sulfuro.salati.core.computation

/**
 * Holds recently used months in memory, in front of the on-disk cache.
 *
 * Navigation tears a screen down whenever the user changes tab, so returning to Daily or
 * Monthly re-runs its loader from scratch: a disk read plus deserialising a month of JSON,
 * for data that has not changed. Keeping the last few months in memory makes switching
 * tabs and paging through the calendar immediate.
 *
 * Only months that came from Aladhan are stored. A month computed on the device is a
 * stand-in for data the app has not managed to fetch yet, and caching it would let it
 * outlive the outage it was made for.
 */
interface PrayerMemoryCache {
    fun get(request: PrayerMonthRequest): List<AladhanDayData>?
    fun put(request: PrayerMonthRequest, data: List<AladhanDayData>)
    fun invalidate(request: PrayerMonthRequest)
    fun clear()
}

/** Used by tests that need to exercise the disk-and-network path on its own. */
object NoPrayerMemoryCache : PrayerMemoryCache {
    override fun get(request: PrayerMonthRequest): List<AladhanDayData>? = null
    override fun put(request: PrayerMonthRequest, data: List<AladhanDayData>) = Unit
    override fun invalidate(request: PrayerMonthRequest) = Unit
    override fun clear() = Unit
}

/**
 * @param maxEntries how many months to keep. Four covers what the app actually reaches
 *   for - the current month, the next one for the month-end Fajr rollover, and a month
 *   either side while paging the calendar - without holding a meaningful amount of
 *   memory, since each entry is roughly thirty small objects.
 */
class LruPrayerMemoryCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : PrayerMemoryCache {

    private val entries = object : LinkedHashMap<PrayerMonthRequest, List<AladhanDayData>>(
        maxEntries,
        LOAD_FACTOR,
        /* accessOrder = */ true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<PrayerMonthRequest, List<AladhanDayData>>
        ): Boolean = size > maxEntries
    }

    override fun get(request: PrayerMonthRequest): List<AladhanDayData>? =
        synchronized(entries) { entries[request] }

    override fun put(request: PrayerMonthRequest, data: List<AladhanDayData>) {
        if (data.isEmpty()) return
        synchronized(entries) { entries[request] = data }
    }

    override fun invalidate(request: PrayerMonthRequest) {
        synchronized(entries) { entries.remove(request) }
    }

    override fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /** Visible for tests, which assert eviction rather than guessing at it. */
    internal fun size(): Int = synchronized(entries) { entries.size }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 4
        const val LOAD_FACTOR = 0.75f
    }
}
