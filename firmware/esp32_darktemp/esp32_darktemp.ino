// DARKTEMP - ESP32 DevKit V1 / DHT11 / LDR
// Verified API target: ESP32 Arduino 3.3.12, FirebaseClient 2.2.13, DHT 1.4.7.
#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <FirebaseClient.h>
#include <DHT.h>
#include <esp_sntp.h>
#include <esp_system.h>
#include <sys/time.h>
#include <math.h>
#include "config.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#include "secrets.example.h"
#endif

using namespace DarkTemp;

DHT dht(DHT_PIN, DHT11);
WiFiClientSecure authTls;
WiFiClientSecure databaseTls;
AsyncClientClass authClient(authTls);
AsyncClientClass databaseClient(databaseTls);
UserAuth deviceAuth(FIREBASE_API_KEY, FIREBASE_DEVICE_EMAIL,
                    FIREBASE_DEVICE_PASSWORD, 3000);
FirebaseApp app;
RealtimeDatabase database;

bool configured = false;
bool authStarted = false;
bool previouslyConnected = false;
bool previouslyReady = false;
volatile bool ntpSynchronized = false;
uint32_t lastWifiAttempt = 0;
uint32_t lastSampleAt = 0;
uint32_t lastDiagnosticAt = 0;
uint64_t lastSampleTimestamp = 0;

// There is only one in-memory pending sample. Each retry uses its original key
// and payload, so an acknowledgement lost after a commit cannot duplicate it.
struct PendingReading {
  bool exists = false;
  String key;
  String payload;
  uint32_t createdAt = 0;
  uint32_t lastAttemptAt = 0;
  uint32_t retryDelayMs = 0;
  uint8_t attempts = 0;
} pending;

bool hasValue(const char *value) {
  return value != nullptr && value[0] != '\0' &&
         String(value).indexOf("REPLACE_") != 0;
}

bool configurationIsPresent() {
  return hasValue(WIFI_SSID) && hasValue(WIFI_PASSWORD) &&
         hasValue(FIREBASE_API_KEY) && hasValue(FIREBASE_DEVICE_EMAIL) &&
         hasValue(FIREBASE_DEVICE_PASSWORD) &&
         String(FIREBASE_DATABASE_URL).startsWith("https://");
}

void timeSynchronized(struct timeval *) {
  ntpSynchronized = true;
}

uint64_t epochMilliseconds() {
  if (!ntpSynchronized) return 0;
  timeval current{};
  gettimeofday(&current, nullptr);
  // Do not turn boot uptime, an unset clock, or an implausible clock into dates.
  if (current.tv_sec < 1704067200LL || current.tv_sec >= 4102444800LL) return 0;
  return static_cast<uint64_t>(current.tv_sec) * 1000ULL +
         static_cast<uint64_t>(current.tv_usec / 1000);
}

void authResult(AsyncResult &result) {
  if (!result.isResult() || !result.isError()) return;
  // Never print raw auth payloads, debug messages, passwords, or tokens.
  Serial.printf("AUTH: sign-in/token refresh failed (code %d). ",
                result.error().code());
  const String message = result.error().message();
  if (message.indexOf("INVALID_LOGIN_CREDENTIALS") >= 0 ||
      message.indexOf("INVALID_PASSWORD") >= 0 ||
      message.indexOf("EMAIL_NOT_FOUND") >= 0) {
    Serial.println("Check the dedicated device email and password.");
  } else if (message.indexOf("USER_DISABLED") >= 0) {
    Serial.println("The dedicated device account is disabled.");
  } else {
    Serial.println("Check Wi-Fi, TLS/time, API key, and Email/Password provider.");
  }
}

