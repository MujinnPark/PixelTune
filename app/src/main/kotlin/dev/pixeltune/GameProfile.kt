package dev.pixeltune

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * One-game profile built on Android's own Game Mode: performance mode, a lower render scale and an FPS cap.
 * Render scale is the one setting here that clearly cuts GPU load; the cap only works if the screen supports it.
 */
object GameProfile {
    val SCALES = listOf("disable", "0.9", "0.8", "0.7", "0.6", "0.5")
    val FPS = listOf(0, 30, 45, 60, 90, 120)

    private val PKG = Regex("^[A-Za-z0-9_.]+$")
    fun valid(pkg: String) = pkg.isNotBlank() && PKG.matches(pkg)

    fun scaleLabel(s: String) = if (s == "disable") "Off" else "${Math.round(s.toFloat() * 100)}%"

    /** Apply = performance mode plus the interventions; revert = clear the overrides and go back to standard mode. */
    fun tweak(pkg: String, label: String, scale: String, fps: Int): Tweak {
        val set = buildString {
            append("cmd game set --mode 2")
            if (scale != "disable") append(" --downscale ").append(scale)
            if (fps != 0) append(" --fps ").append(fps)
            append(' ').append(pkg)
        }
        return Tweak(
            id = "gameprof", title = "Game profile: $label", desc = "",
            apply = listOf("cmd game mode performance $pkg", set),
            revert = listOf("cmd game reset $pkg", "cmd game mode standard $pkg"),
            action = true,
        )
    }

    /** Launchable packages the system categorises as games. Many games are not categorised, hence the "games only" switch. */
    fun gamePackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        return Bench.launchableApps(ctx).map { it.second }.filter { pkg ->
            runCatching { pm.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME }.getOrDefault(false)
        }.toSet()
    }

    data class FpsReading(val fps: Int?, val excerpt: String)

    /**
     * Average FPS of the game's own surface from SurfaceFlinger's time stats. dumpsys gfxinfo is no use for games:
     * it only counts frames drawn by the UI toolkit. Waits [leadSeconds] so you can switch to the game, then samples.
     * The output format is not checked against AOSP source here, so the raw lines come back for the log.
     */
    fun measureFps(pkg: String, leadSeconds: Int = 8, sampleSeconds: Int = 4): FpsReading {
        val out = Shell.run(
            "sleep $leadSeconds; dumpsys SurfaceFlinger --timestats -clear -enable; sleep $sampleSeconds; " +
                "dumpsys SurfaceFlinger --timestats -dump; dumpsys SurfaceFlinger --timestats -disable"
        )
        return parse(out, pkg)
    }

    private fun parse(out: String, pkg: String): FpsReading {
        var layer: String? = null
        val hits = mutableListOf<Pair<String, Float>>()
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("layerName = ")) { layer = line.removePrefix("layerName = "); continue }
            if (!line.startsWith("averageFPS = ")) continue
            val l = layer ?: continue
            val v = line.removePrefix("averageFPS = ").trim().toFloatOrNull() ?: continue
            if (l.contains(pkg) && v > 0f) hits += l to v
        }
        val best = hits.maxByOrNull { it.second }
        val excerpt = if (best != null) "${best.first}: ${best.second}"
        else out.lineSequence().filter { it.contains("layerName") || it.contains("averageFPS") }.take(6).joinToString("\n")
            .ifBlank { out.take(200).ifBlank { "No output." } }
        return FpsReading(best?.second?.let { Math.round(it) }, excerpt)
    }
}
