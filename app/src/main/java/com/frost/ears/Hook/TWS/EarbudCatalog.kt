package com.frost.ears.hook.TWS

/**
 * 品牌耳机 → 官方控制 app 登记表（仅保留本模块实现了私有协议的品牌）。
 * 根据设备名匹配品牌，从候选 app 挑已安装的打开。新增品牌只需加一条 [CatalogEntry]。
 * 设备名匹配统一 normalize：小写 + 去非字母数字（中文保留）。
 */
object EarbudCatalog {

    /** 一个官方 app：包名 + 首页 Activity（null 则由系统 launch intent 打开首页）。 */
    data class App(
        val packageName: String,
        val activityName: String? = null,
    )

    /** 一个品牌：候选官方 app（按优先级）+ 设备名匹配规则。 */
    data class CatalogEntry(
        val brand: String,
        val apps: List<App>,
        val matcher: (String) -> Boolean,
    ) {
        /** normalize 后的设备名（小写 + 去非字母数字，中文保留） */
        private fun norm(name: String): String = name.lowercase().filter(Char::isLetterOrDigit)

        fun matches(deviceName: String): Boolean = matcher(norm(deviceName))
    }

    // ==================== 品牌登记 ====================

    private fun containsAny(vararg keys: String) = { n: String ->
        keys.any { it in n }
    }

    private fun startsWithAny(vararg keys: String) = { n: String ->
        keys.any { n.startsWith(it) }
    }

    private val catalog: List<CatalogEntry> = listOf(
        // vivo / iQOO（GAIA 协议）
        // 不指定 activity：统一打开应用首页，避免直接进内部页面导致白屏
        CatalogEntry(
            brand = "vivo",
            apps = listOf(App(packageName = "com.vivo.vivotws")),
            matcher = startsWithAny("vivotws", "iqootws"),
        ),
        // 原道 NiceHCK / 原道 OriG in（NiceHCK 二进制 RFCOMM 协议）
        CatalogEntry(
            brand = "nicehck",
            apps = listOf(App(packageName = "com.yuandao.nicehck")),
            matcher = containsAny("nicehck", "yuandao"),
        ),
        // 华为 FreeBuds / FreeClip / FreeLace（Huawei 5A SPP 协议）
        CatalogEntry(
            brand = "huawei",
            apps = listOf(App(packageName = "com.huawei.smartaudio")),
            matcher = startsWithAny(
                "huaweifreebuds", "freebuds",
                "huaweifreeclip", "freeclip",
                "huaweifreelace", "freelace",
            ),
        ),
    )

    // ==================== 查询 ====================

    /** 所有已登记品牌，供设置界面展示 */
    fun all(): List<CatalogEntry> = catalog

    /**
     * 按设备名（name/alias）匹配品牌，返回该品牌候选官方 app 列表（按优先级）。
     * 返回 null = 未识别的品牌（调用方应走魅族原生行为）。
     */
    fun matchByDeviceName(deviceName: String?): List<App>? {
        if (deviceName == null || deviceName.isBlank()) return null
        return catalog.firstOrNull { it.matches(deviceName) }?.apps
    }

    /**
     * 按设备名匹配品牌名（供蓝牙进程选择协议用）。
     * 返回 null = 未识别。
     */
    fun brandOfDevice(deviceName: String?): String? {
        if (deviceName == null || deviceName.isBlank()) return null
        return catalog.firstOrNull { it.matches(deviceName) }?.brand
    }
}
