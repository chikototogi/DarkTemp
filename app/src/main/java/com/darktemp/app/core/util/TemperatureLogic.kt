package com.darktemp.app.core.util

import com.darktemp.app.core.config.MonitoringConfig
import com.darktemp.app.data.model.*
import java.util.Locale

fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32.0) * 5.0 / 9.0
fun displayTemperature(celsius: Double, unit: TemperatureUnit): String {
    if (!celsius.isFinite()) return "—"
    val value = if (unit == TemperatureUnit.C) celsius else celsiusToFahrenheit(celsius)
    // Avoid the confusing '-0.0' display after conversion near zero.
    return String.format(Locale.getDefault(), "%.1f °%s", if (kotlin.math.abs(value) < 0.05) 0.0 else value, unit.name)
}

fun validateThresholds(minC: Double, maxC: Double): String? = when {
    !minC.isFinite() || !maxC.isFinite() -> "Enter valid minimum and maximum temperatures."
    minC < -40.0 || maxC > 125.0 -> "Limits must be between -40 °C and 125 °C."
    minC >= maxC -> "Minimum temperature must be lower than maximum temperature."
    else -> null
}

fun temperatureZone(celsius: Double, preferences: Preferences): TemperatureZone {
    require(celsius.isFinite()) { "Temperature must be finite." }
    require(validateThresholds(preferences.minC, preferences.maxC) == null)
    return when {
        celsius < preferences.minC -> TemperatureZone.BELOW
        celsius > preferences.maxC -> TemperatureZone.ABOVE
        else -> TemperatureZone.NORMAL
    }
}

fun isFreshReading(reading: SensorReading, nowMs: Long): Boolean =
    reading.timestamp >= MonitoringConfig.EARLIEST_TIMESTAMP_MS &&
        reading.timestamp <= nowMs + MonitoringConfig.FUTURE_TOLERANCE_MS &&
        nowMs - reading.timestamp <= MonitoringConfig.FRESHNESS_TIMEOUT_MS

fun isValidReading(reading: SensorReading): Boolean =
    reading.deviceId == MonitoringConfig.DEVICE_ID && reading.lightValue in 0..4095 &&
        reading.lightStatus in setOf("LIGHT", "DARK") && reading.temperatureC.isFinite() &&
        reading.temperatureC in 0.0..50.0 && reading.timestamp >= MonitoringConfig.EARLIEST_TIMESTAMP_MS

/** First unprocessed out-of-range sample alerts once. Only newer, fresh samples advance state.
 * Disabled alerts still advance the zone, so enabling alerts does not replay an old crossing.
 * The repository commits this decision and its alert in one retryable Firebase transaction. */
fun decideAlert(reading: SensorReading, preferences: Preferences, previous: AlertState, nowMs: Long): AlertDecision? {
    if (!isValidReading(reading) || !isFreshReading(reading, nowMs) ||
        reading.timestamp <= previous.lastTimestamp || validateThresholds(preferences.minC, preferences.maxC) != null) return null
    val zone = temperatureZone(reading.temperatureC, preferences)
    val changed = previous.lastTimestamp == 0L || zone != previous.zone
    val alert = if (preferences.alertsEnabled && changed && zone != TemperatureZone.NORMAL) {
        SensorAlert(
            id = "${reading.deviceId}_${reading.timestamp}",
            type = if (zone == TemperatureZone.ABOVE) AlertType.TEMPERATURE_HIGH else AlertType.TEMPERATURE_LOW,
            temperatureC = reading.temperatureC,
            thresholdC = if (zone == TemperatureZone.ABOVE) preferences.maxC else preferences.minC,
            timestamp = reading.timestamp,
            isRead = false,
        )
    } else null
    val lightChanged = previous.lastLightStatus != null && previous.lastLightStatus != reading.lightStatus
    val lightAlert = if (preferences.lightAlertsEnabled && lightChanged) {
        SensorAlert(
            id = "${reading.deviceId}_${reading.timestamp}_light",
            type = if (reading.lightStatus == "DARK") AlertType.LIGHT_CHANGED_TO_DARK else AlertType.LIGHT_CHANGED_TO_LIGHT,
            temperatureC = reading.temperatureC,
            thresholdC = null,
            timestamp = reading.timestamp,
            isRead = false,
        )
    } else null
    return AlertDecision(AlertState(reading.timestamp, zone, reading.lightStatus), alert, lightAlert)
}
