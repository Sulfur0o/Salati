package com.sulfuro.salati.core.location

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps coordinates to an IANA timezone without the network.
 *
 * Online, [PrayerLocationResolver] still prefers the timezone Aladhan reports for the
 * new coordinates. This exists so an offline (or failed) lookup never keeps the previous
 * city's zone paired with the new coordinates.
 *
 * Bounding boxes overlap on purpose. The smallest box that contains the point wins, so a
 * city-sized rectangle beats the country it sits in. Points that miss every box fall back
 * to a fixed offset derived from longitude. If the device zone currently has that same
 * offset, the device zone is preferred: it is a named zone with DST rules, and the phone
 * is often already set to the place the user just stood in.
 */
object CoordinateTimezoneLookup {

    fun zoneIdFor(
        latitude: Double,
        longitude: Double,
        deviceZoneId: ZoneId = ZoneId.systemDefault(),
        at: Instant = Instant.now()
    ): ZoneId {
        val lat = latitude.coerceIn(-90.0, 90.0)
        val lng = normalizeLongitude(longitude)
        val estimated = lookupBox(lat, lng) ?: offsetZone(lng)
        return if (sameOffset(estimated, deviceZoneId, at)) deviceZoneId else estimated
    }

    internal fun lookupBox(latitude: Double, longitude: Double): ZoneId? {
        var best: TzBox? = null
        var bestArea = Double.POSITIVE_INFINITY
        for (box in BOXES) {
            if (!box.contains(latitude, longitude)) continue
            val area = box.area
            if (area < bestArea) {
                best = box
                bestArea = area
            }
        }
        return best?.let { ZoneId.of(it.id) }
    }

    internal fun offsetZone(longitude: Double): ZoneId {
        val hours = (longitude / 15.0).roundToInt().coerceIn(-12, 14)
        return ZoneId.ofOffset("UTC", ZoneOffset.ofHours(hours))
    }

    private fun sameOffset(a: ZoneId, b: ZoneId, at: Instant): Boolean {
        return a.rules.getOffset(at) == b.rules.getOffset(at)
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var lng = longitude % 360.0
        if (lng > 180.0) lng -= 360.0
        if (lng <= -180.0) lng += 360.0
        return lng
    }

    private data class TzBox(
        val id: String,
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    ) {
        fun contains(lat: Double, lng: Double): Boolean {
            return lat in minLat..maxLat && lng in minLng..maxLng
        }

        val area: Double get() = (maxLat - minLat) * (maxLng - minLng)
    }

