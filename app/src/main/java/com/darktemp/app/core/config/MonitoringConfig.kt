package com.darktemp.app.core.config

object MonitoringConfig {
    const val DEVICE_ID = "device001"
    const val FRESHNESS_TIMEOUT_MS = 90_000L
    const val FUTURE_TOLERANCE_MS = 5_000L
    const val EARLIEST_TIMESTAMP_MS = 1_577_836_800_000L // 2020-01-01 UTC
    const val HISTORY_LIMIT = 100
}
