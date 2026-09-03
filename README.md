# Salati

**Salati** is a private, accurate, and ad-free Android prayer companion application for Muslims worldwide.

---

## ✨ Features

- **Worldwide Prayer Calculations**: Optional GPS detection, manual latitude/longitude entry, or a default location. Supports all major calculation authorities:
  - Muslim World League (MWL)
  - Umm al-Qura University, Makkah
  - Islamic Society of North America (ISNA)
  - Egyptian General Authority of Survey
  - University of Islamic Sciences, Karachi
  - Dubai & Gulf Region Standards
  - Moonsighting Committee Worldwide
- **3D Tilt-Compensated Qibla Compass**: Real-time Kaaba direction indicator with dynamic pitch compensation (accurate flat or held upright).
- **Islamic Calendar & Hijri Events**: Side-by-side Gregorian and Hijri calendar, key Islamic holidays, and White Days fasting reminders.
- **Zakat al-Maal Engine**: Comprehensive calculation across cash, bank balances, gold (10k–24k), and silver with customizable live metal valuation.
- **Reliable On-Time Alarms**: Exact alarms scheduled per prayer with pre-prayer reminders, vibration alerts, and background restoration across device reboots and timezone shifts.
- **Battery Optimization Helper**: OEM-tailored background execution guides for Xiaomi/HyperOS, Samsung, Oppo, and Huawei devices.
- **Privacy-First**: Zero ads, accounts, analytics, or tracking SDKs. Settings stay on-device; configured coordinates are sent only to the prayer-times API when a schedule is downloaded.

---

## 🔒 Permissions and Privacy

- **Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)**: Optional; used only when you tap to detect GPS. You can skip this or enter coordinates manually.
- **Notifications (`POST_NOTIFICATIONS`)**: Used to post prayer alerts and reminders on Android 13+.
- **Exact Alarms (`SCHEDULE_EXACT_ALARM`)**: Used solely to ensure prayer reminders trigger at the exact calculated prayer time. Falls back to inexact alarms if denied.
- **Notification policy (`ACCESS_NOTIFICATION_POLICY`)**: Optional prayer silent mode; previous ringer mode is always restored.

Public privacy policy: [https://salati.sulfuro.xyz/privacy.html](https://salati.sulfuro.xyz/privacy.html). See also [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## 🛠️ Build & Testing

### Fast Development Test (< 30 seconds):
```powershell
.\gradlew.bat testDebugUnitTest
```

### Full Release Test Suite:
```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest
```

### Build Debug APK:
```powershell
.\gradlew.bat assembleDebug
```

## 📱 Release Specifications

- **Package ID:** `com.sulfuro.salati`
- **Min SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 16 (API 36)
- **Architecture:** Jetpack Compose, Material 3, Navigation 3, Coroutines & Flow, WorkManager, DataStore.
