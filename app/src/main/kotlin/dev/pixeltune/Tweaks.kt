package dev.pixeltune

import android.content.Context
import org.json.JSONArray

data class Tweak(
    val id: String, val title: String, val desc: String,
    val apply: List<String>, val revert: List<String> = emptyList(),
    val risky: Boolean = false, val action: Boolean = false,
)

object Tweaks {
    fun actions() = listOf(
        Tweak("aot", "Compile apps (speed-profile)", "Pre-compiles hot code paths. Faster launches, takes a few minutes.",
            listOf("cmd package compile -m speed-profile -a"), action = true),
        Tweak("bgdex", "Run background dexopt now", "Triggers the system's idle optimizer immediately.",
            listOf("cmd package bg-dexopt-job"), action = true),
        Tweak("trim", "Trim app caches", "Frees cache space. Apps may cold-start slower once.",
            listOf("pm trim-caches 999G"), action = true),
        Tweak("kill", "Clear background apps", "Kills cached processes. Brief boost, then apps reload.",
            listOf("am kill-all"), action = true),
        Tweak("reset", "Undo compilation", "Resets all apps to the default compile state.",
            listOf("cmd package compile --reset -a"), action = true),
    )

    fun toggles(hz: Int) = listOf(
        Tweak("anim", "Faster animations (0.5×)", "Window, transition and animator scale.",
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { "settings put global $it 0.5" },
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { "settings put global $it 1.0" }),
        Tweak("hz", "Lock $hz Hz", "Forces peak and minimum refresh rate. Costs battery.",
            listOf("settings put system peak_refresh_rate $hz.0", "settings put system min_refresh_rate $hz.0"),
            listOf("settings delete system peak_refresh_rate", "settings delete system min_refresh_rate")),
    )

    fun packs(c: Context): List<Tweak> {
        val a = JSONArray(c.assets.open("packs.json").bufferedReader().readText())
        fun JSONArray.strs() = List(length()) { getString(it) }
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Tweak(o.getString("id"), o.getString("title"), o.getString("desc"),
                o.getJSONArray("apply").strs(), o.getJSONArray("revert").strs(), o.getBoolean("risky"))
        }
    }
}
