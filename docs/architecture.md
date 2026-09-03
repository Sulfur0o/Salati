# Salati architecture

## Product boundary

Salati's prayer location is user-configurable from Settings (city name, latitude, longitude), persisted in `CalculationSettings`, and used everywhere prayer times, dates, and alarms are computed. It defaults to Brussels (`50.8503`, `4.3517`, `Europe/Brussels`) on first install. The device's own timezone never influences requests, date selection, timestamp mapping, or scheduling — only the saved prayer-location coordinates and timezone do. The application ID is `com.sulfuro.salati`; the Kotlin namespace remains `io.github.sulfuro25.salati`.

Changing location resolves the IANA timezone for the new coordinates from the Aladhan API's per-day `meta.timezone` field (falling back to the previously saved timezone if that lookup fails, e.g. offline) and persists city name, coordinates, and timezone together in a single settings update, so a failed timezone lookup can never leave new coordinates paired with a stale timezone mid-update.

## Implemented structure

The Compose UI contains Dashboard, Calendar, Zakat, and Settings screens with serializable Navigation 3 keys. Prayer dates use the configured prayer-location `LocalDate`; exact moments use `Instant` and explicit formatting in that same zone.

`PrayerRepository` orchestrates:

- `PrayerRemoteDataSource`: Aladhan monthly URL and HTTP transport, built from the configured coordinates.
- `PrayerResponseParser`: validation and JSON decoding shared by cache and remote paths.
- `PrayerTimeMapper` (`SalatiPrayerTimeMapper`): date/time mapping using the per-day timezone Aladhan reports (falling back to the configured timezone), with Isha and the two night-midpoint fields rolled onto the following calendar day whenever their clock time falls at or before that day's Maghrib.
- `PrayerCacheDataSource`: per-path locked reads, invalidation, and atomic replacement.
- `indexPrayerDataByDate`: indexes a month's API rows by their own reported Gregorian date rather than array position, so a missing, duplicated, or reordered row can't silently shift prayer times onto the wrong date.

The repository is cache-first. Cache-only calls never use the API, including nested lookups (Hijri metadata resolution propagates the same cache-only flag). A network-capable miss or invalid cache falls back to Aladhan. Remote JSON is parsed before cache replacement; valid data is returned even if writing fails. Typed failures preserve cached, API, temporary transport, and permanent configuration classifications. Cache identity includes the configured coordinates, year, month, method, Madhab, and high-latitude rule; payloads remain Aladhan JSON.

Preferences DataStore stores settings, including the user's location (`cityName`, `latitude`, `longitude`, `timezoneId`). The alarm registry stores stable request codes, component/action/URI identity, extras, and `triggerAtMillis`. Backup and device transfer exclude all maintained preferences, registry, caches, databases, and files.

## Alarms and background work

`AlarmScheduler` covers yesterday through today plus six dates in the configured prayer-location timezone so high-latitude Isha that rolled past local midnight is not dropped, excludes passed prayers, schedules five prayers on future dates, and never schedules Sunrise. Main and pre-reminders have distinct identities. The scheduling "today" is derived from an explicit `ZoneId` (from settings) applied to the clock's instant, not from the clock's own zone or the device's default zone, so travel or a mismatched device clock can't shift alarms onto the wrong Gregorian date.

`AlarmRegistrar` owns replacement, rollback, cancellation, and restoration. It uses exact alarms only when permitted and falls back to inexact alarms when denied or after `SecurityException`. Registry replacement follows confirmed outcomes; an old alarm that fails to cancel during a normal refresh is kept in the registry (rather than dropped) so the next refresh retries cancelling it, and the refresh reports `SuccessWithStaleAlarms` instead of silently reporting a clean `Success`.

`AlarmReceiver` is synchronous: it builds a notification from intent extras using the established vibration/silent channels and dedicated icon. It does not access repositories, DataStore, schedulers, or coroutines.

WorkManager identities and policies are:

- `salati_alarm_refresh`: unique cache-first one-time work, `KEEP`.
- `salati_alarm_settings_refresh_debounce`: replaceable settings debounce.
- `salati_alarm_network_refresh`: unique connected fallback, `KEEP`.
- Periodic maintenance: 24-hour interval, six-hour flex, periodic `KEEP`.

Startup, reboot, package replacement, manual time setting, exact-permission changes, location changes, and relevant Activity resumes enqueue cache-first refresh. Missing cache queues connected fallback without looping the cache-only worker. A device-timezone receiver is intentionally unnecessary, since scheduling never depends on the device's timezone.

## Offline, battery, privacy, and release

First use and uncached months require connectivity. Cached months allow offline restoration; a month boundary requires both months cached. Aladhan receives the configured coordinates, parameters, and the network IP. There are no accounts, analytics, trackers, ads, foreground service started by the app, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission. Location permission is optional and used only on an explicit GPS tap; users may skip GPS or enter coordinates manually. Configured coordinates are sent over HTTPS to Aladhan when a schedule is downloaded.

Exact alarms remain subject to permission and device policy. Inexact alarms and WorkManager can be delayed by Doze, standby, and manufacturer restrictions.

The current durable release is `versionCode 2` / `versionName 1.1.0`. Release builds use optimized-default R8 minification and resource shrinking with a minimal custom rules file. Consumer rules plus narrow worker and persisted-model keeps protect WorkManager and kotlinx.serialization. App Bundles disable language splits so in-app locale changes work on API 24–32.

Xiaomi 11T Pro / HyperOS 1.0 / Android 14 qualification is partial. Exact revocation; reboot/update/time-set; mute/offline; Xiaomi Autostart, battery, and Cleaner; real alarm/vibration delivery; Doze and long idle remain manual release gates.
