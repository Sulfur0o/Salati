# Privacy Policy for Salati

**Last updated:** September 3, 2026

**Public URL:** [https://salati.sulfuro.xyz/privacy.html](https://salati.sulfuro.xyz/privacy.html)

**Salati** is an independent, privacy-first Islamic prayer companion application developed by **sulfuro25** (Application ID: `com.sulfuro.salati`).

Your privacy is our utmost priority. Salati is designed from the ground up to respect your personal data and protect your device security.

---

## 1. Core Privacy Principles

* **No User Accounts:** Salati does not require, support, or use accounts, registrations, passwords, or emails.
* **No Advertisements:** Salati contains zero advertisements, banners, or advertising SDKs.
* **No Analytics or Telemetry:** We do not include any tracking tools, behavioral analytics, telemetry SDKs, or crash-reporting trackers.
* **No Data Selling or Advertising Use:** We never sell, monetize, broker, or use personal data for advertising.
* **Local-First Storage:** Preferences, calculations, configured coordinates, and cached responses are stored on your device in Android's sandboxed storage. Limited network transfers required for prayer schedules and currency conversion are described below.

---

## 2. Device Permissions and Usage

Salati requests only the minimal set of permissions strictly necessary to deliver its core features:

### A. Location Permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)
* **Purpose:** To determine your geographic coordinates (latitude and longitude) solely to compute local astronomical prayer times and Qibla direction. When location is detected via GPS, the Android platform Geocoder API (provided by your device OS/Google Play Services) is invoked locally to resolve human-readable city and country names for display.
* **User Control:** Location permission is **optional**. You are never prompted for location access until you explicitly tap "Detect my location". You can skip GPS during onboarding (the app keeps the default Brussels coordinates), or enter a city name, latitude, and longitude manually at any time without granting location permissions.
* **Data Handling:** Detected or entered coordinates are stored locally on your device in sandboxed app storage. Salati never accesses location in the background or tracks movement over time. When monthly prayer timetables are retrieved, your **configured** latitude and longitude (default, GPS, or manual) are transmitted over secure HTTPS to the Aladhan API (`api.aladhan.com`).

### B. Notification Permission (`POST_NOTIFICATIONS`)
* **Purpose:** On Android 13 (API 33) and above, this permission allows Salati to deliver on-time prayer time reminders and voluntary White Days fasting notifications.
* **User Control:** You have granular and global control over all notifications. In app Settings, you can mute all notifications at once ("Mute All Notifications"), enable/disable alerts per prayer, customize pre-prayer warning offsets (0–30 minutes), and toggle sound and vibration modes.

### C. Exact Alarms Permission (`SCHEDULE_EXACT_ALARM`)
* **Purpose:** Islamic prayers must be observed at exact times. Salati uses Android's Exact Alarm API solely to schedule precise local alarm intents for prayer notifications and pre-reminders. If access is unavailable, the app falls back to inexact alarms and does not crash.
* **Compliance:** Exact alarms are never used for marketing, background data scraping, or generic reminders.

### D. Notification Policy Access (`ACCESS_NOTIFICATION_POLICY`)
* **Purpose:** Used only if you enable optional prayer silent mode. Salati checks that policy access is granted, silences the ringer for a bounded window after Adhan, and restores your previous ringer mode.
* **Compliance:** Salati never leaves the device in a permanent silent or Do Not Disturb state.

---

## 3. External Network Communications

To provide accurate prayer schedules and live precious metal prices, Salati interacts with public APIs:

1. **Prayer Times API (Aladhan, `api.aladhan.com`):** Salati downloads monthly prayer schedules by sending the configured latitude, longitude, year, month, calculation method, school, and high-latitude rule. No account identifier, advertising ID, device serial, email address, or analytics identifier is included by the app.
2. **Currency feed (`cdn.jsdelivr.net` with fallback to `latest.currency-api.pages.dev`):** When the user refreshes metal prices, Salati requests the selected currency's public exchange-rate file. The app does not include coordinates or app-specific identifiers in this request.

Connections use HTTPS. Like any internet request, service operators and network infrastructure can receive standard connection metadata such as the device's public IP address and request time. Salati does not operate a developer backend and does not receive copies of these requests. Third-party services process requests under their own practices, which Salati does not control.

Prayer responses and relevant currency results may be cached locally for offline use.

---

## 4. Data Retention and Deletion

All data retained by Salati itself (including saved coordinates, cached schedules, prayer settings, and notification preferences) resides within your device's private application sandbox. Android cloud backup and device-to-device transfer of this app data are disabled.

You can reset all data at any time by clearing the application storage via Android System Settings, or by uninstalling the application.

---

## 5. Contact & Open Source

If you have questions, feedback, or concerns regarding this Privacy Policy, please open an issue or reach out via our repository:

* **Website:** [https://salati.sulfuro.xyz](https://salati.sulfuro.xyz)
* **Privacy policy:** [https://salati.sulfuro.xyz/privacy.html](https://salati.sulfuro.xyz/privacy.html)
* **Repository:** [https://github.com/Sulfur0o/Salati](https://github.com/Sulfur0o/Salati)
* **Privacy questions and support:** [https://github.com/Sulfur0o/Salati/issues](https://github.com/Sulfur0o/Salati/issues)
