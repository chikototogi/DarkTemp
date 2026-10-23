package com.darktemp.app.core

import com.darktemp.app.data.firebase.FirebaseMapper
import com.darktemp.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FirebaseMapperTest {
    private val timestamp = 1_800_000_000_000L
    private val sample = mapOf("deviceId" to "device001", "lightValue" to 1800L, "lightStatus" to "DARK", "temperature" to 25.0, "lastUpdated" to timestamp)
    @Test fun mapsActualFrsLatestAndHistoryFieldNames() {
        val reading = FirebaseMapper.sensor(sample)
        assertEquals(25.0, reading.temperatureC, 0.0)
        assertEquals(timestamp, reading.timestamp)
        assertEquals("DARK", reading.lightStatus)
        assertEquals(reading, FirebaseMapper.sensor(sample - "lastUpdated" + ("timestamp" to timestamp), history = true))
    }
    @Test fun rejectsMissingMalformedAndNonFiniteSensorFields() {
        for (invalid in listOf(
            sample - "temperature", sample + ("temperature" to "25"), sample + ("temperature" to Double.NaN),
            sample + ("lastUpdated" to 0L), sample + ("lastUpdated" to timestamp + 0.5),
            sample + ("lightValue" to 1.1), sample + ("lightStatus" to "OFFLINE"), sample - "deviceId",
        )) assertTrue("Should reject $invalid", runCatching { FirebaseMapper.sensor(invalid) }.isFailure)
    }
    @Test fun incompleteAccountsExposeRecoveryRatherThanInventedNames() {
        assertNull(FirebaseMapper.profile(null))
        assertNull(FirebaseMapper.profile(mapOf("email" to "a@example.test")))
        val fields = mapOf("firstName" to "Test", "lastName" to "User", "email" to "a@example.test", "createdAt" to timestamp)
        assertNull(FirebaseMapper.profile(fields))
        assertNull(FirebaseMapper.profile(fields + ("preferences" to mapOf("temperatureUnit" to "K"))))
        val complete = fields + ("preferences" to FirebaseMapper.preferencesMap(Preferences()))
        assertEquals("Test", FirebaseMapper.profile(complete)!!.firstName)
        assertNull(FirebaseMapper.profile(complete + ("firstName" to 42)))
        assertNull(FirebaseMapper.profile(complete + ("lastName" to " ")))
    }
    @Test fun preferencesRequireRealCompleteData() {
        assertTrue(runCatching { FirebaseMapper.preferences(null) }.isFailure)
        assertEquals(Preferences(), FirebaseMapper.preferences(FirebaseMapper.preferencesMap(Preferences())))
        assertEquals(true, FirebaseMapper.preferences(
            FirebaseMapper.preferencesMap(Preferences()) - "lightAlertsEnabled"
        ).lightAlertsEnabled)
        assertTrue(runCatching {
            FirebaseMapper.preferences(FirebaseMapper.preferencesMap(Preferences()) + ("lightAlertsEnabled" to "yes"))
        }.isFailure)
    }
    @Test fun mapsLightAlertsAndBackwardCompatibleAlertState() {
        val alert = SensorAlert(
            "device001_${timestamp}_light",
            AlertType.LIGHT_CHANGED_TO_DARK,
            25.0,
            null,
            timestamp,
            false,
        )
        val mapped = FirebaseMapper.alertMap(alert)
        assertFalse(mapped.containsKey("thresholdC"))
        assertEquals(alert, FirebaseMapper.alert(alert.id, mapped))
        assertEquals(AlertState(), FirebaseMapper.alertState(null))
        assertEquals(
            AlertState(timestamp, TemperatureZone.NORMAL, "DARK"),
            FirebaseMapper.alertState(mapOf(
                "lastProcessedReadingTimestamp" to timestamp,
                "lastTemperatureZone" to "NORMAL",
                "lastLightStatus" to "DARK",
            )),
        )
        assertTrue(runCatching {
            FirebaseMapper.alertState(mapOf(
                "lastProcessedReadingTimestamp" to timestamp,
                "lastTemperatureZone" to "NORMAL",
                "lastLightStatus" to "UNKNOWN",
            ))
        }.isFailure)
    }
}
