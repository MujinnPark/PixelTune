package dev.pixeltune

import android.content.Context
import org.json.JSONArray

data class Tweak(
    val id: String, val title: String, val desc: String, val note: String = "",
    val apply: List<String>, val revert: List<String> = emptyList(),
    val risky: Boolean = false, val action: Boolean = false,
)

object Tweaks {
    fun actions() = listOf(
        Tweak("aot", "Compile apps (speed-profile)", "Gets apps ready ahead of time so they open faster.",
            "Takes a few minutes and uses some battery while it runs.",
            listOf("cmd package compile -m speed-profile -a"), action = true),
        Tweak("bgdex", "Run background dexopt now", "Starts the system's tidy-up job now instead of waiting for night.",
            "Runs in the background for a while.",
            listOf("cmd package bg-dexopt-job"), action = true),
        Tweak("trim", "Trim app caches", "Deletes apps' temporary files to free space.",
            "Apps may open slightly slower the first time.",
            listOf("pm trim-caches 999G"), action = true),
        Tweak("kill", "Clear background apps", "Closes apps running in the background.",
            "Frees RAM briefly; apps reload when you open them.",
            listOf("am kill-all"), action = true),
        Tweak("reset", "Undo compilation", "Puts app optimization back to the default.",
            "Use this to undo \"Compile apps\".",
            listOf("cmd package compile --reset -a"), action = true),
    )

    fun toggles(hz: Int) = listOf(
        Tweak("anim", "Faster animations (0.5×)", "Menus and transitions play twice as fast.",
            "No battery cost. It just feels quicker.",
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { "settings put global $it 0.5" },
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { "settings put global $it 1.0" }),
        Tweak("hz", "Lock $hz Hz", "Keeps the screen at its smoothest ($hz Hz) all the time.",
            "Uses more battery.",
            listOf("settings put system peak_refresh_rate $hz.0", "settings put system min_refresh_rate $hz.0"),
            listOf("settings delete system peak_refresh_rate", "settings delete system min_refresh_rate")),
    )

    fun packs(c: Context): List<Tweak> {
        val a = JSONArray(c.assets.open("packs.json").bufferedReader().readText())
        fun JSONArray.strs() = List(length()) { getString(it) }
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Tweak(o.getString("id"), o.getString("title"), o.getString("desc"), o.optString("note"),
                o.getJSONArray("apply").strs(), o.getJSONArray("revert").strs(), o.getBoolean("risky"))
        }
    }
}
