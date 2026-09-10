# Salati Google Play Release Checklist

This checklist is for application ID `com.sulfuro.salati`, version code `5`, version name `1.3.0`.

## 1. Run the release gates

Use JDK 17 or newer. Android Studio's bundled JDK is suitable.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assembleDebug assembleAndroidTest
```

Run the instrumented tests on at least one API 36 emulator or device:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 2. Configure the upload key outside the repository

Create an external properties file that is not stored in this repository:

```properties
storeFile=C:/secure/salati-upload.jks
storePassword=replace-with-secret
keyAlias=salati-upload
keyPassword=replace-with-secret
```

Build a signed, optimized Android App Bundle. The `salati.requireSigning` flag makes the build fail instead of silently producing an unsigned upload artifact.

```powershell
$env:SALATI_SIGNING_PROPERTIES = 'C:\secure\salati-signing.properties'
.\gradlew.bat -Psalati.requireSigning=true clean testDebugUnitTest testReleaseUnitTest lintRelease bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Enroll in Play App Signing and securely back up the upload key and its passwords. Never commit the keystore or signing properties.

Before every later release, increase `versionCode`. Keep the application ID `com.sulfuro.salati` unchanged after the first Play upload.

Confirm Play Console reports 16 KB page-size compatibility for the native libraries in the bundle. Local ELF check of the current AndroidX `.so` files (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) shows `p_align >= 16384` on every ABI.

After pushing, deploy `adhans.json`, `audio/fajr/`, and `audio/regular/` to `https://salati.sulfuro.xyz/`. Remove any old recordings that are no longer in the catalogue.

## 3. Complete Play Console declarations

- Verify the developer identity and register the package `com.sulfuro.salati`.
- App access: all functionality is available without an account.
- Ads: no.
- Target audience: 13+ only. Selecting children invokes the Families policy.
- Complete the IARC content-rating questionnaire; do not enter a rating manually.
- Complete the Data safety form using `docs/play-store.md`. Location is sent to Aladhan. Android Backup is **on** for the settings store (location + preferences + Zakat inputs). Do not declare that backup is disabled.
- Privacy policy URL: `https://salati.sulfuro.xyz/privacy.html`. Confirm that this public URL matches the current policy before submission.
- Support email: `salati@sulfuro.eu`.
- Declare exact alarms, foreground-only location, notifications, optional DND access, and the `mediaPlayback` foreground service used for adhan playback. The app does not request background location or `USE_EXACT_ALARM`.
- Personal developer accounts created after 13 November 2023 need a closed test with at least 12 testers opted in for 14 consecutive days before production access. Opt-in is what counts, not merely adding emails to a list.

## 4. Store listing assets

- 512 x 512, 32-bit PNG Play icon, no more than 1 MB.
- 1024 x 500 JPEG or 24-bit PNG feature graphic.
- At least two phone screenshots; four portrait screenshots at 1080 x 1920 or higher are recommended.
- Verify the app name, short description, and full description in `docs/play-store.md` against the final build.
- Play icon, feature graphic, and screenshots: `docs/play-listing/`. Recapture screenshots on the current build before upload.

## 5. Manual device checks

- Fresh install: onboarding **requires** a location (GPS or city search). There is no skip-to-default-city path.
- Offline location change: new coordinates must not keep the previous city's timezone.
- API 24: launch, onboarding, city search, prayer schedule, calendar, Qibla fallback, and Zakat.
- API 31/32: exact-alarm access denied and granted; confirm the inexact fallback and settings return flow.
- API 33+: notification permission denied and granted.
- API 35/36: edge-to-edge layout, predictive back, notification delivery, and alarm restoration.
- Phone, tablet/foldable, portrait, landscape, split-screen, large font, and display scaling.
- First launch online, cached/offline launch, failed API calls, and retry behavior.
- Timezone and daylight-saving changes, manual clock changes, reboot, app update, and force-stop recovery.
- Light/dark themes, RTL layout, TalkBack labels, and touch-target sizes.
- A real device with compass sensors, including low-accuracy/calibration behavior.
- Xiaomi / Samsung / Oppo: Fajr delivery with battery restrictions on and off.

Do a staged production rollout after closed testing and monitor Play pre-launch reports and Android vitals before increasing rollout percentage.
