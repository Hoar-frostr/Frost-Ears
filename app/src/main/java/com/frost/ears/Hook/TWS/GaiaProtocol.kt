package com.frost.ears.hook.TWS

/**
 * 通用 GAIA-shaped 私有协议编解码层，与品牌无关：
 * 帧版本、vendor、命令号、载荷后缀等字节级差异收进 [BrandConfig]，业务代码不感知品牌。
 * 帧布局：[0xFF, version, flags, len, vendor(2), command(2), payload...]，运行于经典 RFCOMM。
 */
object GaiaProtocol {

    // 通用 GAIA vendor：标准 GAIA 家族。私有品牌帧用各自品牌 vendor。
    const val GAIA_VENDOR = 0x000A

    // 握手命令（走标准 GAIA vendor）
    private const val HANDSHAKE = 0x0300
    private const val HANDSHAKE_RESPONSE = 0x8300

    private const val PREAMBLE = 0xFF
    private const val COMMAND_BYTES = 4

    /**
     * 降噪模式。线值即 GAIA 上的一字节模式值。
     * 注意：各品牌/机型在“UI 枚举 -> 线值”上可能不同，这里以 vivo 家族为准，
     * 其它品牌如需不同映射，在适配时扩展该枚举即可。
     */
    enum class NoiseMode(val wireValue: Int) {
        ANC(0),
        OFF(1),
        TRANSPARENCY(2);

        companion object {
            fun fromWire(value: Int): NoiseMode? =
                entries.firstOrNull { it.wireValue == value }
        }
    }

    /**
     * 品牌 / 机型的字节级差异配置。
     * 适配一个新品牌时，只需要新增一个枚举值（必要时新增一个品牌枚举）。
     */
    enum class BrandConfig(
        val label: String,
        internal val gaiaVersion: Int,
        internal val vendor: Int,
        internal val setNoiseSuffix: ByteArray,
    ) {
        /**
         * vivo 家族默认（参考 HyperEars FAMILY_DEFAULT_V4）：
         * 版本 v4，vendor 0x001B，设置降噪后缀 [3, 1]。
         */
        VIVO_DEFAULT_V4(
            label = "vivo / iQOO 家族默认 v4",
            gaiaVersion = 4,
            vendor = 0x001B,
            setNoiseSuffix = byteArrayOf(3, 1),
        ),

        /**
         * vivo TWS Air3 Pro（参考 HyperEars AIR3_PRO_CAPTURED）：
         * 版本 v3，vendor 0x001B，设置降噪后缀 [4, 0]。
         */
        VIVO_AIR3_PRO_V3(
            label = "vivo TWS Air3 Pro v3",
            gaiaVersion = 3,
            vendor = 0x001B,
            setNoiseSuffix = byteArrayOf(4, 0),
        ),

        /**
         * vivo TWS 3e（参考 HyperEars TWS_3E_V3）：
         * 版本 v3，vendor 0x001B，设置降噪后缀 [3]。
         */
        VIVO_TWS3E_V3(
            label = "vivo TWS 3e v3",
            gaiaVersion = 3,
            vendor = 0x001B,
            setNoiseSuffix = byteArrayOf(3),
        ),
    }

    /**
     * 品牌自身的 GAIA 服务 UUID / RFCOMM 通道。
     * 后续适配新品牌时在这里登记该品牌的传输端点。
     */
    data class Transport(
        val serviceUuid: String,
        val channel: Int? = null,
    )

    /** 一个已解析的完整帧。 */
    data class Frame(
        val version: Int,
        val flags: Int,
        val vendor: Int,
        val command: Int,
        val payload: ByteArray,
    )

