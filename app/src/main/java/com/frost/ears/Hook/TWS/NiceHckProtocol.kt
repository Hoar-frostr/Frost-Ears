package com.frost.ears.hook.TWS

/**
 * 原道 NiceHCK / 原道 OriG in 的二进制 RFCOMM 私有协议（参考 HyperEars NiceHckWireCodec）。
 * 帧格式：[0x4E(MARKER), lenLo, lenHi, 0, opcodeLo, opcodeHi, payload...]，opcode 小端。
 */
object NiceHckProtocol {

    enum class NoiseMode(val wireValue: Int) {
        OFF(0x00),
        TRANSPARENCY(0x01),
        ANC(0x02),
        DEEP_ANC(0x03),
        WIND(0x11),
    }

    data class Frame(
        val opcode: Int,
        val parameters: ByteArray,
    )

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
    )

    private const val BATTERY_OPCODE = 0x0005
    private const val NOISE_QUERY_OPCODE = 0x0101
    private const val NOISE_SET_OPCODE = 0x0201
    private const val MARKER: Byte = 0x4E
    private const val HEADER_PAYLOAD_SIZE = 3

    fun queryBattery(): ByteArray = command(BATTERY_OPCODE)

    fun queryNoiseMode(): ByteArray = command(NOISE_QUERY_OPCODE)

    fun setNoiseMode(mode: NoiseMode): ByteArray =
        command(NOISE_SET_OPCODE, byteArrayOf(mode.wireValue.toByte(), 0))

    private fun command(opcode: Int, parameters: ByteArray = ByteArray(0)): ByteArray {
        val payloadLength = HEADER_PAYLOAD_SIZE + parameters.size
        return byteArrayOf(
            MARKER,
            payloadLength.toByte(),
            (payloadLength ushr 8).toByte(),
            0,
            opcode.toByte(),
            (opcode ushr 8).toByte(),
        ) + parameters
    }

    fun parseBattery(frame: Frame): BatteryState? {
        if (frame.opcode != BATTERY_OPCODE || frame.parameters.size < 3) return null
        return BatteryState(
            leftPercent = frame.parameters[0].batteryPercent(),
            rightPercent = frame.parameters[1].batteryPercent(),
            casePercent = frame.parameters[2].batteryPercent(),
        )
    }

    fun parseNoiseMode(frame: Frame): NoiseMode? {
        if (frame.opcode != NOISE_QUERY_OPCODE || frame.parameters.isEmpty()) return null
        return NoiseMode.entries.firstOrNull { it.wireValue == frame.parameters[0].unsigned() }
    }

    /** 简单粘包解码器。 */
    class Decoder {
        private val buffer = mutableListOf<Byte>()

        fun offer(chunk: ByteArray): List<Frame> {
            chunk.forEach { buffer.add(it) }
            val frames = mutableListOf<Frame>()
            while (buffer.size >= 6) {
                val marker = buffer.indexOfFirst { it == MARKER }
                if (marker < 0) {
                    buffer.clear()
                    break
                }
                if (marker > 0) repeat(marker) { buffer.removeAt(0) }
                if (buffer.size < 6) break

                val payloadLength = buffer[1].unsigned() or (buffer[2].unsigned() shl 8)
                val frameSize = payloadLength + 3
                if (payloadLength < HEADER_PAYLOAD_SIZE || frameSize > MAX_FRAME_SIZE) {
                    buffer.removeAt(0)
                    continue
                }
                if (buffer.size < frameSize) break

                val raw = ByteArray(frameSize)
                repeat(frameSize) { raw[it] = buffer.removeAt(0) }
                val parameters = ByteArray(frameSize - 6)
                repeat(parameters.size) { parameters[it] = raw[6 + it] }
                frames += Frame(
                    opcode = raw[4].unsigned() or (raw[5].unsigned() shl 8),
                    parameters = parameters,
                )
            }
            return frames
        }

        fun reset() = buffer.clear()
    }

    private fun Byte.batteryPercent(): Int? = unsigned().takeIf { it in 1..100 }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val MAX_FRAME_SIZE = 1024
}