package com.pistoncontrol.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BinaryProtocolParserTest {

    private val parser = BinaryProtocolParser()
    private val deviceId = UUID.fromString("3f2b8c1e-9d4a-4b7e-a1c5-6e8f0d2b4a71")

    /**
     * Builds a 0x05 flow reading frame the way the ESP32 firmware must:
     * [type:1][deviceUUID:16 big-endian][piston:1][flow L/min float32 LE:4][timestamp int64 LE:8][CRC16 LE:2]
     */
    private fun flowFrame(pistonNumber: Int, flowRate: Float, timestamp: Long): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 16 + 13 + 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(BinaryProtocolParser.MSG_FLOW_READING)
        buffer.put(ByteBuffer.allocate(16).putLong(deviceId.mostSignificantBits).putLong(deviceId.leastSignificantBits).array())
        buffer.put(pistonNumber.toByte())
        buffer.putFloat(flowRate)
        buffer.putLong(timestamp)
        buffer.putShort(crc16(buffer.array(), buffer.position()).toShort())
        return buffer.array()
    }

    private fun crc16(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) (crc shr 1) xor 0x8005 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }

    @Test
    fun `parses flow reading frame`() {
        val parsed = parser.parse(flowFrame(pistonNumber = 3, flowRate = 42.5f, timestamp = 1_760_000_000_000))

        assertEquals(
            BinaryProtocolParser.ParsedMessage.FlowReading(deviceId, 3, 42.5f, 1_760_000_000_000),
            parsed
        )
    }

    @Test
    fun `parses reference frames from the ESP32 handover`() {
        fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

        assertEquals(
            BinaryProtocolParser.ParsedMessage.FlowReading(deviceId, 3, 12.5f, 1_760_796_000_000),
            parser.parse(hex("05 3f 2b 8c 1e 9d 4a 4b 7e a1 c5 6e 8f 0d 2b 4a 71 03 00 00 48 41 00 bf 9e f7 99 01 00 00 5f 06"))
        )
        assertEquals(
            BinaryProtocolParser.ParsedMessage.FlowReading(deviceId, 3, 0f, 1_760_796_002_000),
            parser.parse(hex("05 3f 2b 8c 1e 9d 4a 4b 7e a1 c5 6e 8f 0d 2b 4a 71 03 00 00 00 00 d0 c6 9e f7 99 01 00 00 4e c5"))
        )
        assertEquals(
            BinaryProtocolParser.ParsedMessage.PistonStateChange(deviceId, 3, true, 1_760_796_000_000),
            parser.parse(hex("01 3f 2b 8c 1e 9d 4a 4b 7e a1 c5 6e 8f 0d 2b 4a 71 03 01 00 bf 9e f7 99 01 00 00 f8 46"))
        )
    }

    @Test
    fun `rejects flow reading with invalid piston number`() {
        assertNull(parser.parse(flowFrame(pistonNumber = 9, flowRate = 1f, timestamp = 0)))
    }

    @Test
    fun `rejects negative or non-finite flow`() {
        assertNull(parser.parse(flowFrame(pistonNumber = 1, flowRate = -1f, timestamp = 0)))
        assertNull(parser.parse(flowFrame(pistonNumber = 1, flowRate = Float.NaN, timestamp = 0)))
    }

    @Test
    fun `rejects corrupted frame`() {
        val frame = flowFrame(pistonNumber = 1, flowRate = 5f, timestamp = 0)
        frame[20] = (frame[20].toInt() xor 0xFF).toByte()
        assertNull(parser.parse(frame))
    }
}
