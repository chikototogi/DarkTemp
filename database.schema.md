# DARKTEMP Realtime Database schema

This describes paths and types, not sample data to upload. Do not seed fabricated sensor values. The Android application ID is `com.darktemp.app`; the single device ID is `device001`.

| Path / field | Type | Meaning / writer |
| --- | --- | --- |
| `users/{firebaseAuthUid}/firstName`, `lastName` | string, 1–60 characters | Account owner; trimmed by Android |
| `users/{uid}/email` | string | Must equal the authenticated account's email |
| `users/{uid}/createdAt` | integer, epoch milliseconds | Server timestamp at registration; immutable |
| `users/{uid}/preferences/temperatureUnit` | `C` or `F` | Display preference, default `C` |
| `users/{uid}/preferences/temperatureAlertsEnabled` | boolean | Default `true` |
| `users/{uid}/preferences/lightAlertsEnabled` | boolean | Default `true`; older profiles without this field use `true` |
| `users/{uid}/preferences/minimumTemperatureC` | number | Default 18; canonical Celsius |
| `users/{uid}/preferences/maximumTemperatureC` | number | Default 35; canonical Celsius |
| `users/{uid}/alertState/lastProcessedReadingTimestamp` | integer, epoch milliseconds | Latest processed fresh sample, monotonically increasing |
| `users/{uid}/alertState/lastTemperatureZone` | `BELOW`, `NORMAL`, `ABOVE` | Last observed zone, including when alerts are disabled |
| `users/{uid}/alertState/lastLightStatus` | `LIGHT` or `DARK` | Last observed light status; absent until the first sample |
| `users/{uid}/alerts/{device001_timestamp}/type` | `TEMPERATURE_HIGH` or `TEMPERATURE_LOW` | Android-generated in-app temperature alert |
| `users/{uid}/alerts/{device001_timestamp_light}/type` | `LIGHT_CHANGED_TO_DARK` or `LIGHT_CHANGED_TO_LIGHT` | Android-generated in-app light transition alert |
| `users/{uid}/alerts/{id}/temperatureC` | number | Original temperature in Celsius |
| `users/{uid}/alerts/{id}/thresholdC` | number, temperature alerts only | Threshold in Celsius at the crossing |
| `users/{uid}/alerts/{id}/timestamp` | integer, epoch milliseconds | Original sensor sample time |
| `users/{uid}/alerts/{id}/isRead` | boolean | Initially `false` |
| `devices/device001/latest/deviceId` | string | Exactly `device001` |
| `devices/device001/latest/lightValue` | integer, 0–4095 | Raw ESP32 ADC value |
| `devices/device001/latest/lightStatus` | `LIGHT` or `DARK` | Firmware classification |
| `devices/device001/latest/temperature` | number, 0–50 | DHT11 ambient temperature in Celsius |
| `devices/device001/latest/lastUpdated` | integer, epoch milliseconds | NTP-synchronized sampling timestamp |
| `devices/device001/history/{uniqueSampleId}` | object | Same five fields as latest, with `timestamp` replacing `lastUpdated` |

The FRS names `lightValue`, `lightStatus`, `temperature`, and `lastUpdated` are preserved in `latest`. Kotlin maps `temperature` to the descriptive `temperatureC` property without changing cloud field names. History is ordered by `timestamp`, indexed in the rules, and Android initially listens to the latest 100 records in descending order. Each firmware sampling cycle uses one unique history ID. Retrying an uncertain upload reuses that ID and payload; the rules allow an identical retry and reject modifications or deletions.

Preferences require `minimumTemperatureC < maximumTemperatureC`, both within −40 to 125 °C. These are editable alarm limits, not claims about the DHT11 measurement range. Fahrenheit entry is converted to Celsius before saving. No formatted alert message is persisted: Android formats the original value and threshold in the user's currently selected unit.

All database timestamps are Unix epoch **milliseconds**, never seconds or human-readable date strings. Firmware skips uploads until network time is valid and skips a failed sensor sample. Rules reject timestamps before 2020, more than five seconds in the future, and sensor uploads older than 120 seconds. Latest timestamps cannot move backwards. Android marks readings stale after 90 seconds; this configurable limit is `MonitoringConfig.FRESHNESS_TIMEOUT_MS`. A future timestamp beyond five seconds is invalid for freshness and alert processing. Phones should use automatic date and time.

## Alert transition behavior

The application processes readings only while actively monitoring. The first fresh unprocessed reading outside the limits creates one temperature alert. Later readings in that same outside zone do not create more alerts. Normal-range boundaries are inclusive. Returning to normal and crossing again creates another alert; crossing directly from high to low (or low to high) also creates an alert. The first valid light sample establishes a baseline without creating a light alert; later changes between `LIGHT` and `DARK` create an alert when enabled. Both alert preferences default to enabled. Disabled alert types still advance the timestamp, temperature zone, and light status without creating alerts. Re-enabling does not replay the same sample or an already observed condition. Edited limits take effect on the next newer reading; changing settings does not reprocess the previous sample.

`MonitoringRepository.processReading` runs a transaction at `users/{uid}`. It re-reads preferences and persisted state on every transaction retry, and commits the next state and deterministic alert IDs together. Temperature and light alerts from the same reading use distinct IDs. This prevents two active sessions or a restart from generating duplicates. Missing, invalid, stale, future, duplicate, or older readings do not advance state. Clearing alerts leaves `alertState` intact. Cancellation and UID checks stop later transaction retries after the UI stops monitoring or the account changes; an already accepted server commit cannot be recalled.

## Access and scale

Root access is denied. Ordinary authenticated users can read the shared latest/history paths, write no sensor records, and read/write only their own profile/preferences/alerts. The dedicated device UID is expressly excluded from all `users` access and can write only the two sensor paths. Unknown fields are rejected. Creating another Auth account does not grant device privileges. The Web API key does not grant database access.

Client-generated alerts are the account owner's private convenience records, not trusted audit evidence: an owner can edit their own permitted records through another authenticated client. Sensor data remains device-write-only. The all/unread inbox reads all alerts, and its parent transaction reads the account's inbox. History filters the latest 100 samples by `LIGHT` or `DARK` on-device; filtering does not alter or delete records. This is intentionally suitable for a small capstone demonstration; clear unneeded alerts and monitor usage. Sensor history is retained until the project owner archives/deletes it through administrative tooling; the app's 100-record query does not delete history. No background push notifications, cloud functions, or server are used.
