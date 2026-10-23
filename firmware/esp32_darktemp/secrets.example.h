#pragma once

// Copy to secrets.h beside the sketch. The real file is excluded from Git.
// These placeholders intentionally disable networking until replaced.
#define WIFI_SSID "REPLACE_WIFI_SSID"
#define WIFI_PASSWORD "REPLACE_WIFI_PASSWORD"
#define FIREBASE_API_KEY "REPLACE_FIREBASE_WEB_API_KEY"
#define FIREBASE_DATABASE_URL "REPLACE_REAL_HTTPS_DATABASE_URL"
#define FIREBASE_DEVICE_EMAIL "REPLACE_DEDICATED_DEVICE_EMAIL"
#define FIREBASE_DEVICE_PASSWORD "REPLACE_DEDICATED_DEVICE_PASSWORD"

// Use the Web API key from Firebase Project settings, NOT an Admin SDK key.
// Database URL: copy the complete https:// URL from Realtime Database Console.
// Create this dedicated device account in Firebase Authentication. Its UID must
// replace the device UID placeholder in database.rules.json before publishing.
