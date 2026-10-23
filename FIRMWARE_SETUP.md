# DARKTEMP firmware setup

The sketch measures ambient room temperature and light using an ESP32 DevKit V1, DHT11, and LDR. It uploads real readings to `devices/device001/latest` and `devices/device001/history`. It does not control appliances or detect power outages.

## 1. Install the board and libraries

Use Arduino IDE 2, or Arduino CLI. The reproducible dependency versions are:

| Component | Version |
| --- | --- |
| ESP32 boards by Espressif Systems | 3.3.12 |
| FirebaseClient by Mobizt | 2.2.13 |
| ESP_SSLClient, FirebaseClient dependency | 3.1.3 |
| DHT sensor library by Adafruit | 1.4.7 |
| Adafruit Unified Sensor | 1.1.15 |

Wi-Fi, SNTP, ADC, and the secure TLS client come from the ESP32 board package. Select **DOIT ESP32 DEVKIT V1**, corresponding to FQBN `esp32:esp32:esp32doit-devkit-v1`. Do not install the retired `Firebase-ESP-Client` as a replacement for `FirebaseClient`.

In Arduino IDE, add this URL in **File > Preferences > Additional boards manager URLs**:

```text
https://espressif.github.io/arduino-esp32/package_esp32_index.json
```

Install the exact board version in Boards Manager. Install the exact libraries in Library Manager, including their dependencies. If using Arduino CLI on your PATH, run these PowerShell commands from the repository root:

```powershell
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.3.12 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli lib install "ESP_SSLClient@3.1.3" "FirebaseClient@2.2.13" "DHT sensor library@1.4.7" "Adafruit Unified Sensor@1.1.15"
```

