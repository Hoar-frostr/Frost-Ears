package com.frost.ears.hook.TWS

/**
 * 蓝牙进程（BluetoothHook）与各耳机 app（MeizuTWS / MeizuiotTWS）之间约定的广播动作。
 *
 * 命名采用品牌无关的 EARBUD 通用命名空间（不再用 VIVO 前缀），
 * 以后适配其它耳机品牌时，这三个动作语义不变，无需改这里。
 */
object TwsActions {

    /** 电量上报：蓝牙进程 GAIA 读取左右耳 + 仓电量后发出。extras: left, right, box */
    const val ACTION_BATTERY = "com.frost.ears.EARBUD_BATTERY"

    /** 设备信息上报：蓝牙进程读取真实设备名 + MAC 后发出。extras: name, mac */
    const val ACTION_DEVICE_INFO = "com.frost.ears.EARBUD_DEVICE_INFO"

    /** 降噪设置请求：耳机 app 切降噪时发给蓝牙进程走 GAIA 帧。extra: mode (1=关 2=降噪 3=通透) */
    const val ACTION_SET_ANC = "com.frost.ears.EARBUD_SET_ANC"
}