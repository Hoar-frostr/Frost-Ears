package com.frost.ears.hook.TWS

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import io.github.libxposed.api.XposedModule
import com.frost.ears.utils.XLog

object MeizuFastPairPop {

    private const val PKG = "com.android.bluetooth"
    private const val CLS_SVC = "com.android.bluetooth.fastpair.MzFastPairService"
    private const val ACTION = "com.meizu.bluetooth.fastpair.action.FAST_PAIR_BATTERY"
    private const val EX_ACTION = "action"
    private const val EX_BATTERY = "meizu.bluetooth.fastpair.extra.FAST_PAIR_BATTERY"
    private const val EX_VPID = "meizu.bluetooth.fastpair.extra.FAST_PAIR_VPID"
    private const val EX_DEVICE = "android.bluetooth.device.extra.DEVICE"

    private const val VPID = "26d5:1"

    fun show(ctx: Context?, device: BluetoothDevice, left: Int, right: Int, box: Int, module: XposedModule?) {
        if (ctx == null) return
        try {
            val l = if (left in 0..100) left else 0
            val r = if (right in 0..100) right else 0
            val b = if (box in 0..100) box else 0
            val at = "3,1,$l,2,$r,3,$b"

            val intent = Intent()
            intent.setClassName(PKG, CLS_SVC)
            intent.putExtra(EX_ACTION, ACTION)
            intent.putExtra(EX_BATTERY, at)
            intent.putExtra(EX_VPID, VPID)
            intent.putExtra(EX_DEVICE, device)
            ctx.startService(intent)

        } catch (e: Throwable) {}
    }
}