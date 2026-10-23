# Connect DARKTEMP to your Firebase project

The source contains no project credentials and is buildable without Firebase configuration. Without a valid `app/google-services.json` that includes a Realtime Database URL, Android displays a setup message. These steps require your Firebase Console access. Rules have not been deployed by this project.

## 1. Create and register the project

1. Open the [Firebase Console](https://console.firebase.google.com/) and create your project. Analytics is unnecessary for this MVP.
2. Add an **Android** application with package name **`com.darktemp.app`**, exactly as in `app/build.gradle.kts`. A signing SHA fingerprint is unnecessary for Email/Password authentication.
3. Download `google-services.json` and place it at **`app/google-services.json`**, beside `app/build.gradle.kts`. This file is ignored by Git so your own configuration is not accidentally shared. Do not paste its contents into Kotlin.
4. If you selected another Firebase project, replace the configuration file with the correct one. If the client package in that file differs, register the exact `com.darktemp.app` package and download again. Do not manually rename JSON client entries or partially change the Gradle package.

## 2. Enable Authentication

1. In **Build → Authentication → Get started → Sign-in method**, enable **Email/Password**. Email-link login is unnecessary.
2. Users create their own normal accounts with the Android registration wizard. The app validates required names, email syntax, matching passwords, and at least six password characters. Firebase enforces any additional password policy you configure and the app displays the resulting error.
3. Registration creates the Auth account, writes profile plus default preferences atomically, and only then returns success and signs the new account out. Sign in manually afterward.
4. If Auth creation succeeds but profile storage fails, the account remains recoverable: correct the rules/connection, sign in with that email and password, and complete the profile recovery screen. Do not attempt to register the same email again. Recovery preserves valid existing preferences and repairs missing/invalid preferences.
5. Password reset uses Firebase's reset email; check spam and the Authentication email templates if delivery is delayed. Never share or store a user's plaintext password.

## 3. Create Realtime Database

1. Open **Build → Realtime Database → Create database**, choose your region, and start in **locked/production mode**.
2. Copy the exact HTTPS database URL shown in the database page. Its domain depends on the database region. Do not guess a URL from a project name.
3. Re-download `google-services.json` after creating the database and replace the copy in `app/`. Verify it has `project_info.firebase_url` pointing to the selected database. Firebase's Gradle plugin generates Android resources from this file; `FirebaseProvider` obtains the URL from `FirebaseApp.options.databaseUrl`.
4. The firmware uses the same database URL in its local `secrets.h`. Configuration details are in [FIRMWARE_SETUP.md](FIRMWARE_SETUP.md).

## 4. Create the dedicated device account

1. In **Authentication → Users → Add user**, create a separate email/password account exclusively for the ESP32. Use a strong unique password. Do not use a group member's normal Android account.
2. Copy the account's Firebase-generated **User UID** exactly. This UID, not its email, grants the device sensor-write access.
3. Put the email/password, project's **Web API key** from project settings, and actual database URL in `firmware/esp32_darktemp/secrets.h`, copied from the supplied template. The Web API key identifies the project; it does not bypass authentication or rules.
4. Keep that secrets file local and excluded from source control. No Firebase Admin SDK key or service-account JSON belongs in either Android or firmware. Microcontroller firmware credentials can be extracted; this scoped account is appropriate for a student prototype, not production hardware identity.

## 5. Publish the exact rules

The complete rules are [database.rules.json](database.rules.json). They deny root access, isolate each account, validate field types and ranges, index history/alerts by timestamp, and permit sensor writes only from the configured device UID. The device account cannot access any user profile, including its own. Field names match both the Kotlin mapper and ESP32 firmware. If this Firebase project already has older DARKTEMP rules deployed, publish the current complete rules file again so the new light-alert preference and alert records are accepted.

From the repository root in PowerShell, replace every device UID placeholder:

```powershell
$deviceAuthUid = Read-Host 'Paste the dedicated device account UID'
$rulesPath = Join-Path (Get-Location) 'database.rules.json'
$rulesText = Get-Content -Raw -LiteralPath $rulesPath
$rulesText.Replace('REPLACE_WITH_DEVICE_AUTH_UID', $deviceAuthUid) | Set-Content -LiteralPath $rulesPath -Encoding utf8
Get-Content -Raw -LiteralPath $rulesPath
```

Review that all occurrences were replaced, including the exclusion under `users`. Copy the **entire file** into **Realtime Database → Rules** and select **Publish**. Alternatively, after installing the Firebase CLI and signing in:

```powershell
firebase login
$firebaseProjectId = Read-Host 'Enter your actual Firebase project ID'
firebase deploy --only database --project $firebaseProjectId
```

The checked-in `firebase.json` points to the actual rules file. The placeholder intentionally grants no real device write access until replaced. Never use public read/write rules to resolve an error. The app does not create or deploy security rules for you. For subsequent projects, replace the old UID in **all** locations with the new dedicated UID.

## 6. Build and connect Android

Open the repository in Android Studio, allow Gradle sync, and run on an emulator or Android phone with internet access. Or run from the repository root with the configured Android SDK and supported JDK:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Rebuild after adding/replacing `google-services.json`; a previously installed APK does not acquire the file automatically. No Android runtime location or notification permission is required. Firebase uses HTTPS; cleartext traffic is disabled. A normal Firebase session remains signed in across app restarts until sign-out or session invalidation.

## 7. Verify with real readings

1. Register a test user. Confirm the app returns to Login with success, then sign in manually.
2. In the Console confirm `users/{thatAuthUid}` contains names, email, server creation timestamp, and defaults: `C`, alerts enabled, minimum 18, maximum 35. Do not manually seed sensor records.
3. Flash and run the real ESP32 as described in [FIRMWARE_SETUP.md](FIRMWARE_SETUP.md). Confirm `devices/device001/latest` and a new history entry appear for each successful sample.
4. Dashboard should display that real sample and timestamp. Cover/uncover the calibrated LDR to verify LIGHT/DARK. Stop uploads and wait over 90 seconds to verify stale status. DARK is an illumination state, not a power failure.
5. Switch °C/°F and confirm Dashboard, History, settings limits, and alert values convert while Firebase sensor values remain Celsius. Save, restart, and confirm the setting persists.
6. With DARKTEMP open, choose safe limits near the current ambient reading to observe an outside crossing on the next sample. Restore a normal zone, then cross again. Verify one alert per crossing, none for repeated samples, and no replay after clearing/restarting. Limits are informational; the system controls no equipment.
7. Test all/unread, mark read/all read, and clear. Sign out and sign into a second normal account to verify its private settings/profile/inbox. The sensor is intentionally shared.

## Local security tests (no cloud project needed)

The tests use Firebase's Realtime Database emulator, a synthetic `demo-` project ID, and test-only data. They do not write to any live project. Node.js 22 or later and a Java version supported by the Firebase CLI are required. The initial emulator run downloads its JAR.

```powershell
Push-Location .\rules-tests
npm.cmd ci
npm.cmd test
Pop-Location
```

Tests check anonymous denial, cross-user denial, device isolation, immutable history with identical retries, malformed fields, timestamp limits, profile validation, preference validation, atomic alert state, concurrent sessions, and clear-without-replay state retention. Run the suite **before** replacing the placeholder for your own UID; the test harness substitutes an emulator-only UID. If you have already configured a real UID, use the original template in a separate checkout for the tests, or update the test harness replacement target locally; never use production credentials in tests.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Setup-required screen | Correct file at `app/google-services.json`; package `com.darktemp.app`; `project_info.firebase_url`; rebuild and reinstall |
| No matching client found at build | JSON downloaded for a different Android package |
| Sign-in operation not allowed | Enable Email/Password in the same Firebase project |
| Invalid API key / project mismatch | Re-download configuration from the intended project; ensure firmware and Android share that project |
| Email already in use after failed registration | Sign in and complete profile recovery, or use Forgot Password |
| Permission denied on profile write | Full rules deployed; account is a normal user rather than the dedicated device account |
| Permission denied on firmware upload | Correct dedicated Auth UID substituted everywhere; correct database URL; valid fields/time; token refreshed |
| Waiting for readings | Real device has not uploaded; inspect Serial Monitor, Wi-Fi, NTP, DHT11, credentials, and rules |
| Stale/future reading | Verify sensor uploads and automatic date/time on the phone; check NTP synchronization |
| Connected but denied/error data | Connection status means database transport; it does not override permission or mapping errors |
| Reset email not received | Valid address, spam folder, Firebase email template/settings, rate limiting; Firebase may avoid disclosing whether an address has an account |

Official references verified for this implementation: [Android setup and main Firebase modules](https://firebase.google.com/docs/android/setup), [Realtime Database reads/writes and transactions](https://firebase.google.com/docs/database/android/read-and-write), [Database security rules](https://firebase.google.com/docs/database/security), and [Rules test environment](https://firebase.google.com/docs/rules/unit-tests).
