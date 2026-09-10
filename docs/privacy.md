# Privacy Policy for Salati

**Last updated:** 10 September 2026

**Public URL:** [https://salati.sulfuro.xyz/privacy.html](https://salati.sulfuro.xyz/privacy.html)

**Salati** is an independent, privacy-first Islamic prayer companion developed by **Sulfuro** (Application ID: `com.sulfuro.salati`).

This markdown file is the in-repo copy of the public policy (`privacy.html`). If the two ever differ, the public page at the URL above is the one linked from the app and from Google Play.

---

## 1. Core principles

* **No user accounts.** No login, registration, passwords, or emails collected by Salati.
* **No advertisements.** No ad SDKs, banners, or advertising identifiers.
* **No analytics or telemetry.** No tracking tools, behavioral analytics, or crash-reporting SDKs.
* **No data selling.** Information is never sold, brokered, or used for advertising.
* **On-device first.** Preferences, coordinates, Zakat inputs, caches, and downloaded audio live in Android's app sandbox. Network use is limited to the cases below.

Contact: [salati@sulfuro.eu](mailto:salati@sulfuro.eu).

---

## 2. Permissions

### Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)

Optional. Requested only when you tap to detect GPS. You can refuse and search for a city by name instead. There is no default city. Location is never read in the background.

Detected or entered coordinates are stored locally. When a monthly timetable is downloaded, those coordinates are sent over HTTPS to the Aladhan API (`api.aladhan.com`).

### Notifications (`POST_NOTIFICATIONS`)

Prayer alerts, pre-reminders, and optional White Days notices on Android 13+.

### Exact alarms (`SCHEDULE_EXACT_ALARM`)

Used so a prayer notice can fire at the calculated minute. If access is unavailable, Salati falls back to inexact alarms, which Android may delay.

### Notification policy (`ACCESS_NOTIFICATION_POLICY`)

Only if you enable optional prayer silent mode. The previous ringer mode is restored after a bounded window.

### Foreground service (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)

Plays a downloaded adhan through to the end. A notification-channel sound would be cut off by the system.

---

## 3. Network

1. **Aladhan (`api.aladhan.com`)** — monthly prayer schedules. Sends configured latitude, longitude, year, month, method, school, and high-latitude rule. No account or advertising ID.
2. **Adhan catalogue and audio (`salati.sulfuro.xyz`)** — optional. Catalogue JSON and MP3 downloads, verified locally (size, type, SHA-256 when provided). Recordings are redistributed under their stated licenses (currently CC BY-SA 4.0) with attribution in the picker.
3. **Currency feed (`cdn.jsdelivr.net`, fallback `latest.currency-api.pages.dev`)** — optional Zakat metal-price refresh. No coordinates.

Connections use HTTPS. Service operators can see standard connection metadata such as the public IP address. Salati does not operate a developer backend.

City search uses the Android platform Geocoder (OS / Play services on the device), not a Salati server.

---

## 4. Android backup

Backup and device-to-device transfer are **enabled for the settings store only** (`datastore/salati_settings.preferences_pb`). That file includes the configured prayer location and any Zakat figures you entered.

Prayer caches, alarm registries, WorkManager state, and downloaded adhan files are excluded.

If Android Backup is on, Google may store that settings file as part of *your* backup. Salati does not keep a separate cloud copy.

---

## 5. Deletion

Clearing app storage or uninstalling removes the local copy. There is no Salati account to delete. Third-party operators keep data under their own policies.

---

## 6. Children

Salati is not directed at children under 13 and does not include social features, chat, or ads.

---

## 7. Contact

* Website: [https://salati.sulfuro.xyz](https://salati.sulfuro.xyz)
* Privacy policy: [https://salati.sulfuro.xyz/privacy.html](https://salati.sulfuro.xyz/privacy.html)
* Email: [salati@sulfuro.eu](mailto:salati@sulfuro.eu)
* Repository: [https://github.com/Sulfur0o/Salati](https://github.com/Sulfur0o/Salati)
