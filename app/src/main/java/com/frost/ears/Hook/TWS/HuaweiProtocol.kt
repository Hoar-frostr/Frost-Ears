package com.frost.ears.hook.TWS

/**
 * 华为 FreeBuds / FreeClip 的 SPP 私有协议（参考 HyperEars HuaweiFreebudsSppCodec）。
 * 帧：[0x5A] [len:2BE] [0x00] [cmd:2] [TLV...] [crc16:2]，CRC16/XMODEM 覆盖前 n-2 字节。
 */
object HuaweiProtocol {

    private const val CMD_BATTERY_READ = 0x0108
    private const val CMD_BATTERY_NOTIFY = 0x0127
    private const val CMD_NOISE_STATE = 0x2B2A
    private const val CMD_NOISE_WRITE = 0x2B04

    private const val MARKER: Byte = 0x5A
    private const val MIN_FRAME_SIZE = 6
    private const val MIN_PAYLOAD_LENGTH = 4

    enum class NoiseMode(val wire: Int) {
        OFF(0x00),
        ANC(0x01),
        TRANSPARENCY(0x02),
    }

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
    )

    val queryBattery: ByteArray = packet(
        CMD_BATTERY_READ,
        listOf(1 to byteArrayOf(), 2 to byteArrayOf(), 3 to byteArrayOf()),
    )

    val queryNoiseState: ByteArray = packet(
        CMD_NOISE_STATE,
        listOf(1 to byteArrayOf(), 2 to byteArrayOf()),
    )

    fun setNoiseMode(mode: NoiseMode): ByteArray {
        val unchanged = if (mode == NoiseMode.OFF) 0x00 else 0xFF
        return packet(
            CMD_NOISE_WRITE,
            listOf(1 to byteArrayOf(mode.wire.toByte(), unchanged.toByte())),
        )
    }

    fun packet(command: Int, params: List<Pair<Int, ByteArray>>): ByteArray {
        val bodySize = 2 + params.sumOf { 2 + it.second.size }
        val frame = ByteArray(bodySize + 6)
        frame[0] = MARKER
        val length = bodySize + 1
        frame[1] = (length shr 8).toByte()
        frame[2] = length.toByte()
        frame[3] = 0x00
        frame[4] = (command shr 8).toByte()
        frame[5] = command.toByte()
        var offset = 6
        for ((type, value) in params) {
            frame[offset++] = type.toByte()
            frame[offset++] = value.size.toByte()
            value.copyInto(frame, offset)
            offset += value.size
        }
        val crc = crc16Xmodem(frame, 0, frame.size - 2)
        frame[frame.size - 2] = (crc shr 8).toByte()
        frame[frame.size - 1] = crc.toByte()
        return frame
    }

    fun crc16Xmodem(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            crc = crc xor (data[i].unsigned() shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    fun parseBattery(bytes: ByteArray): BatteryState? {
        val frame = parseFrame(bytes) ?: return null
        if (frame.command != CMD_BATTERY_READ && frame.command != CMD_BATTERY_NOTIFY) return null
        val components = frame.params[2]?.takeIf { it.size == 3 } ?: return null
        val left = components[0].unsigned().percentOrNull()
        val right = components[1].unsigned().percentOrNull()
        val case = components[2].unsigned().percentOrNull()
        if (left == null && right == null && case == null) return null
        return BatteryState(left, right, case)
    }

    fun parseNoiseMode(bytes: ByteArray): NoiseMode? {
        val frame = parseFrame(bytes) ?: return null
        if (frame.command != CMD_NOISE_STATE) return null
        val data = frame.params[1] ?: return null
        if (data.size != 2) return null
        return NoiseMode.entries.firstOrNull { it.wire == data[1].unsigned() }
    }

    private data class Frame(
        val command: Int,
        val params: Map<Int, ByteArray>,
    )

    private fun parseFrame(bytes: ByteArray): Frame? {
        if (bytes.size < MIN_FRAME_SIZE) return null
        if (bytes[0] != MARKER) return null
        val length = (bytes[1].unsigned() shl 8) or bytes[2].unsigned()
        if (bytes.size != length + 5) return null
        if (bytes[3] != 0x00.toByte()) return null
        val expected = (bytes[bytes.size - 2].unsigned() shl 8) or bytes[bytes.size - 1].unsigned()
        if (crc16Xmodem(bytes, 0, bytes.size - 2) != expected) return null
        val command = (bytes[4].unsigned() shl 8) or bytes[5].unsigned()
        val params = LinkedHashMap<Int, ByteArray>()
        var pos = 6
        while (pos < bytes.size - 2) {
            if (pos + 1 >= bytes.size - 2) return null
            val type = bytes[pos].unsigned()
            val len = bytes[pos + 1].unsigned()
            if (pos + 2 + len > bytes.size - 2) return null
            params[type] = bytes.copyOfRange(pos + 2, pos + 2 + len)
            pos += len + 2
        }
        return Frame(command, params)
    }

    /** 5A 帧流解码器。 */
    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<ByteArray> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val frames = mutableListOf<ByteArray>()
            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOfFirst { it == MARKER }
                if (marker < 0) {
                    pending = ByteArray(0)
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size < MIN_FRAME_SIZE) break

                val length = (pending[1].unsigned() shl 8) or pending[2].unsigned()
                val frameSize = length + 5
                if (length < MIN_PAYLOAD_LENGTH) {
                    if (pending.size < frameSize) break
                    pending = pending.copyOfRange(frameSize, pending.size)
                    continue
                }
                if (frameSize > MAX_FRAME_SIZE || pending[3] != 0x00.toByte()) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                if (pending.size < frameSize) break
                frames += pending.copyOfRange(0, frameSize)
                pending = pending.copyOfRange(frameSize, pending.size)
            }
            return frames
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun Int.percentOrNull(): Int? = takeIf { it in 1..100 }

    private const val MAX_FRAME_SIZE = 4096
}
