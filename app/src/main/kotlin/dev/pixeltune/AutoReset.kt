package dev.pixeltune

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns every tweak off once after each reboot, as soon as Shizuku is available.
 * Android keeps most settings across a reboot, so this reverts them explicitly.
 */
object AutoReset {
    /** Bumped after a reset so a running UI can reload its state. */
    val version = mutableIntStateOf(0)
    private val running = AtomicBoolean(false)

    fun runIfNeeded(ctx: Context) {
        val prefs = ctx.getSharedPreferences("tweaks", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reset_on_boot", true)) return
        val boot = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot < 0) return
        val last = prefs.getInt("last_boot", -1)
        if (last < 0) { prefs.edit().putInt("last_boot", boot).apply(); return } // first run: only remember this boot
        if (boot == last) return
        if (Shell.status() != 2) return
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                val tweaks = Tweaks.toggles(120) + Tweaks.packs(ctx)
                val cmds = tweaks.flatMap { it.revert }.toMutableList()
                cmds += "wm size reset"
                cmds += "wm density reset"
                (prefs.getString("alive_sel", "") ?: "").split(",").filter { it.isNotBlank() }.forEach {
                    cmds += "cmd deviceidle whitelist -$it"
                    cmds += "cmd appops set $it RUN_ANY_IN_BACKGROUND default"
                }
                val r = Shell.runAll(cmds)
                val e = prefs.edit()
                tweaks.forEach { e.putBoolean(it.id, false) }
                e.putInt("last_boot", boot).putInt("res_scale", 100).putBoolean("res_pending", false).apply()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                LogStore.save(ctx, "pixeltune_autoreset_$stamp.txt",
                    "Reset after reboot (boot #$boot)\n${cmds.size} command(s) sent\n\nOutput:\n${r.raw}")
                version.intValue = version.intValue + 1
            } catch (_: Throwable) {
                // Shizuku went away mid-way: last_boot is unchanged, so this retries next time it starts.
            } finally {
                running.set(false)
            }
        }.start()
    }
}
