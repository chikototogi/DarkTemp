#pragma once

#include <Arduino.h>

namespace DarkTemp {
constexpr char DEVICE_ID[] = "device001";
constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t LDR_PIN = 34;  // ADC1 works while Wi-Fi is active; GPIO34 is input-only.
constexpr uint32_t SAMPLE_INTERVAL_MS = 20'000;
constexpr uint32_t WIFI_RETRY_MS = 15'000;
constexpr uint32_t DIAGNOSTIC_INTERVAL_MS = 5'000;
constexpr uint32_t PENDING_MAX_AGE_MS = 90'000;
constexpr uint8_t MAX_UPLOAD_ATTEMPTS = 5;

// Divider: 3V3 -- LDR -- GPIO34 -- 10 kOhm -- GND.
// This wiring produces a higher value in brighter conditions. Calibrate locally.
constexpr int LDR_LIGHT_THRESHOLD = 2000;
constexpr bool LIGHT_WHEN_ABOVE_THRESHOLD = true;
constexpr char NTP_SERVER_1[] = "time.google.com";
constexpr char NTP_SERVER_2[] = "pool.ntp.org";
}  // namespace DarkTemp
