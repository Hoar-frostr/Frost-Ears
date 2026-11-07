package com.frost.ears.utils

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.DataOutputStream

/**
 * 依赖 root 的作用域重启。
 *
 * pkill 之后系统会把目标进程重新拉起（systemui / 蓝牙 / 魅族耳机都是常驻服务）。
 */
object RootUtils {

    /** 系统界面 */
    val SYSTEM_UI_PROCESSES = listOf("com.android.systemui")

    /** 蓝牙相关三个进程 */
    val BLUETOOTH_PROCESSES = listOf(
        "com.android.bluetooth",
        "com.meizu.earphone",
        "com.meizu.meizuiot",
    )

    /** su 是否可用 */
    fun hasRootPermission(): Boolean = try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("exit\n")
        os.flush()
        os.close()
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    /**
     * 震动一下，然后用 root 把目标进程 pkill 掉。
     * 会阻塞等待 su 返回，请放到子线程调用。
     */
    fun restartWithVibration(context: Context, packages: List<String>): Boolean {
        vibrate(context)
        return try {
            val root = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(root.outputStream)
            packages.forEach { os.writeBytes("pkill -f $it\n") }
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            root.waitFor() == 0
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    50L,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        }
    }
}
