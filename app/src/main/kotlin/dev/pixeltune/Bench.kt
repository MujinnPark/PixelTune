package dev.pixeltune

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

data class BenchResult(
    val pkg: String, val label: String, val ts: Long, val tweaksOn: Int,
    val medianMs: Int, val minMs: Int, val maxMs: Int, val jankPct: Float, val p90: Int, val frames: Int,
)

/** Cold-start timing (am start -W) plus a scripted scroll read through dumpsys gfxinfo. */
object Bench {
    fun run(
        pkg: String, label: String, comp: String, self: String, tweaksOn: Int, w: Int, h: Int, progress: (String) -> Unit,
    ): Result<BenchResult> = runCatching {
        try {
            val times = mutableListOf<Int>()
            repeat(5) { i ->
                progress("Cold start ${i + 1}/5...")
                val out = Shell.run("am force-stop $pkg; sleep 1; am start -W -n $comp; sleep 2; input keyevent KEYCODE_HOME; sleep 1")
                Regex("TotalTime:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toInt()?.let { times += it }
            }
            if (times.isEmpty()) error("Couldn't read launch times")
            progress("Scroll test...")
            val x = w / 2; val y1 = (h * 0.75).toInt(); val y2 = (h * 0.25).toInt()
            Shell.run("am start -W -n $comp >/dev/null; sleep 2; dumpsys gfxinfo $pkg reset >/dev/null")
            Shell.run("for i in 1 2 3 4 5 6; do input swipe $x $y1 $x $y2 300; sleep 1.5; input swipe $x $y2 $x $y1 300; sleep 1.5; done")
            val g = Shell.run("dumpsys gfxinfo $pkg")
            val frames = Regex("Total frames rendered:\\s*(\\d+)").find(g)?.groupValues?.get(1)?.toInt() ?: 0
            val jank = Regex("Janky frames:\\s*\\d+\\s*\\(([\\d.]+)%\\)").find(g)?.groupValues?.get(1)?.toFloat() ?: 0f
            val p90 = Regex("90th percentile:\\s*(\\d+)ms").find(g)?.groupValues?.get(1)?.toInt() ?: 0
            val s = times.sorted()
            BenchResult(pkg, label, System.currentTimeMillis(), tweaksOn, s[s.size / 2], s.first(), s.last(), jank, p90, frames)
        } finally {
            runCatching { Shell.run("input keyevent KEYCODE_HOME; sleep 1; am start -n $self/.MainActivity") }
        }
    }

    fun launchableApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.sortedBy { it.first.lowercase() }
    }

    fun load(prefs: SharedPreferences): List<BenchResult> =
        (prefs.getString("bench_results", "") ?: "").lines().mapNotNull { l ->
            val f = l.split("|")
            if (f.size < 10) null else runCatching {
                BenchResult(f[0], f[1], f[2].toLong(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toFloat(), f[8].toInt(), f[9].toInt())
            }.getOrNull()
        }

    fun save(prefs: SharedPreferences, list: List<BenchResult>) {
        prefs.edit().putString("bench_results", list.take(20).joinToString("\n") {
            "${it.pkg}|${it.label.replace("|", " ")}|${it.ts}|${it.tweaksOn}|${it.medianMs}|${it.minMs}|${it.maxMs}|${it.jankPct}|${it.p90}|${it.frames}"
        }).apply()
    }
}