    /** 电量状态：左 / 右 / 仓（%），以及各自的充电位标志。 */
    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
        val caseCharging: Boolean,
    )

    /** 降噪状态：当前模式 + 是否来自 ACK。 */
    data class NoiseState(
        val mode: NoiseMode,
        val acknowledged: Boolean,
    )

    /** 握手状态：是否被接受。 */
    data class HandshakeState(
        val accepted: Boolean,
    )

    // ========================================================================
    // ========== 发送命令构造 ==========
    // ========================================================================

    /** 握手请求：走标准 GAIA vendor 0x000A，命令 0x0300。 */
    fun handshake(config: BrandConfig): ByteArray = frame(
        version = config.gaiaVersion,
        vendor = GAIA_VENDOR,
        command = HANDSHAKE,
    )

    /** 查询电量：无载荷，命令 0x0207。 */
    fun queryBattery(config: BrandConfig): ByteArray = frame(
        version = config.gaiaVersion,
        vendor = config.vendor,
        command = QUERY_BATTERY,
    )

    /** 设置降噪模式：命令 0x0130，载荷 = [模式线值] + 品牌后缀。 */
    fun setNoiseMode(mode: NoiseMode, config: BrandConfig): ByteArray = frame(
        version = config.gaiaVersion,
        vendor = config.vendor,
        command = SET_NOISE_MODE,
        payload = byteArrayOf(mode.wireValue.toByte()) + config.setNoiseSuffix,
    )

    // ========================================================================
    // ========== 响应解析 ==========
    // ========================================================================

    /** 解析电量上报（0x8207）：载荷 [0, left, right, case, charging]。 */
    fun parseBatteryState(frame: Frame, config: BrandConfig): BatteryState? {
        if (frame.vendor != config.vendor || frame.command != REPORT_BATTERY) return null
        if (frame.payload.size < 5 || frame.payload[0].unsigned() != 0) return null
        val charging = frame.payload[4].unsigned()
        return BatteryState(
            leftPercent = frame.payload[1].batteryPercent(),
            rightPercent = frame.payload[2].batteryPercent(),
            casePercent = frame.payload[3].batteryPercent(),
            leftCharging = charging and 0x01 != 0,
            rightCharging = charging and 0x02 != 0,
            caseCharging = charging and 0x04 != 0,
        )
    }

    /** 解析降噪 ACK（0x8130）/ 上报（0x8230）：载荷 [0, mode, ...]。 */
    fun parseNoiseState(frame: Frame, config: BrandConfig): NoiseState? {
        if (frame.vendor != config.vendor) return null
        val acknowledged = when (frame.command) {
            ACK_NOISE_MODE -> true
            REPORT_NOISE_MODE -> false
            else -> return null
        }
        if (frame.payload.size < 2 || frame.payload[0].unsigned() != 0) return null
        val mode = NoiseMode.fromWire(frame.payload[1].unsigned()) ?: return null
        return NoiseState(mode = mode, acknowledged = acknowledged)
    }

    /** 解析握手响应（0x8300）：accepted = 首个载荷字节 == 0。 */
    fun parseHandshakeState(frame: Frame): HandshakeState? {
        if (frame.vendor != GAIA_VENDOR || frame.command != HANDSHAKE_RESPONSE) return null
        return HandshakeState(accepted = frame.payload.firstOrNull()?.unsigned() == 0)
    }

    // ========================================================================
    // ========== 帧编解码核心 ==========
    // ========================================================================

    private const val SET_NOISE_MODE = 0x0130
    private const val QUERY_NOISE_MODE = 0x0230
    private const val ACK_NOISE_MODE = 0x8130
    private const val REPORT_NOISE_MODE = 0x8230
    private const val QUERY_BATTERY = 0x0207
    private const val REPORT_BATTERY = 0x8207

    fun frame(
        version: Int,
        vendor: Int,
        command: Int,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray {
        return ByteArray(4 + COMMAND_BYTES + payload.size).apply {
            this[0] = PREAMBLE.toByte()
            this[1] = version.toByte()
            this[2] = 0
            this[3] = payload.size.toByte()
            writeShort(vendor, 4)
            writeShort(command, 6)
            payload.copyInto(this, destinationOffset = 8)
        }
    }

    /** 简单粘包解码器：只处理非扩展、无校验的普通帧。 */
    class Decoder {
        private val buffer = mutableListOf<Byte>()

        fun offer(chunk: ByteArray): List<Frame> {
            chunk.forEach { buffer.add(it) }
            val frames = mutableListOf<Frame>()
            while (true) {
                discardNoise()
                if (buffer.size < 8) return frames
                val payloadLen = buffer[3].unsigned()
                val total = 8 + payloadLen
                if (buffer.size < total) return frames

                val raw = ByteArray(total)
                repeat(total) { raw[it] = buffer.removeAt(0) }

                val payload = ByteArray(payloadLen)
                var i = 0
                while (i < payloadLen) {
                    payload[i] = raw[8 + i]
                    i++
                }
                frames += Frame(
                    version = raw[1].unsigned(),
                    flags = raw[2].unsigned(),
                    vendor = raw.readShort(4),
                    command = raw.readShort(6),
                    payload = payload,
                )
            }
        }

        fun reset() = buffer.clear()

        private fun discardNoise() {
            while (buffer.isNotEmpty() && buffer.first().unsigned() != PREAMBLE) {
                buffer.removeAt(0)
            }
        }
    }

    // ========================================================================
    // ========== 工具 ==========
    // ========================================================================

    private fun ByteArray.writeShort(value: Int, offset: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.readShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun Byte.batteryPercent(): Int? = unsigned().takeIf { it in 0..100 }

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
