# Google Play Store Listing & Compliance Metadata

## 1. App Identity & Basic Info
* **App Name:** Salati – Prayer Times & Qibla
* **Short Description (max 80 chars):** Private, accurate Islamic prayer times, Qibla compass & Zakat calculator.
* **Category:** Lifestyle / Productivity
* **Content Rating:** Everyone (3+)
* **Contains Ads:** No
* **App Access:** All functionality is available without special access restrictions.
* **Target Audience:** Worldwide Muslim community (13+)

---

## 2. Full Store Description (max 4000 chars)

Salati is a clean, modern, and privacy-focused Islamic companion application designed to help you maintain your daily prayers with confidence wherever you are in the world.

Key Features:

🕌 ACCURATE PRAYER TIMES
- Precise astronomical prayer times calculated specifically for your geographic coordinates.
- Supports all major global calculation standards: Muslim World League (MWL), Umm al-Qura (Makkah), ISNA (North America), Egyptian General Authority of Survey, University of Islamic Sciences (Karachi), Dubai / Gulf Region, and Moon Sighting Committee.
- Automatic Asr calculation according to Shafi'i / Maliki / Hanbali or Hanafi jurisprudence.
- Midnight and Last Third of the Night calculations for Qiyam al-Layl.

🧭 3D TILT-COMPENSATED QIBLA COMPASS
- High-precision compass pointing directly to the Holy Kaaba in Makkah.
- Real-time magnetic and sensor tilt compensation, working seamlessly whether your phone is flat on a surface or held upright in front of you.

📅 ISLAMIC HIJRI CALENDAR & EVENTS
- Complete monthly calendar showing Gregorian and Hijri dates side-by-side.
- Major Islamic occasions and holidays (Islamic New Year, Ashura, Ramadan, Eid al-Fitr, Day of Arafah, Eid al-Adha).
- Dedicated White Days (Ayyam al-Beed) fasting indicators and eve-of-fast reminders.

💰 ZAKAT AL-MAAL CALCULATOR
- Comprehensive Zakat calculation engine according to Islamic jurisprudence.
- Aggregate wealth evaluation for cash, savings, investments, and precious metals.
- Accurate gold and silver Nisab thresholds based on selected gold purity (24k, 21k, 18k, 14k, 10k).
- Live commodity price refreshes in multiple global currencies.

🔔 RELIABLE TIMELY NOTIFICATIONS & EXACT ALARMS
- Exact on-time alarm alerts for Fajr, Dhuhr, Asr, Maghrib, and Isha.
- Configurable pre-prayer alerts (5 to 30 minutes before prayer).
- Independent vibration and notification sound settings per prayer.
- Background alarm resilience across device reboots and timezone changes.

🔒 ABSOLUTE PRIVACY & TRUST
- 100% Ad-Free: Zero advertisements, banners, or tracking.
- No Accounts Required: No login, password, email, or registration.
- Privacy by design: No analytics, telemetry, or behavioral tracking. Preferences remain on-device; configured coordinates are sent over HTTPS only when retrieving prayer schedules.
- Full Offline Support: Once downloaded, monthly prayer schedules remain cached and fully accessible without an internet connection.

---

## 3. Google Play Data Safety Declarations

| Question | Answer | Details |
| :--- | :--- | :--- |
| **Data Collection** | Yes — location | Configured coordinates (default Brussels, optional GPS, or manual entry) are stored locally and transmitted to the Aladhan API when prayer schedules are retrieved. GPS collection is optional. Declare the purpose as **App functionality**. |
| **Ephemeral Processing** | Yes for API requests | Salati uses the coordinates only to complete the prayer-schedule request and does not operate a developer backend. The API operator may independently receive standard request metadata such as the IP address. Metal-price refresh calls a public currency feed without coordinates; the CDN may still see the network IP. |
| **Data Shared** | Yes — conservative declaration | Coordinates are transferred to the third-party Aladhan prayer-times service. Only select a Play “service provider” sharing exception if the provider relationship actually satisfies Google Play's definition. |
| **Security Practices** | Encrypted in transit / local storage | API calls use HTTPS. Preferences and cached responses are stored in Android's app sandbox; Android backup and device-transfer export are disabled. |
| **User Deletion** | Local deletion supported | Clearing app storage or uninstalling removes local preferences, coordinates, and cached responses. Salati has no account or developer backend containing user profiles. |

---

## 4. Permission Justifications (Play Console Declaration)

* **`SCHEDULE_EXACT_ALARM`**: "Salati is an Islamic prayer time utility that must notify users at the exact minute each prayer begins. Access is requested by the user through Android settings. If access is unavailable, the app falls back to an inexact alarm."
* **`POST_NOTIFICATIONS`**: "Required on Android 13+ to display prayer time alerts, approaching prayer reminders, and White Days notifications."
* **`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`**: "Used solely when the user taps to detect the current GPS location for prayer times and Qibla. Location is optional: users can skip GPS, keep the default coordinates, or enter latitude and longitude manually. Location is never accessed in the background. Configured coordinates are sent over HTTPS to the Aladhan API when prayer schedules are retrieved."
* **`ACCESS_NOTIFICATION_POLICY`**: "Used only if the user enables optional prayer silent mode. Salati checks that Do Not Disturb / notification-policy access is granted, silences the ringer for a bounded window after Adhan, and restores the previous ringer mode. It never leaves the device in a permanent silent or DND state."

## 5. Required Console and Listing Links

* **Privacy policy URL:** `https://salati.sulfuro.xyz/privacy.html`
* The same URL is linked from the in-app Settings screen.
* Complete the Data safety form even for testing tracks other than internal testing.
