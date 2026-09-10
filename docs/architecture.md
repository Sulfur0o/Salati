# Salati architecture

## Product boundary

Salati's prayer location is user-configurable from Settings (city name, latitude, longitude), persisted in `CalculationSettings`, and used everywhere prayer times, dates, and alarms are computed. There is no default city: a fresh install has empty coordinates until the user sets a location during onboarding (GPS or city search). The device's own timezone does not drive date selection or scheduling once a location is saved — the saved prayer-location coordinates and timezone do. The application ID and Kotlin namespace are both `com.sulfuro.salati`. Current release: `versionCode 5` / `versionName 1.3.0`.

Changing location persists city name, coordinates, and timezone in a single settings update. Timezone resolution prefers the IANA zone Aladhan reports on a successful network fetch. If that lookup fails (offline or invalid payload), `CoordinateTimezoneLookup` estimates a zone from the *new* coordinates. The previous city's timezone is never kept just because the network was down.

## Implemented structure

The Compose UI contains Dashboard, Calendar, Zakat, and Settings screens with serializable Navigation 3 keys. Prayer dates use the configured prayer-location `LocalDate`; exact moments use `Instant` and explicit formatting in that same zone. Domain code sits under `core/computation` (prayer times, Hijri, Qibla), `core/zakat`, `core/alarms`, `core/work`, `core/alerts`, `core/permissions`, `core/location`, `core/audio`, and `core/device`.

`PrayerRepository` orchestrates:

- `PrayerRemoteDataSource`: Aladhan monthly URL and HTTP transport, built from the configured coordinates.
- `PrayerResponseParser`: validation and JSON decoding shared by cache and remote paths.
- `PrayerTimeMapper` (`SalatiPrayerTimeMapper`): date/time mapping using the per-day timezone Aladhan reports (falling back to the configured timezone), with Isha and the two night-midpoint fields rolled onto the following calendar day whenever their clock time falls at or before that day's Maghrib.
- `PrayerCacheDataSource`: per-path locked reads, invalidation, and atomic replacement.
- `indexPrayerDataByDate`: indexes a month's API rows by their own reported Gregorian date rather than array position, so a missing, duplicated, or reordered row can't silently shift prayer times onto the wrong date.

The repository is cache-first. Cache-only calls never use the API, including nested lookups (Hijri metadata resolution propagates the same cache-only flag). A network-capable miss or invalid cache falls back to Aladhan. If the network still fails, `LocalPrayerTimeCalculator` computes the month on-device. Remote JSON is parsed before cache replacement; valid data is returned even if writing fails. Computed months are not written to the cache. Typed failures preserve cached, API, temporary transport, and permanent configuration classifications. Cache identity includes the configured coordinates, year, month, method, Madhab, and high-latitude rule; payloads remain Aladhan JSON.

Preferences DataStore stores settings, including the user's location (`cityName`, `latitude`, `longitude`, `timezoneId`) and Zakat inputs. The alarm registry stores stable request codes, component/action/URI identity, extras, and `triggerAtMillis`. Android backup and device transfer include **only** the settings store; caches, alarm registry, WorkManager state, and downloaded adhan files are excluded.

## Alarms and background work

`AlarmScheduler` covers yesterday through today plus six dates in the configured prayer-location timezone so high-latitude Isha that rolled past local midnight is not dropped, excludes passed prayers, schedules five prayers on future dates, and never schedules Sunrise. Scheduling is skipped until onboarding is complete **and** a location has been configured. Main and pre-reminders have distinct identities. The scheduling "today" is derived from an explicit `ZoneId` (from settings) applied to the clock's instant, not from the clock's own zone or the device's default zone.

`AlarmRegistrar` owns replacement, rollback, cancellation, and restoration. It uses exact alarms only when permitted and falls back to inexact alarms when denied or after `SecurityException`. Registry replacement follows confirmed outcomes; an old alarm that fails to cancel during a normal refresh is kept in the registry (rather than dropped) so the next refresh retries cancelling it, and the refresh reports `SuccessWithStaleAlarms` instead of silently reporting a clean `Success`.

`AlarmReceiver` is synchronous: it builds a notification from intent extras using the established vibration/silent channels and dedicated icon. A chosen adhan is handed to `AdhanPlaybackService`, a `mediaPlayback` foreground service, because a notification-channel sound is cut off by the platform.

WorkManager identities and policies are:

- `salati_alarm_refresh`: unique cache-first one-time work, `REPLACE`.
- `salati_alarm_settings_refresh_debounce`: replaceable settings debounce.
- `salati_alarm_network_refresh`: unique connected fallback, `KEEP`.
- Periodic maintenance: 24-hour interval, six-hour flex, periodic `KEEP`.

Startup, reboot, package replacement, manual time setting, exact-permission changes, location changes, and relevant Activity resumes enqueue cache-first refresh. Missing cache queues connected fallback without looping the cache-only worker.

## Offline, battery, privacy, and release

First use requires the user to set a location. Uncached months prefer Aladhan; if the network fails they are computed on-device. Cached months allow offline restoration. Aladhan receives the configured coordinates, parameters, and the network IP. There are no accounts, analytics, trackers, or ads. Location permission is optional and used only on an explicit GPS tap; users may search for a city instead. Configured coordinates are sent over HTTPS to Aladhan when a schedule is downloaded.

Exact alarms remain subject to permission and device policy. Inexact alarms and WorkManager can be delayed by Doze, standby, and manufacturer restrictions. Starting the adhan foreground service from the background is reliably allowed from an exact alarm; the inexact path may fall back to a notification without audio.

Release builds use optimized-default R8 minification and resource shrinking with a minimal custom rules file. Consumer rules plus narrow worker and persisted-model keeps protect WorkManager and kotlinx.serialization. App Bundles disable language splits so in-app locale changes work on API 24–32.

Xiaomi / HyperOS, Samsung, Oppo, and similar OEMs remain a manual release gate for real alarm delivery.