The maintained library's [UserAuth/update example](https://github.com/mobizt/FirebaseClient/blob/v2.2.13/examples/RealtimeDatabase/Update/Update.ino) was used to verify authentication and update APIs. DARKTEMP uses `initializeApp`, `FirebaseApp.loop`, `app.ready`, and `RealtimeDatabase.update`; it deliberately configures certificate validation rather than copying an example's insecure TLS setup. The ESP32 [secure client implementation](https://github.com/espressif/arduino-esp32/blob/3.3.12/libraries/NetworkClientSecure/src/NetworkClientSecure.cpp) provides `useBuiltinCACertBundle()`.

## 2. Wire the sensors

Disconnect the board's power while changing wiring. All sensor grounds must connect to ESP32 GND.

| Connection | ESP32 pin |
| --- | --- |
| DHT11 data | GPIO 4 |
| LDR divider midpoint | GPIO 34 |
| Compatible sensor module VCC | 3V3 |
| Sensor grounds | GND |

LDR divider:

```text
3V3 ---- LDR ----+---- 10 kOhm resistor ---- GND
                |
              GPIO34
```

This orientation normally produces larger readings as the light gets brighter. GPIO34 is an input-only ADC1 pin, so it can sample while Wi-Fi is running. It has no internal pull-up/down; the external divider resistor is required. Keep the divider powered from **3.3 V**; never connect a 5 V analog output to the ESP32. The 12-bit raw reading is 0-4095 and is not a calibrated lux measurement.

For a bare four-pin DHT11, check the exact part's pinout; typically, facing its grille, pins are VCC, DATA, not connected, GND. Fit a 4.7-10 kOhm pull-up between DATA and 3V3. Most three-pin DHT11 modules already contain this resistor, but their pin order varies: use the module labels. Confirm the module supports 3.3 V operation. A module that requires 5 V supply needs suitable signal-level handling; its data pull-up must never pull GPIO4 to 5 V. Keep wires short. See Adafruit's [DHT wiring guide](https://learn.adafruit.com/dht/connecting-to-a-dhtxx-sensor). DHT11 readings are only accepted within its [0-50 C temperature range](https://learn.adafruit.com/dht/overview).

## 3. Configure Firebase and local credentials

Follow [FIREBASE_SETUP.md](FIREBASE_SETUP.md) first:

1. Enable Firebase Authentication **Email/Password**.
2. Create a separate dedicated device account in Authentication. Keep the UID for database rules; keep its email and password for the firmware.
3. Replace the device UID placeholder in `database.rules.json` with that account's real UID and publish the rules to your Realtime Database.
4. Copy the Web API key from Firebase Project settings and the complete real HTTPS Realtime Database URL from its Console.

Create the excluded local file:

```powershell
Copy-Item -LiteralPath .\firmware\esp32_darktemp\secrets.example.h -Destination .\firmware\esp32_darktemp\secrets.h
notepad .\firmware\esp32_darktemp\secrets.h
```

Replace all six values: `WIFI_SSID`, `WIFI_PASSWORD`, `FIREBASE_API_KEY`, `FIREBASE_DATABASE_URL`, `FIREBASE_DEVICE_EMAIL`, and `FIREBASE_DEVICE_PASSWORD`. Use a 2.4 GHz Wi-Fi network that the ESP32 can access; a captive portal will not work without additional provisioning. Enter C++ string literals correctly (escape a literal quote as `\"` and a backslash as `\\`). The URL must begin with `https://`, identify the actual database, and contain no `.json` path or token. Copy it from Console rather than constructing a project URL from memory.

`secrets.h` is ignored. Do not commit or share it, exported binaries containing it, auth tokens, or build artifacts. The Web API key identifies your Firebase project; database access still requires authentication and rules authorization. Never use an Admin SDK/service-account private key. Microcontroller firmware credentials can be extracted; the dedicated, narrowly authorized account is a student-prototype identity, not a production hardware identity mechanism. Rotate its password if disclosed.

The source also compiles without `secrets.h`: it includes the template, prints `SETUP REQUIRED`, and disables networking. That build does not authenticate or upload data.

## 4. Compile, upload, and inspect Serial Monitor

Open `firmware/esp32_darktemp/esp32_darktemp.ino` in Arduino IDE. Select the board and the COM port that appears when you connect the ESP32 using a data-capable USB cable. Use **Verify**, then **Upload**. Start Serial Monitor at **115200 baud** and reset the board.

Equivalent CLI commands:

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32doit-devkit-v1 .\firmware\esp32_darktemp
arduino-cli board list
$devicePort = Read-Host 'Enter the ESP32 port shown by board list (COM followed by a number)'
arduino-cli upload --port $devicePort --fqbn esp32:esp32:esp32doit-devkit-v1 .\firmware\esp32_darktemp
arduino-cli monitor --port $devicePort --config baudrate=115200
```

Some clone boards need a CP210x or CH340 USB serial driver from the board/chip manufacturer. If upload remains at `Connecting`, hold BOOT until upload starts, then release it. Do not keep Serial Monitor open while another program uploads to the same port.

Expected progress is `WIFI: connected`, synchronized time, `AUTH: ready`, real `SAMPLE` values, then `UPLOAD: latest and history committed`. The firmware prints no passwords or auth tokens. No such progress is claimed until the board actually reports it.

## 5. Calibrate light classification

With real credentials configured, the sketch prints a raw `LDR` value every five seconds, even while it is waiting for Firebase. Record several values with the room lit and several with the LDR covered. Set `LDR_LIGHT_THRESHOLD` in `config.h` to a value between those observed groups, then upload again. The initial `2000` is only a calibration starting point.

The documented divider uses `LIGHT_WHEN_ABOVE_THRESHOLD = true`. If the divider is intentionally reversed, set this to `false`. A value exactly at the threshold counts as above. Verify both conditions in Serial Monitor and the app. A DARK value means low ambient light only.

## 6. Sampling, history, time, and recovery behavior

- `SAMPLE_INTERVAL_MS` is 20,000 (20 seconds). Keep it around 15-30 seconds. A successful cycle requires Wi-Fi, Firebase authentication, synchronized time, and a valid DHT11 read.
- Temperature remains Celsius. Latest contains `deviceId`, `lightValue`, `lightStatus`, `temperature`, and numeric `lastUpdated`. Each historical record has the same first four fields plus numeric `timestamp` instead of `lastUpdated`.
- Both timestamps are **Unix epoch milliseconds**, describing the same sampling event. SNTP uses `time.google.com` and `pool.ntp.org` with UTC; Android formats times for its local timezone. Authentication starts only after the first successful SNTP callback and a plausible clock. No uptime is passed off as a date.
- One Firebase multi-location update at `/devices/device001` commits `latest` and `history/<uniqueId>` atomically. The key combines sample epoch milliseconds and 64 random bits. Retrying sends exactly the original key and payload, so a lost acknowledgement does not create another history entry. Rules allow an identical history rewrite, but reject changing an existing event.
- One pending event is held in RAM. Failed uploads retry up to five times, with delays of 2, 4, 8, and 16 seconds. No new sensor event replaces a pending event. It is discarded after 90 seconds or five failed attempts; there is no unlimited backlog. A server may already have committed a write whose response was lost, so diagnostics call it unconfirmed rather than asserting data was lost.
- During outages, sample cycles are skipped and the last valid database reading is retained. RAM pending data is lost on reset. History may therefore have gaps. On reconnect, the firmware requests time again, the Firebase library refreshes/signs in as needed, and new sampling resumes. It does not fill gaps with fabricated records.
- A failed, non-finite, or out-of-range DHT11 read skips the entire cycle. It never writes zero as a substitute. Inspect VCC, GND, GPIO4, pull-up, and wire length.
- If time is not synchronized, no data is written. The synchronized clock continues running between short outages. If a time correction moves the clock behind the last sample, sampling pauses until it catches up, preserving ordering.
- TLS verifies hostnames and certificate chains using the ESP32 3.3.12 built-in CA bundle. Wi-Fi must allow DNS, HTTPS (443), and NTP (UDP 123). Keep the ESP32 core's CA bundle current when certificate roots change; never fix a TLS error by disabling verification. TLS/connect and synchronous read/write timeouts are bounded; brief blocking during an upload can lengthen the effective sampling interval.

## 7. Hardware-to-cloud test

No physical sensor or real Firebase credentials are needed for a compile check. They are required for the following integration test:

1. Upload the configured firmware, open Serial Monitor, and confirm Wi-Fi, time, and auth readiness.
2. Wait for `UPLOAD: latest and history committed`. In Firebase Console, confirm one valid latest object and a new matching history child. Verify timestamps are current numeric milliseconds.
3. Wait for the next cycle and verify one additional history child, with latest pointing to the newer values. Restart/navigate the Android app and ensure it does not create history.
4. Cover/uncover the LDR. Confirm Serial Monitor, database, and app agree on DARK/LIGHT after calibration.
5. Compare ambient temperature with a reference thermometer within the DHT11's expected accuracy. Test app alert limits by moving the configured bounds around the actual room reading; do not heat the board or introduce synthetic readings.
6. Turn off the Wi-Fi access point briefly, then restore it. Confirm the app becomes stale as configured, firmware reconnects, and real history resumes with an honest gap. Check that an upload retry keeps its original history key.
7. Disconnect DHT11 DATA temporarily. Confirm `SENSOR` failures retain latest and create no bogus history. Restore the connection and confirm recovery.
8. Start the board with NTP blocked. Confirm it stays at waiting for NTP and writes nothing; unblock it and confirm recovery.
9. Test a wrong device password locally: expect an `AUTH` failure and no upload. Restore it. Separately test denied device rules: auth can be ready, but `DATABASE` permission errors occur. Restore the correct published rules immediately.

## Troubleshooting

| Diagnostic | Check |
| --- | --- |
| `SETUP REQUIRED` | Local `secrets.h` location, all placeholders replaced, HTTPS URL |
| Repeated Wi-Fi attempts | 2.4 GHz SSID/password, range, captive portal, router access |
| `waiting for NTP` | DNS and UDP 123 access; internet connection |
| `AUTH` error | Device email/password, disabled/deleted account, Email/Password provider, Web API key, TLS/time |
| Auth ready but `DATABASE` permission error | Actual device UID in published rules, `device001`, exact field contract, server clock tolerance |
| Database/TLS or connection error | Real database URL and region, internet/443, trusted CA bundle and clock |
| `SENSOR` error | DHT11 wiring, pull-up, supply compatibility, loose connections |
| LDR pinned at 0 or 4095 | Divider continuity, GPIO34, common ground; calibrate before changing code |
| Compile cannot find `useBuiltinCACertBundle` | Install selected ESP32 core 3.3.12 rather than an older incompatible core |

For the actual compile verification result and the limits of testing performed in this workspace, see [TESTING.md](TESTING.md).
