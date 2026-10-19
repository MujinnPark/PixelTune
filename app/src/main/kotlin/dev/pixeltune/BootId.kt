package dev.pixeltune

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import kotlin.math.abs

/**
 * Tells one boot from the next without trusting a single source. Settings.Global.BOOT_COUNT read 0 inside the app
 * on a Samsung A07 while the shell reported 22, so it is only a fallback here.
 * Order: kernel boot_id, then BOOT_COUNT (if > 0), then boot time derived from uptime.
 */
object BootId {
    /** Returns (id, source). The id stays the same for the whole boot and changes after a reboot. */
    fun read(ctx: Context): Pair<String, String> {
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()?.takeIf { it.length >= 8 }?.let { return "u$it" to "boot_id" }
        val n = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (n > 0) return "c$n" to "boot_count"
        return "t${System.currentTimeMillis() - SystemClock.elapsedRealtime()}" to "uptime"
    }

    /** Uptime-based ids drift a little (NTP, rounding), so they match within two minutes. */
    fun same(a: String, b: String): Boolean {
        if (a.startsWith("t") && b.startsWith("t")) {
            val x = a.drop(1).toLongOrNull() ?: return false
            val y = b.drop(1).toLongOrNull() ?: return false
            return abs(x - y) < 120_000
        }
        return a == b
    }
}
