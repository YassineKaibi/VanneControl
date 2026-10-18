package com.pistoncontrol.services

import com.pistoncontrol.services.FlowFaultDetector.Fault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlowFaultDetectorTest {

    private val device = "00000000-0000-0000-0000-000000000001"
    private fun detector() = FlowFaultDetector(
        gracePeriodMillis = 15_000,
        noFlowThresholdLpm = 0.5f,
        leakThresholdLpm = 0.5f
    )

    private fun FlowFaultDetector.read(
        isActive: Boolean,
        flow: Float,
        now: Long,
        current: Fault? = null,
        stateChangedAt: Long? = null
    ) = evaluate(device, 1, isActive, flow, current, stateChangedAt, now)

    @Test
    fun `open valve with flow is healthy`() {
        val d = detector()
        assertNull(d.read(isActive = true, flow = 20f, now = 0))
        assertNull(d.read(isActive = true, flow = 20f, now = 60_000))
    }

    @Test
    fun `open valve without flow raises no_flow after grace period`() {
        val d = detector()
        assertNull(d.read(isActive = true, flow = 0f, now = 0, stateChangedAt = 0))
        assertNull(d.read(isActive = true, flow = 0f, now = 14_999, stateChangedAt = 0))
        assertEquals(Fault.NO_FLOW, d.read(isActive = true, flow = 0f, now = 15_000, stateChangedAt = 0))
    }

    @Test
    fun `flow returning within grace period resets the timer`() {
        val d = detector()
        d.read(isActive = true, flow = 0f, now = 0)
        assertNull(d.read(isActive = true, flow = 10f, now = 10_000))
        assertNull(d.read(isActive = true, flow = 0f, now = 20_000))
        assertNull(d.read(isActive = true, flow = 0f, now = 30_000))
        assertEquals(Fault.NO_FLOW, d.read(isActive = true, flow = 0f, now = 35_000))
    }

    @Test
    fun `grace period restarts when the valve state changes`() {
        val d = detector()
        d.read(isActive = true, flow = 0f, now = 0, stateChangedAt = 0)
        // Valve toggled at 10s (closed and reopened between readings)
        assertNull(d.read(isActive = true, flow = 0f, now = 20_000, stateChangedAt = 10_000))
        assertEquals(Fault.NO_FLOW, d.read(isActive = true, flow = 0f, now = 25_000, stateChangedAt = 10_000))
    }

    @Test
    fun `closed valve with flow raises leak after grace period`() {
        val d = detector()
        assertNull(d.read(isActive = false, flow = 8f, now = 0))
        assertEquals(Fault.LEAK, d.read(isActive = false, flow = 8f, now = 15_000))
    }

    @Test
    fun `closed valve draining below threshold is not a leak`() {
        val d = detector()
        assertNull(d.read(isActive = false, flow = 8f, now = 0))
        assertNull(d.read(isActive = false, flow = 0.2f, now = 10_000))
        assertNull(d.read(isActive = false, flow = 0f, now = 30_000))
    }

    @Test
    fun `stored fault is kept immediately after restart`() {
        val d = detector()
        assertEquals(Fault.NO_FLOW, d.read(isActive = true, flow = 0f, now = 0, current = Fault.NO_FLOW))
    }

    @Test
    fun `fault clears once condition no longer holds`() {
        val d = detector()
        assertNull(d.read(isActive = true, flow = 12f, now = 0, current = Fault.NO_FLOW))
        // Valve closed while no_flow was active: no fault when flow is zero
        assertNull(d.read(isActive = false, flow = 0f, now = 0, current = Fault.NO_FLOW))
    }

    @Test
    fun `valves are tracked independently`() {
        val d = detector()
        d.evaluate(device, 1, true, 0f, null, null, 0)
        assertNull(d.evaluate(device, 2, true, 0f, null, null, 15_000))
        assertEquals(Fault.NO_FLOW, d.evaluate(device, 1, true, 0f, null, null, 15_000))
    }
}
