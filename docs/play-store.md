# Google Play Store Listing & Compliance Metadata

## 1. App Identity & Basic Info
* **App Name:** Salati – Prayer Times & Qibla
* **Short Description (max 80 chars):** Private, accurate Islamic prayer times, Qibla compass & Zakat calculator.
* **Category:** Lifestyle
* **Content Rating:** Everyone (complete the IARC questionnaire; do not type a rating by hand)
* **Contains Ads:** No
* **App Access:** All functionality is available without an account
* **Target Audience:** 13+ only. Do **not** select under-13 / Families.
* **Support email:** `salati@sulfuro.eu`

---

## 2. Full Store Description (max 4000 chars)

Salati is a private, ad-free Islamic companion for daily prayer. No accounts, no ads, no analytics.

PRAYER TIMES
- Astronomical times for the city you choose (GPS or city search — there is no default city).
- Methods: Muslim World League, Umm al-Qura (Makkah), ISNA, Egyptian Authority, Karachi, Kuwait, Qatar, Dubai, Singapore, and the Moon Sighting Committee.
- Asr by Shafi'i / Maliki / Hanbali or Hanafi.
- Midnight and last third of the night for Qiyam.

QIBLA COMPASS
- Direction to the Kaaba from your saved location.
- Tilt compensation, flat or upright, with magnetic declination.

HIJRI CALENDAR
- Gregorian and Hijri side by side.
- Islamic New Year, Ashura, Ramadan, Eid al-Fitr, Day of Arafah, Eid al-Adha.
- White Days (Ayyam al-Beed) markers and optional eve-of-fast reminder.

ZAKAT CALCULATOR
- Cash, savings, receivables, gold (several karats), and silver.
- Nisab from gold or silver. Metal prices refresh from a public currency feed (approximate, not a certified bullion quote).

NOTIFICATIONS
- Alerts for Fajr, Dhuhr, Asr, Maghrib, and Isha, plus optional pre-reminders.
- Exact alarms when Android allows them; otherwise inexact alarms (the system may delay these, especially on battery-restricted phones).
- Optional downloaded adhan (Creative Commons licensed recordings, attributed in Settings), played through a foreground notification with a Stop button. If Android refuses background playback, the notification includes a Play action.
- Alarms are restored after reboot and app update. On Xiaomi, Samsung, Oppo, and similar devices, you may still need to unrestrict the app in battery settings.

PRIVACY
- No ads, no accounts, no analytics SDKs.
- Location is optional and used only when you tap to detect GPS, or when you pick a city.
- Configured coordinates are sent over HTTPS to the Aladhan prayer-times API when a month is downloaded.
- Settings (including location and any Zakat figures you entered) can be included in Android Backup. Prayer caches and downloaded audio are not.

Once a month is on the device, that month can be used offline. Uncached months are computed on the device if the network is down.

---

## 3. Google Play Data Safety Declarations

| Question | Answer | Details |
| :--- | :--- | :--- |
| **Data Collection** | Yes — location; optionally financial info you type into Zakat | Configured coordinates are stored locally and sent to Aladhan when a schedule is downloaded. GPS is optional (tap to detect). Zakat amounts stay on-device and may be included in Android Backup. Purpose: **App functionality**. |
| **Ephemeral Processing** | Yes for API requests | Coordinates are used to complete the prayer-schedule request. Salati has no developer backend. The API operator may see IP address and request time. Metal-price refresh does not include coordinates; the CDN may still see the network IP. |
| **Data Shared** | Yes — location with Aladhan | Coordinates go to the third-party Aladhan prayer-times service. Do not tick a “service provider” exception unless that relationship actually qualifies. Adhan files are fetched from `salati.sulfuro.xyz` only if the user downloads one. |
| **Security Practices** | Encrypted in transit | HTTPS for network calls. Local data is in the app sandbox. **Android Backup is on for the settings store only** (location + preferences + Zakat inputs). Declare backup as collected / backed up if Play asks. |
| **User Deletion** | Local deletion | Clear storage or uninstall. No Salati account. Android Backup copies are controlled by the user’s Google account. |

---

## 4. Permission Justifications (Play Console)

* **`SCHEDULE_EXACT_ALARM`**: "Salati is a prayer-time utility. Exact alarms fire at the calculated prayer minute. If the user does not grant access, the app falls back to inexact alarms."
* **`POST_NOTIFICATIONS`**: "Prayer alerts, pre-reminders, and optional White Days notices on Android 13+."
* **`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`**: "Used only when the user taps to detect GPS for prayer times and Qibla. Location is optional: the user can search for a city by name instead. Never used in the background. Configured coordinates are sent over HTTPS to Aladhan when a month is downloaded."
* **`ACCESS_NOTIFICATION_POLICY`**: "Optional prayer silent mode. Silences the ringer for a bounded window after Adhan and restores the previous ringer mode."
* **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: "Plays a user-downloaded adhan to completion. A notification sound would be cut off by the system. The notification includes a Stop action."

## 5. Required Console and Listing Links

* **Privacy policy URL:** `https://salati.sulfuro.xyz/privacy.html`
* **Support email:** `salati@sulfuro.eu`
* The privacy URL is also linked from in-app Settings.
* Complete the Data safety form even on closed testing.
* Complete the IARC questionnaire. Target only 13+.