void maintainWifi() {
  const bool connected = WiFi.status() == WL_CONNECTED;
  if (connected != previouslyConnected) {
    previouslyConnected = connected;
    Serial.println(connected ? "WIFI: connected." : "WIFI: disconnected; retrying.");
    if (connected) {
      // Re-request network time on every reconnect. The already synchronized
      // clock continues between short outages; SNTP subsequently corrects it.
      configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);
    }
  }
  if (!connected && static_cast<uint32_t>(millis() - lastWifiAttempt) >= WIFI_RETRY_MS) {
    lastWifiAttempt = millis();
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.println("WIFI: connection attempt.");
  }
}

void startAuthenticationWhenReady() {
  if (authStarted || WiFi.status() != WL_CONNECTED || epochMilliseconds() == 0) return;
  // Certificate chains and hostname are verified with the ESP32 core's built-in
  // CA bundle. A synchronized clock is required for certificate validity checks.
  authTls.useBuiltinCACertBundle();
  databaseTls.useBuiltinCACertBundle();
  authTls.setHandshakeTimeout(10);
  databaseTls.setHandshakeTimeout(10);
  authTls.setConnectionTimeout(10'000);
  databaseTls.setConnectionTimeout(10'000);
  databaseClient.setSyncSendTimeout(10);
  databaseClient.setSyncReadTimeout(10);
  initializeApp(authClient, app, getAuth(deviceAuth), authResult, "deviceAuth");
  app.getApp<RealtimeDatabase>(database);
  database.url(FIREBASE_DATABASE_URL);
  authStarted = true;
  Serial.println("AUTH: signing in with the dedicated device account.");
}

void discardPending(const char *reason) {
  Serial.println(reason);
  pending = PendingReading{};
}

void uploadPending() {
  if (!pending.exists) return;
  const uint32_t now = millis();
  if (static_cast<uint32_t>(now - pending.createdAt) >= PENDING_MAX_AGE_MS) {
    discardPending("UPLOAD: pending sample expired; any unconfirmed sample is dropped.");
    return;
  }
  if (WiFi.status() != WL_CONNECTED || !app.ready() || epochMilliseconds() == 0) return;
  if (pending.attempts > 0 &&
      static_cast<uint32_t>(now - pending.lastAttemptAt) < pending.retryDelayMs) return;

  pending.lastAttemptAt = now;
  ++pending.attempts;
  const String path = String("/devices/") + DEVICE_ID;
  // One multi-location PATCH is atomic: either latest and this history child
  // both commit, or neither commits. No new key is created when retrying.
  const bool success = database.update(databaseClient, path, object_t(pending.payload));
  if (success) {
    Serial.printf("UPLOAD: latest and history committed (%s).\n", pending.key.c_str());
    pending = PendingReading{};
    return;
  }

  const int code = databaseClient.lastError().code();
  String message = databaseClient.lastError().message();
  message.toLowerCase();
  Serial.printf("DATABASE: upload attempt %u failed (code %d). ", pending.attempts, code);
  if (message.indexOf("permission") >= 0 || code == 403) {
    Serial.println("Check device UID rules, approved paths, and field validation.");
  } else if (code == 401) {
    Serial.println("Check device authentication and database rules; token refresh continues.");
  } else {
    Serial.println("Check connectivity/TLS and the exact Realtime Database URL.");
  }
  if (pending.attempts >= MAX_UPLOAD_ATTEMPTS) {
    discardPending("UPLOAD: retry limit reached; waiting for the next real sample.");
  } else {
    pending.retryDelayMs = 2000UL << (pending.attempts - 1);
  }
}

void sampleSensors() {
  if (pending.exists) return;  // Preserve the pending event rather than replacing it.
  const uint32_t now = millis();
  if (static_cast<uint32_t>(now - lastSampleAt) < SAMPLE_INTERVAL_MS) return;
  lastSampleAt = now;
  if (WiFi.status() != WL_CONNECTED || !authStarted || !app.ready()) {
    Serial.println("SAMPLE: waiting for Wi-Fi and Firebase authentication.");
    return;
  }
  if (epochMilliseconds() == 0) {
    Serial.println("SAMPLE: waiting for network time; no reading written.");
    return;
  }

  const float temperatureC = dht.readTemperature();
  if (!isfinite(temperatureC) || temperatureC < 0.0f || temperatureC > 50.0f) {
    Serial.println("SENSOR: DHT11 read invalid/outside 0-50 C; cycle skipped, latest retained.");
    return;
  }
  const int lightValue = analogRead(LDR_PIN);
  const bool aboveThreshold = lightValue >= LDR_LIGHT_THRESHOLD;
  const char *lightStatus = (aboveThreshold == LIGHT_WHEN_ABOVE_THRESHOLD) ? "LIGHT" : "DARK";
  const uint64_t timestamp = epochMilliseconds();
  if (timestamp == 0 || timestamp <= lastSampleTimestamp) {
    Serial.println("TIME: clock unavailable or moved backward; cycle skipped.");
    return;
  }

  char timestampText[24];
  snprintf(timestampText, sizeof(timestampText), "%llu", static_cast<unsigned long long>(timestamp));
  char key[56];
  snprintf(key, sizeof(key), "%s-%08lx%08lx", timestampText,
           static_cast<unsigned long>(esp_random()), static_cast<unsigned long>(esp_random()));
  // All string fields below come from fixed firmware constants, never user input.
  String common = String("\"deviceId\":\"") + DEVICE_ID + "\",\"lightValue\":" +
                  String(lightValue) + ",\"lightStatus\":\"" + lightStatus +
                  "\",\"temperature\":" + String(temperatureC, 1);
  pending.payload = String("{\"latest\":{") + common + ",\"lastUpdated\":" +
                    timestampText + "},\"history/" + key + "\":{" + common +
                    ",\"timestamp\":" + timestampText + "}}";
  pending.key = key;
  pending.createdAt = millis();
  pending.attempts = 0;
  pending.retryDelayMs = 0;
  pending.exists = true;
  lastSampleTimestamp = timestamp;
  Serial.printf("SAMPLE: %.1f C, LDR %d, %s, epoch ms %s.\n",
                temperatureC, lightValue, lightStatus, timestampText);
}

void setup() {
  Serial.begin(115200);
  delay(250);
  Serial.println("\nDARKTEMP: starting ambient light/temperature monitor.");
  dht.begin();
  pinMode(LDR_PIN, INPUT);
  analogReadResolution(12);  // Raw LDR values range from 0 to 4095.
  analogSetPinAttenuation(LDR_PIN, ADC_11db);
  configured = configurationIsPresent();
  if (!configured) {
    Serial.println("SETUP REQUIRED: copy secrets.example.h to secrets.h and replace all placeholders.");
    Serial.println("Networking is disabled until real configuration is supplied.");
    return;
  }
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  sntp_set_time_sync_notification_cb(timeSynchronized);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttempt = millis();
  Serial.println("WIFI: connecting. Credentials are never printed.");
}

void loop() {
  if (!configured) {
    delay(100);
    return;
  }
  maintainWifi();
  startAuthenticationWhenReady();
  // Must run continuously for token refresh and recovery after network failures.
  if (authStarted) app.loop();
  const bool ready = authStarted && app.ready();
  if (ready != previouslyReady) {
    previouslyReady = ready;
    Serial.println(ready ? "AUTH: ready." : "AUTH: awaiting sign-in/token refresh.");
  }
  if (static_cast<uint32_t>(millis() - lastDiagnosticAt) >= DIAGNOSTIC_INTERVAL_MS) {
    lastDiagnosticAt = millis();
    Serial.printf("LDR: %d (threshold %d). Time: %s.\n", analogRead(LDR_PIN),
                  LDR_LIGHT_THRESHOLD, epochMilliseconds() == 0 ? "waiting for NTP" : "synchronized");
  }
  uploadPending();
  sampleSensors();
  delay(5);
}
