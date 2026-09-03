# Salati Google Play Release Checklist

This checklist is for application ID `io.github.sulfuro25.salati`, version code `2`, version name `1.1.0`.

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

Before every later release, increase `versionCode`. Keep the application ID unchanged after the first Play upload.

## 3. Complete Play Console declarations

- Verify the developer identity and register the app/package in Play Console.
- App access: all functionality is available without an account.
- Ads: no.
- Target audience: select only the age groups the listing is actually designed for. Selecting children invokes the Families policy.
- Complete the IARC content-rating questionnaire; do not enter a rating manually as a substitute.
- Complete the Data safety form using `PLAY_STORE_METADATA.md`. Automatic location can transmit approximate or precise coordinates to the Aladhan API for app functionality, so do not declare that all processing stays on-device.
- Privacy policy URL: `https://salati.sulfuro.xyz/privacy.html`. Confirm that this public URL works before submission; the same URL is available from in-app Settings.
- Review sensitive-permission declarations shown by Play Console. The app uses user-granted `SCHEDULE_EXACT_ALARM`, foreground-only location, and notifications. It does not request background location or `USE_EXACT_ALARM`.
- If this is a personal developer account created after November 13, 2023, complete the required closed test with at least 12 continuously opted-in testers for 14 days before applying for production access.

## 4. Store listing assets

- 512 x 512, 32-bit PNG Play icon, no more than 1 MB.
- 1024 x 500 JPEG or 24-bit PNG feature graphic.
- At least two phone screenshots; four portrait screenshots at 1080 x 1920 or higher are recommended.
- Verify the app name, short description, and full description in `PLAY_STORE_METADATA.md` against the final build.
- Add a support email in Play Console and keep the GitHub issues URL available for support and privacy questions.

## 5. Manual device checks

- API 24: launch, onboarding, manual location, prayer schedule, calendar, Qibla fallback, and Zakat.
- API 31/32: exact-alarm access denied and granted; confirm the inexact fallback and settings return flow.
- API 33+: notification permission denied and granted.
- API 35/36: edge-to-edge layout, predictive back, notification delivery, and alarm restoration.
- Phone, tablet/foldable, portrait, landscape, split-screen, large font, and display scaling.
- First launch online, cached/offline launch, failed API calls, and retry behavior.
- Timezone and daylight-saving changes, manual clock changes, reboot, app update, and force-stop recovery.
- Light/dark themes, RTL layout, TalkBack labels, and touch-target sizes.
- A real device with compass sensors, including low-accuracy/calibration behavior.

Do a staged production rollout after internal/closed testing and monitor Play pre-launch reports and Android vitals before increasing rollout percentage.