    /**
     * Representative rectangles, not political borders. Smaller boxes are preferred when
     * they overlap, so keep city and small-country entries in the list.
     */
    private val BOXES = arrayOf(
        // Maghrib and West Africa
        TzBox("Africa/Casablanca", 27.6, 35.9, -13.3, -1.0),
        TzBox("Atlantic/Canary", 27.4, 29.5, -18.3, -13.3),
        TzBox("Africa/Algiers", 18.9, 37.1, -8.7, 12.0),
        TzBox("Africa/Tunis", 30.2, 37.6, 7.5, 11.6),
        TzBox("Africa/Tripoli", 19.5, 33.2, 9.3, 25.2),
        TzBox("Africa/Nouakchott", 14.7, 27.3, -17.1, -4.8),
        TzBox("Africa/Dakar", 12.3, 16.7, -17.6, -11.3),
        TzBox("Africa/Bamako", 10.1, 25.0, -12.3, 4.3),
        TzBox("Africa/Abidjan", 4.3, 10.7, -8.6, -2.5),
        TzBox("Africa/Accra", 4.5, 11.2, -3.3, 1.3),
        TzBox("Africa/Lagos", 4.2, 13.9, 2.6, 14.7),
        TzBox("Africa/Ndjamena", 7.4, 23.5, 13.4, 24.0),
        TzBox("Africa/Khartoum", 8.6, 22.3, 21.8, 38.6),
        TzBox("Africa/Cairo", 22.0, 31.7, 24.6, 37.0),
        TzBox("Africa/Johannesburg", -35.0, -22.1, 16.4, 33.0),
        TzBox("Africa/Nairobi", -4.7, 5.5, 33.9, 42.0),
        TzBox("Africa/Addis_Ababa", 3.4, 14.9, 32.9, 48.0),
        TzBox("Africa/Mogadishu", -1.7, 12.0, 40.9, 51.5),
        TzBox("Indian/Antananarivo", -25.6, -11.9, 43.2, 50.5),

        // Levant, Arabia, Iran
        TzBox("Asia/Gaza", 31.2, 31.6, 34.2, 34.6),
        TzBox("Asia/Hebron", 31.3, 32.6, 34.8, 35.6),
        TzBox("Asia/Jerusalem", 31.4, 33.3, 34.2, 35.9),
        TzBox("Asia/Beirut", 33.0, 34.7, 35.1, 36.7),
        TzBox("Asia/Damascus", 32.3, 37.4, 35.6, 42.4),
        TzBox("Asia/Amman", 29.1, 33.4, 34.9, 39.3),
        TzBox("Asia/Baghdad", 29.0, 37.4, 38.7, 48.6),
        TzBox("Asia/Riyadh", 16.0, 32.3, 34.5, 55.7),
        TzBox("Asia/Kuwait", 28.5, 30.1, 46.5, 48.5),
        TzBox("Asia/Bahrain", 25.7, 26.4, 50.3, 50.8),
        TzBox("Asia/Qatar", 24.4, 26.2, 50.7, 51.7),
        TzBox("Asia/Dubai", 22.6, 26.1, 51.5, 56.4),
        TzBox("Asia/Muscat", 16.6, 26.4, 52.0, 59.9),
        TzBox("Asia/Aden", 12.1, 19.0, 42.5, 54.7),
        TzBox("Asia/Tehran", 25.0, 39.8, 44.0, 63.4),
        TzBox("Asia/Kabul", 29.3, 38.5, 60.4, 74.9),

        // Turkey, Caucasus, Central / South Asia
        TzBox("Europe/Istanbul", 35.8, 42.3, 25.6, 45.0),
        TzBox("Asia/Tbilisi", 41.0, 43.6, 39.9, 46.8),
        TzBox("Asia/Yerevan", 38.8, 41.4, 43.4, 46.7),
        TzBox("Asia/Baku", 38.4, 41.9, 44.7, 50.5),
        TzBox("Asia/Karachi", 23.6, 37.1, 60.8, 77.9),
        TzBox("Asia/Kolkata", 6.5, 35.7, 68.0, 97.4),
        TzBox("Asia/Colombo", 5.9, 9.9, 79.5, 82.1),
        TzBox("Asia/Dhaka", 20.5, 26.7, 88.0, 92.8),
        TzBox("Asia/Kathmandu", 26.3, 30.5, 80.0, 88.3),
        TzBox("Indian/Maldives", -0.7, 7.1, 72.6, 73.8),
        TzBox("Asia/Tashkent", 37.1, 45.6, 55.9, 73.2),
        TzBox("Asia/Almaty", 40.5, 55.5, 46.5, 87.4),

        // Southeast Asia
        TzBox("Asia/Yangon", 9.5, 28.6, 92.1, 101.2),
        TzBox("Asia/Bangkok", 5.6, 20.5, 97.3, 105.7),
        TzBox("Asia/Ho_Chi_Minh", 8.4, 23.4, 102.1, 109.5),
        TzBox("Asia/Jakarta", -11.0, 6.0, 95.0, 119.0),
        TzBox("Asia/Makassar", -8.8, 4.8, 118.7, 125.5),
        TzBox("Asia/Jayapura", -9.2, 0.8, 129.0, 141.0),
        TzBox("Asia/Kuala_Lumpur", 0.8, 7.4, 99.6, 119.3),
        TzBox("Asia/Singapore", 1.15, 1.48, 103.6, 104.1),
        TzBox("Asia/Brunei", 4.0, 5.1, 114.0, 115.4),
        TzBox("Asia/Manila", 4.6, 21.2, 116.9, 126.7),
        TzBox("Australia/Perth", -35.2, -13.6, 112.9, 129.0),
        TzBox("Australia/Adelaide", -38.1, -26.0, 129.0, 141.0),
        TzBox("Australia/Sydney", -37.6, -28.1, 141.0, 153.7),
        TzBox("Australia/Darwin", -16.0, -10.9, 129.0, 138.1),
        TzBox("Pacific/Auckland", -47.4, -34.0, 166.3, 178.8),

        // East Asia
        TzBox("Asia/Shanghai", 18.1, 53.6, 73.4, 135.1),
        TzBox("Asia/Hong_Kong", 22.1, 22.6, 113.8, 114.5),
        TzBox("Asia/Taipei", 21.8, 25.4, 120.0, 122.1),
        TzBox("Asia/Seoul", 33.1, 38.7, 124.5, 132.0),
        TzBox("Asia/Tokyo", 24.0, 45.6, 122.9, 146.0),

        // Europe
        TzBox("Europe/Lisbon", 36.9, 42.2, -9.6, -6.1),
        TzBox("Atlantic/Azores", 36.9, 39.8, -31.3, -24.9),
        TzBox("Europe/Dublin", 51.4, 55.4, -10.5, -5.9),
        TzBox("Europe/London", 49.8, 58.7, -8.3, 1.8),
        TzBox("Europe/Madrid", 35.9, 43.8, -9.4, 3.4),
        TzBox("Europe/Paris", 42.3, 51.2, -5.2, 8.3),
        TzBox("Europe/Brussels", 49.45, 51.55, 2.5, 6.45),
        TzBox("Europe/Amsterdam", 50.7, 53.6, 3.3, 7.3),
        TzBox("Europe/Luxembourg", 49.44, 50.19, 5.73, 6.54),
        TzBox("Europe/Zurich", 45.8, 47.9, 5.9, 10.5),
        TzBox("Europe/Berlin", 47.2, 55.1, 5.8, 15.1),
        TzBox("Europe/Rome", 36.6, 47.1, 6.6, 18.6),
        TzBox("Europe/Vienna", 46.3, 49.1, 9.5, 17.2),
        TzBox("Europe/Prague", 48.5, 51.1, 12.0, 18.9),
        TzBox("Europe/Warsaw", 49.0, 54.9, 14.1, 24.2),
        TzBox("Europe/Budapest", 45.7, 48.6, 16.1, 22.9),
        TzBox("Europe/Bucharest", 43.6, 48.3, 20.2, 29.8),
        TzBox("Europe/Athens", 34.8, 41.8, 19.3, 29.7),
        TzBox("Europe/Sofia", 41.2, 44.3, 22.3, 28.7),
        TzBox("Europe/Helsinki", 59.4, 70.1, 20.5, 31.6),
        TzBox("Europe/Stockholm", 55.2, 69.1, 10.9, 24.2),
        TzBox("Europe/Oslo", 57.9, 71.2, 4.6, 31.1),
        TzBox("Europe/Copenhagen", 54.5, 57.8, 8.0, 15.2),
        TzBox("Europe/Kyiv", 44.3, 52.4, 22.1, 40.3),
        TzBox("Europe/Moscow", 44.0, 70.0, 27.0, 60.0),
        TzBox("Europe/Istanbul", 40.7, 41.6, 28.4, 29.5),

        // Americas
        TzBox("America/Sao_Paulo", -33.8, -2.6, -53.2, -34.7),
        TzBox("America/Argentina/Buenos_Aires", -55.1, -21.7, -73.6, -53.5),
        TzBox("America/Santiago", -56.0, -17.5, -75.7, -66.3),
        TzBox("America/Bogota", -4.3, 13.5, -79.1, -66.8),
        TzBox("America/Lima", -18.4, -0.03, -81.4, -68.6),
        TzBox("America/Mexico_City", 14.5, 32.8, -118.5, -86.7),
        TzBox("America/New_York", 24.0, 47.5, -85.0, -66.8),
        TzBox("America/Chicago", 25.8, 49.4, -104.1, -84.7),
        TzBox("America/Denver", 31.3, 49.1, -116.1, -102.0),
        TzBox("America/Los_Angeles", 32.5, 49.1, -124.6, -114.0),
        TzBox("America/Anchorage", 51.2, 71.5, -172.5, -129.8),
        TzBox("Pacific/Honolulu", 18.9, 22.3, -160.3, -154.7),
        TzBox("America/Toronto", 41.6, 56.9, -95.2, -74.3),
        TzBox("America/Vancouver", 48.0, 60.0, -139.1, -114.0),
        TzBox("America/Halifax", 43.3, 47.1, -66.5, -59.6)
    )
}
