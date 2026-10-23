package com.darktemp.app.data.model

enum class TemperatureUnit { C, F }
enum class AlertType {
    TEMPERATURE_HIGH,
    TEMPERATURE_LOW,
    LIGHT_CHANGED_TO_DARK,
    LIGHT_CHANGED_TO_LIGHT,
}
enum class TemperatureZone { BELOW, NORMAL, ABOVE }

data class Profile(val firstName: String, val lastName: String, val email: String, val createdAt: Long)

data class Preferences(
    val unit: TemperatureUnit = TemperatureUnit.C,
    val alertsEnabled: Boolean = true,
    val minC: Double = 18.0,
    val maxC: Double = 35.0,
    val lightAlertsEnabled: Boolean = true,
)

data class SensorReading(
    val deviceId: String,
    val lightValue: Int,
    val lightStatus: String,
    val temperatureC: Double,
    val timestamp: Long,
)

data class SensorAlert(
    val id: String,
    val type: AlertType,
    val temperatureC: Double,
    val thresholdC: Double?,
    val timestamp: Long,
    val isRead: Boolean,
)

data class AlertState(
    val lastTimestamp: Long = 0,
    val zone: TemperatureZone = TemperatureZone.NORMAL,
    val lastLightStatus: String? = null,
)
data class AlertDecision(val state: AlertState, val alert: SensorAlert?, val lightAlert: SensorAlert? = null)

sealed interface AuthSession {
    data object SignedOut : AuthSession
    data class SignedIn(val uid: String, val email: String) : AuthSession
}

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Ready<T>(val value: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}
