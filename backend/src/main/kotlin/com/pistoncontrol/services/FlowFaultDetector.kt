package com.pistoncontrol.services

import java.util.concurrent.ConcurrentHashMap

/**
 * Flow Fault Detector
 *
 * Compares a valve's state with the flow reported by its water flow sensor (ZJ-DN32):
 * - NO_FLOW: valve is open but flow stays below noFlowThresholdLpm
 * - LEAK: valve is closed but flow stays above leakThresholdLpm
 *
 * A condition must hold continuously for gracePeriodMillis (measured from the later of
 * the first anomalous reading and the last valve state change) before it is reported.
 * This absorbs water ramp-up after opening and drain-down after closing.
 *
 * Tracking is in memory. A fault already stored in the database is kept as soon as the
 * condition is seen again, so a backend restart doesn't clear and re-raise it.
 */
class FlowFaultDetector(
    private val gracePeriodMillis: Long,
    private val noFlowThresholdLpm: Float,
    private val leakThresholdLpm: Float
) {
    enum class Fault(val dbValue: String, val eventType: String) {
        NO_FLOW("no_flow", "fault_no_flow"),
        LEAK("leak", "fault_leak");

        companion object {
            fun fromDb(value: String?): Fault? = values().find { it.dbValue == value }
        }
    }

    private data class Anomaly(val fault: Fault, val since: Long)

    // "deviceId:pistonNumber" -> condition currently being observed
    private val anomalies = ConcurrentHashMap<String, Anomaly>()

    /**
     * Evaluate one flow reading
     *
     * @param isActive Whether the valve is currently open
     * @param flowRate Measured flow in L/min
     * @param currentFault Fault currently stored for this valve
     * @param stateChangedAt Epoch millis of the valve's last state change, if known
     * @param now Epoch millis of this reading
     * @return The fault that should be active after this reading, or null if none
     */
    fun evaluate(
        deviceId: String,
        pistonNumber: Int,
        isActive: Boolean,
        flowRate: Float,
        currentFault: Fault?,
        stateChangedAt: Long?,
        now: Long
    ): Fault? {
        val key = "$deviceId:$pistonNumber"

        val candidate = when {
            isActive && flowRate < noFlowThresholdLpm -> Fault.NO_FLOW
            !isActive && flowRate > leakThresholdLpm -> Fault.LEAK
            else -> null
        }

        if (candidate == null) {
            anomalies.remove(key)
            return null
        }

        val anomaly = anomalies[key]?.takeIf { it.fault == candidate }
            ?: Anomaly(candidate, now).also { anomalies[key] = it }

        if (candidate == currentFault) return candidate

        val since = maxOf(anomaly.since, stateChangedAt ?: 0L)
        return if (now - since >= gracePeriodMillis) candidate else null
    }
}
