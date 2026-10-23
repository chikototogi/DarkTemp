package com.darktemp.app.core

import com.darktemp.app.core.config.MonitoringConfig
import com.darktemp.app.core.util.*
import com.darktemp.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TemperatureLogicTest {
    private val now = 1_800_000_000_000L
    private fun reading(c: Double = 25.0, timestamp: Long = now) = SensorReading("device001", 1800, "LIGHT", c, timestamp)

    @Test fun convertsCelsiusToFahrenheit() {
        assertEquals(32.0, celsiusToFahrenheit(0.0), 0.00001)
        assertEquals(77.0, celsiusToFahrenheit(25.0), 0.00001)
        assertEquals(212.0, celsiusToFahrenheit(100.0), 0.00001)
    }

    @Test fun convertsFahrenheitToCelsius() {
        assertEquals(0.0, fahrenheitToCelsius(32.0), 0.00001)
        assertEquals(25.0, fahrenheitToCelsius(77.0), 0.00001)
        assertEquals(100.0, fahrenheitToCelsius(212.0), 0.00001)
        assertEquals(-40.0, fahrenheitToCelsius(-40.0), 0.00001)
    }

    @Test fun formatsAndRoundsInSelectedUnit() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("25.1 °C", displayTemperature(25.06, TemperatureUnit.C))
            assertEquals("77.0 °F", displayTemperature(25.0, TemperatureUnit.F))
            assertEquals("0.0 °C", displayTemperature(-0.001, TemperatureUnit.C))
            assertEquals("—", displayTemperature(Double.NaN, TemperatureUnit.C))
        } finally { Locale.setDefault(old) }
    }

    @Test fun validatesRangesIncludingNonFiniteNumbers() {
        assertNull(validateThresholds(18.0, 35.0))
        assertNull(validateThresholds(fahrenheitToCelsius(64.4), fahrenheitToCelsius(95.0)))
        assertNotNull(validateThresholds(35.0, 18.0))
        assertNotNull(validateThresholds(18.0, 18.0))
        assertNotNull(validateThresholds(Double.NaN, 35.0))
        assertNotNull(validateThresholds(18.0, Double.POSITIVE_INFINITY))
        assertNotNull(validateThresholds(-41.0, 35.0))
        assertNotNull(validateThresholds(18.0, 126.0))
    }

    @Test fun classifiesZonesWithInclusiveNormalBoundaries() {
        val p = Preferences()
        assertEquals(TemperatureZone.BELOW, temperatureZone(17.9, p))
        assertEquals(TemperatureZone.NORMAL, temperatureZone(18.0, p))
        assertEquals(TemperatureZone.NORMAL, temperatureZone(35.0, p))
        assertEquals(TemperatureZone.ABOVE, temperatureZone(35.1, p))
    }

    @Test fun firstOutsideSampleAlertsOnceAndDuplicatesAreIgnored() {
        val first = decideAlert(reading(36.0), Preferences(), AlertState(), now)!!
        assertEquals(AlertType.TEMPERATURE_HIGH, first.alert!!.type)
        assertEquals(35.0, first.alert!!.thresholdC!!, 0.0)
        assertEquals("device001_$now", first.alert.id)
        assertNull(decideAlert(reading(36.0), Preferences(), first.state, now))
        assertNull(decideAlert(reading(17.0, now - 1), Preferences(), first.state, now))
        val sustained = decideAlert(reading(38.0, now + 1), Preferences(), first.state, now + 1)!!
        assertNull(sustained.alert)
    }

    @Test fun crossingNormalAllowsAnotherAlertAndHighToLowAlerts() {
        val high = decideAlert(reading(36.0), Preferences(), AlertState(), now)!!
        val normal = decideAlert(reading(25.0, now + 1), Preferences(), high.state, now + 1)!!
        assertNull(normal.alert)
        val highAgain = decideAlert(reading(36.0, now + 2), Preferences(), normal.state, now + 2)!!
        assertNotNull(highAgain.alert)
        val low = decideAlert(reading(17.0, now + 3), Preferences(), highAgain.state, now + 3)!!
        assertEquals(AlertType.TEMPERATURE_LOW, low.alert!!.type)
        assertEquals(18.0, low.alert!!.thresholdC!!, 0.0)
    }

    @Test fun disabledAlertsStillAdvanceStateAndDoNotReplay() {
        val disabled = decideAlert(reading(36.0), Preferences(alertsEnabled = false), AlertState(), now)!!
        assertNull(disabled.alert)
        assertEquals(now, disabled.state.lastTimestamp)
        assertNull(decideAlert(reading(36.0), Preferences(), disabled.state, now))
        assertNull(decideAlert(reading(36.0, now + 1), Preferences(), disabled.state, now + 1)!!.alert)
    }

    @Test fun staleFutureInvalidAndMissingTimestampsNeverAdvanceState() {
        for (invalid in listOf(
            reading(timestamp = now - MonitoringConfig.FRESHNESS_TIMEOUT_MS - 1),
            reading(timestamp = now + MonitoringConfig.FUTURE_TOLERANCE_MS + 1),
            reading(timestamp = 0), reading(Double.NaN), reading(51.0),
            reading().copy(lightStatus = "UNKNOWN"), reading().copy(deviceId = "device002"),
            reading().copy(lightValue = -1),
        )) assertNull(decideAlert(invalid, Preferences(), AlertState(), now))
        assertTrue(isFreshReading(reading(timestamp = now - MonitoringConfig.FRESHNESS_TIMEOUT_MS), now))
        assertTrue(isFreshReading(reading(timestamp = now + MonitoringConfig.FUTURE_TOLERANCE_MS), now))
    }

    @Test fun preferenceChangeUsesNextSampleAndPersistedTimestamp() {
        val normal = decideAlert(reading(25.0), Preferences(), AlertState(), now)!!
        val changed = Preferences(maxC = 24.0)
        assertNull(decideAlert(reading(25.0), changed, normal.state, now))
        assertNotNull(decideAlert(reading(25.0, now + 1), changed, normal.state, now + 1)!!.alert)
    }

    @Test fun lightAlertsUseFirstReadingAsBaselineAndAlertOnStatusChanges() {
        val first = decideAlert(reading().copy(lightStatus = "LIGHT"), Preferences(), AlertState(), now)!!
        assertNull(first.lightAlert)
        assertEquals("LIGHT", first.state.lastLightStatus)

        val dark = decideAlert(reading(timestamp = now + 1).copy(lightStatus = "DARK"), Preferences(), first.state, now + 1)!!
        assertEquals(AlertType.LIGHT_CHANGED_TO_DARK, dark.lightAlert!!.type)
        assertEquals("device001_${now + 1}_light", dark.lightAlert.id)
        assertNull(decideAlert(reading(timestamp = now + 2).copy(lightStatus = "DARK"), Preferences(), dark.state, now + 2)!!.lightAlert)

        val light = decideAlert(reading(timestamp = now + 3).copy(lightStatus = "LIGHT"), Preferences(), dark.state, now + 3)!!
        assertEquals(AlertType.LIGHT_CHANGED_TO_LIGHT, light.lightAlert!!.type)
    }

    @Test fun disabledLightAlertsAdvanceStatusWithoutReplayingAndCanCoexistWithTemperatureAlert() {
        val first = decideAlert(reading().copy(lightStatus = "LIGHT"), Preferences(), AlertState(), now)!!
        val disabled = decideAlert(
            reading(25.0, now + 1).copy(lightStatus = "DARK"),
            Preferences(lightAlertsEnabled = false),
            first.state,
            now + 1,
        )!!
        assertNull(disabled.lightAlert)
        assertEquals("DARK", disabled.state.lastLightStatus)
        assertNull(decideAlert(reading(36.0, now + 2).copy(lightStatus = "DARK"), Preferences(), disabled.state, now + 2)!!.lightAlert)

        val simultaneous = decideAlert(
            reading(36.0, now + 3).copy(lightStatus = "LIGHT"),
            Preferences(),
            disabled.state,
            now + 3,
        )!!
        assertEquals(AlertType.TEMPERATURE_HIGH, simultaneous.alert!!.type)
        assertEquals(AlertType.LIGHT_CHANGED_TO_LIGHT, simultaneous.lightAlert!!.type)
        assertNotEquals(simultaneous.alert.id, simultaneous.lightAlert.id)
    }
}
