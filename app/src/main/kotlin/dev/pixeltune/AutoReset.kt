package dev.pixeltune

import android.content.Context
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
        val (boot, source) = BootId.read(ctx)
        val last = prefs.getString("last_boot_id", null)
        if (last == null) { prefs.edit().putString("last_boot_id", boot).apply(); return } // first run: only remember this boot
        if (BootId.same(boot, last)) return
        if (Shell.status() != 2) return
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                // Only undo what PixelTune itself applied. Deleting keys that were never touched would wipe OEM defaults.
                val tweaks = (Tweaks.toggles(120) + Tweaks.packs(ctx)).filter { prefs.getBoolean(it.id, false) || Snapshot.has(prefs, it) }
                val cmds = tweaks.flatMap { Snapshot.revertCmds(prefs, it) }.toMutableList()
                if (prefs.getInt("res_scale", 100) < 100 || prefs.getBoolean("res_pending", false)) {
                    cmds += "wm size reset"
                    cmds += "wm density reset"
                }
                (prefs.getString("alive_sel", "") ?: "").split(",").filter { it.isNotBlank() }.forEach {
                    cmds += "cmd deviceidle whitelist -$it"
                    cmds += "cmd appops set $it RUN_ANY_IN_BACKGROUND default"
                }
                val r = Shell.runAll(cmds)
                tweaks.forEach { Snapshot.clear(prefs, it) }
                val e = prefs.edit()
                tweaks.forEach { e.putBoolean(it.id, false) }
                e.putString("last_boot_id", boot).putInt("res_scale", 100).putBoolean("res_pending", false).apply()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                LogStore.save(ctx, "pixeltune_autoreset_$stamp.txt",
                    "Reset after reboot (detected by $source)\n${cmds.size} command(s) sent\n\nCommands:\n${cmds.joinToString("\n")}\n\nOutput:\n${r.raw}")
                version.intValue = version.intValue + 1
            } catch (_: Throwable) {
                // Shizuku went away mid-way: last_boot_id is unchanged, so this retries next time it starts.
            } finally {
                running.set(false)
            }
        }.start()
    }
}
