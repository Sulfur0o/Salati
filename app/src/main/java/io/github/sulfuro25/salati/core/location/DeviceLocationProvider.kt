package io.github.sulfuro25.salati.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedDeviceLocation(
    val latitude: Double,
    val longitude: Double,
    /** Reverse-geocoded label, or null when no geocoder result is available. */
    val cityName: String?
)

sealed interface DeviceLocationResult {
    data class Success(val location: ResolvedDeviceLocation) : DeviceLocationResult
    data object PermissionDenied : DeviceLocationResult
    data object LocationDisabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}

/**
 * Resolves the device's current position using only platform APIs, so the app keeps its
 * zero-dependency, no-Play-Services footprint. Location is never read in the background:
 * this is only invoked when the user explicitly taps "use my current location".
 */
object DeviceLocationProvider {
    private const val TAG = "DeviceLocationProvider"
    private const val FIX_TIMEOUT_MILLIS = 15_000L

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun resolveCurrentLocation(context: Context): DeviceLocationResult {
        if (!hasLocationPermission(context)) return DeviceLocationResult.PermissionDenied

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return DeviceLocationResult.Unavailable

        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        if (!gpsEnabled && !networkEnabled) return DeviceLocationResult.LocationDisabled

        val location = requestFreshFix(context, locationManager)
            ?: lastKnownLocation(context, locationManager)
            ?: return DeviceLocationResult.Unavailable

        return DeviceLocationResult.Success(
            ResolvedDeviceLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                cityName = reverseGeocode(context, location.latitude, location.longitude)
            )
        )
    }

    private suspend fun requestFreshFix(
        context: Context,
        locationManager: LocationManager
    ): Location? {
        if (!hasLocationPermission(context)) return null

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            hasFineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                LocationManager.GPS_PROVIDER
            }
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = android.os.CancellationSignal()
                    continuation.invokeOnCancellation { runCatching { signal.cancel() } }
                    try {
                        locationManager.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Location permission revoked while requesting a fix", e)
                        if (continuation.isActive) continuation.resume(null)
                    }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { locationManager.removeUpdates(this) }
                            if (continuation.isActive) continuation.resume(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {
                            runCatching { locationManager.removeUpdates(this) }
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    continuation.invokeOnCancellation { runCatching { locationManager.removeUpdates(listener) } }
                    try {
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Location permission revoked while requesting a fix", e)
                        if (continuation.isActive) continuation.resume(null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request single update", e)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun lastKnownLocation(context: Context, locationManager: LocationManager): Location? {
        // Ordered best-accuracy-first; used as a fallback on API < 30 and when no fresh fix arrives.
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (hasFineLocation) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return providers.mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission revoked while reading last known location", e)
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())

        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1) { result ->
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Geocoder failed asynchronously", e)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }.getOrNull()
            }
        }

        val address = addresses?.firstOrNull() ?: return null
        val city = address.locality
            ?: address.subAdminArea
            ?: address.adminArea
            ?: return address.countryName
        val country = address.countryName
        return if (country.isNullOrBlank()) city else "$city, $country"
    }
}
