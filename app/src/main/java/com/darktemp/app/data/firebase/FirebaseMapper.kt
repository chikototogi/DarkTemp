package com.darktemp.app.data.firebase

import com.darktemp.app.core.util.isValidReading
import com.darktemp.app.core.util.validateThresholds
import com.darktemp.app.data.model.*

/** Map explicitly: malformed cloud values must never silently become plausible zero readings. */
object FirebaseMapper {
    fun sensor(value: Any?, history: Boolean = false): SensorReading {
        val map = value.asMap("sensor reading")
        return SensorReading(
            deviceId = map.string("deviceId"),
            lightValue = map.integer("lightValue").also { require(it in 0..4095) }.toInt(),
            lightStatus = map.string("lightStatus"),
            temperatureC = map.number("temperature"),
            timestamp = map.integer(if (history) "timestamp" else "lastUpdated"),
        ).also { require(isValidReading(it)) { "Sensor data is malformed. Check the device fields and timestamp." } }
    }

    fun profile(value: Any?): Profile? {
        if (value == null) return null
        // Missing/malformed profile or preferences opens the explicit profile recovery screen.
        return runCatching {
            val map = value.asMap("profile")
            preferences(map["preferences"])
            Profile(map.string("firstName"), map.string("lastName"), map.string("email"), map.integer("createdAt"))
                .also { require(it.firstName.isNotBlank() && it.firstName.length <= 60 && it.lastName.isNotBlank() && it.lastName.length <= 60 && it.email.isNotBlank() && it.createdAt > 0) }
        }.getOrNull()
    }

    fun preferences(value: Any?): Preferences {
        require(value != null) { "Account settings are missing. Complete your profile to recover them." }
        val map = value.asMap("preferences")
        return Preferences(
            TemperatureUnit.valueOf(map.string("temperatureUnit")),
            map.boolean("temperatureAlertsEnabled"),
            map.number("minimumTemperatureC"),
            map.number("maximumTemperatureC"),
            if ("lightAlertsEnabled" in map) map.boolean("lightAlertsEnabled") else true,
        ).also { require(validateThresholds(it.minC, it.maxC) == null) { "Stored temperature limits are invalid." } }
    }

    fun alertState(value: Any?): AlertState {
        if (value == null) return AlertState()
        val map = value.asMap("alert state")
        val lightStatus = map["lastLightStatus"]
        require(lightStatus == null || lightStatus is String && lightStatus in setOf("LIGHT", "DARK")) {
            "Stored light status is invalid."
        }
        return AlertState(
            map.integer("lastProcessedReadingTimestamp"),
            TemperatureZone.valueOf(map.string("lastTemperatureZone")),
            lightStatus as? String,
        )
    }

    fun alert(id: String, value: Any?): SensorAlert {
        val map = value.asMap("alert")
        val type = AlertType.valueOf(map.string("type"))
        val threshold = if (type == AlertType.TEMPERATURE_HIGH || type == AlertType.TEMPERATURE_LOW) map.number("thresholdC") else null
        return SensorAlert(id, type, map.number("temperatureC"), threshold, map.integer("timestamp"), map.boolean("isRead"))
    }

    fun preferencesMap(value: Preferences): Map<String, Any> = mapOf(
        "temperatureUnit" to value.unit.name,
        "temperatureAlertsEnabled" to value.alertsEnabled,
        "minimumTemperatureC" to value.minC,
        "maximumTemperatureC" to value.maxC,
        "lightAlertsEnabled" to value.lightAlertsEnabled,
    )

    fun alertMap(value: SensorAlert): Map<String, Any> = buildMap {
        put("type", value.type.name)
        put("temperatureC", value.temperatureC)
        value.thresholdC?.let { put("thresholdC", it) }
        put("timestamp", value.timestamp)
        put("isRead", value.isRead)
    }

    private fun Any?.asMap(label: String): Map<*, *> = this as? Map<*, *> ?: error("Invalid $label data.")
    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing or invalid $key.")
    private fun Map<*, *>.boolean(key: String): Boolean = this[key] as? Boolean ?: error("Missing or invalid $key.")
    private fun Map<*, *>.number(key: String): Double = (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: error("Missing or invalid $key.")
    private fun Map<*, *>.integer(key: String): Long {
        val value = number(key)
        require(value >= 0 && value <= 9_007_199_254_740_991.0 && value % 1.0 == 0.0) { "Invalid integer $key." }
        return value.toLong()
    }
}
