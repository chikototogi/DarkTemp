# Verification and acceptance tests

Record date: **October 14, 2026 (Asia/Manila)**. This document distinguishes local automated verification from tests requiring the owner's Firebase project and physical sensors.

## Automated checks

Latest local verification: `assembleDebug`, `testDebugUnitTest` (20 tests), and `lintDebug` passed. The Firebase rules emulator suite passed all 8 tests. These commands reproduce the Android checks:

```powershell
Set-Location 'C:\Users\ark\Desktop\DarkTemp'
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

The helper runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug`. JVM tests exercise conversions in both directions, locale-aware formatting/rounding, threshold validation, inclusive normal-range boundaries, temperature crossing behavior, light-change baselines/transitions, disabled-alert suppression without replay, simultaneous temperature/light alerts, duplicate/older/stale/future/invalid readings, registration validation, and strict Firebase mapping.

Firebase Security Rules tests run locally and require Node.js and Java. They use an explicitly local `demo-darktemp` test project, replace the device UID only in the test-loaded rules, and never deploy or connect to a real project:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location .\rules-tests
npm.cmd ci
npm.cmd test
Pop-Location
```

Rules tests verify unauthenticated denial, own-UID profile access, required-field protection, device-only sensor writes, approved device paths, invalid/stale/future/regressing sample rejection, idempotent history retries, preference validation including light-alert settings, private temperature/light alert operations, preserved processed state after clearing, and concurrent alert transactions.

The missing-configuration Android smoke test is `SetupLaunchTest`. With an emulator or physical test phone connected, run it on a build without `google-services.json`:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

It verifies the setup-required screen is displayed without crashing. The test skips on configured builds. Do not remove a real configuration without preserving your local file. On this machine, no phone is attached, no Android emulator/system image is installed, and Windows Hypervisor Platform is disabled. No runtime Android/UI result is claimed from compilation alone.

Compile firmware using the exact commands and pinned dependencies in [FIRMWARE_SETUP.md](FIRMWARE_SETUP.md). The absence of `secrets.h` is a supported compile state: the template placeholders produce a setup-required boot path and disable networking. Compilation does not prove physical sensor or cloud operation.

## Manual integration checklist

These tests are **not performed against a real Firebase project or ESP32** until you configure and connect them. Use two ordinary app accounts, A and B, plus the dedicated device account. Never import demonstration readings into the live database to mark a sensor test passed.

| # | Procedure | Expected result |
|---|---|---|
| 1 | Install/run without Firebase JSON, then rebuild/install with the real configuration. | First run shows setup guidance; configured run opens without crashing. |
| 2 | Launch configured app with no active login. | Login appears with logo, email/password fields, show/hide, reset and registration links. |
| 3 | Open registration; submit blank first/last names. Fill both; move Next then Back. | Missing names are rejected; entered values persist across steps. |
| 4 | Enter invalid email, a short password, and mismatched confirmation in Step 2. | Each invalid combination is rejected with a useful message. |
| 5 | Create a valid account. Inspect `users/<new-auth-uid>`. | Profile and default preferences exist; success appears only after write confirmation; user returns signed out to Login. |
| 6 | Sign in with that account, close/reopen normally. | Main app opens, profile loads, and the login session persists. |
| 7 | Request password reset with your email. Follow the delivered Firebase link. | Firebase reset flow works; app never displays or stores the password. |
| 8 | Sign out from Settings, confirming the dialog; press Back. | Login appears; private screens/data cannot be reached via Back. |
| 9 | Set A to °F and custom limits; sign out and sign in as B. | B has its own profile/preferences/inbox; A's settings and alerts are absent. |
| 10 | Power/wire ESP32 and wait for Serial `AUTH: ready` and `UPLOAD` confirmation. | Firebase latest and dashboard match the real measured temperature, LDR, and timestamp. |
| 11 | Cover/uncover the LDR; calibrate firmware threshold from raw readings. Keep DARKTEMP open with light alerts enabled. | LIGHT/DARK changes correctly; after the initial baseline, a changed status creates one in-app light alert. No outage is inferred. |
| 12 | Select °C then °F in Settings; visit all tabs. | Dashboard/history/alerts/limits use the selected unit; Firebase sensor temperatures remain Celsius. |
| 13 | Save limits in °F, inspect Firebase, then try equal/reversed/non-numeric limits. | Valid values convert to Celsius; invalid values cannot be saved. Defaults are 18 °C and 35 °C. |
| 14 | With app open, set a limit so the next real reading is out of range. Keep it in that zone for several uploads; move limit so a new sample is normal, then cross again. | One initial alert, no repeated-zone spam, one new alert after the normal-to-outside transition. Limits changes apply to subsequent readings. |
| 15 | Observe several ESP32 sampling events, then open History. | Newest first, actual timestamps/temperatures/light values, at most latest100 samples in the app. |
| 16 | Note history keys/count, navigate tabs repeatedly and reopen app. | Navigation never creates sensor history. Only real firmware sampling events add entries. |
| 17 | Read an alert; filter Unread; mark all read; clear after confirmation; restart. | Read status persists, filters work, clearing removes only inbox records, cleared alert is not recreated from the old sample. |
| 18 | Repeat with A and B. Also run automated rules tests. | B cannot read/mark/clear A's alerts or write sensor paths. |
| 19 | Disconnect phone internet and ESP32 Wi-Fi separately, then reconnect. | Phone reports disconnected; old readings become stale after90sec; sensor retries are bounded; recovery uses actual fresh readings. No fabricated data fills gaps. |
| 20 | Use incorrect device credentials or deny sensor access in an isolated test project. Restore afterward. | Auth failures differ from database permission failures; app shows errors/retry/sign-out without crashing. |
| 21 | In Settings, toggle light-change alerts, then verify the setting persists after restarting. | The switch is account-specific and changes only light-change alerts, not temperature alerts. |
| 22 | In History, select All readings, LIGHT, then DARK. | Each filter shows only matching records from the latest 100 samples; no data is changed or deleted. |
| 23 | Change password with a wrong current password, then the correct current password and matching new password. | Wrong credentials produce an explicit error; successful change clears the form and the new password signs in. |

Additional edge cases:

- Keep the app open with an empty database. Expect “Waiting for sensor readings.” and empty History/Notifications.
- Disable alerts, cross a threshold for several real samples, then enable while still outside. No replay of the already processed crossing; return to normal and cross again to test the next alert.
- Disable light-change alerts, establish a LIGHT/DARK baseline, change status, then re-enable while the status remains there. No old transition is replayed; the next transition alerts. The first ever sample sets the baseline without an alert.
- Open the same user on two phones. A single sample/transition yields one persistent alert, even after restart or navigation.
- Clear the inbox while the latest temperature remains outside. The same old reading must not recreate an alert.
- Put the app in the background. New sensor history still comes from ESP32; this app does not generate background push alerts. On return, an eligible recent sample may be processed once.
- Temporarily deny profile creation in an isolated test project. Registration must report partial failure and sign out; restore rules, sign in, and complete the profile-recovery screen.
- In an isolated test database, introduce malformed sensor fields using the Console/Admin tooling. The app must report invalid data, not show a zero reading or crash. Ordinary users and firmware are prevented from introducing these by the deployed rules.
- Disconnect DHT11 data/power safely. Serial must report a skipped cycle, without overwriting valid temperature with zero. Restore wiring and verify recovery.
- Block network time or use a network without NTP access. Firmware must wait without uploading fake calendar timestamps.
- Interrupt connectivity after an upload may have committed. Inspect history: retrying the pending sample must retain its original unique ID.
- Test at a small phone size, landscape, large font size, TalkBack, and with the keyboard open. Check scrolling, labels, password visibility, selected tabs, dialogs, and button reachability.

## Evidence boundaries

Local emulator tests do not deploy rules to or connect to the owner's Firebase project. No live authentication, reset email delivery, live sensor reading, hardware upload, sensor calibration, or end-to-end hardware-to-cloud-to-phone result is implied by local automated checks. The supplied logos are real branding assets; test fixture values exist only in automated test code.
