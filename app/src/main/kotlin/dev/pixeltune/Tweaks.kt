package dev.pixeltune

import android.content.Context
import org.json.JSONArray

data class Tweak(
    val id: String, val title: String, val desc: String, val note: String = "",
    val apply: List<String>, val revert: List<String> = emptyList(),
    val risky: Boolean = false, val action: Boolean = false,
)

object Tweaks {
    private val GUARD = Regex("(?i)notif|push_messaging|messagearmour|app_standby|background_(check|execution|restrictions)|app_restriction|restricted_device")
    /** True for commands that touch notification features or background limits. */
    fun touchesNotifications(cmd: String) = GUARD.containsMatchIn(cmd)

    fun actions() = listOf(
        Tweak("aot", "Compile apps (speed-profile)", "Pre-compiles apps so they open faster. Skips apps that are already optimized.",
            "Measured about 17% faster cold start in WhatsApp. Re-run after big app updates.",
            listOf("cmd package compile -m speed-profile -a"), action = true),
        Tweak("bgdex", "Run background dexopt now", "Starts the system's tidy-up job now instead of waiting for night.",
            "Runs in the background for a while.",
            listOf("cmd package bg-dexopt-job"), action = true),
        Tweak("trim", "Trim app caches", "Deletes apps' temporary files to free space.",
            "Apps may open slightly slower the first time.",
            listOf("pm trim-caches 999G"), action = true),
        Tweak("maint", "Run storage maintenance", "Trims flash storage and runs the system's idle cleanup now.",
            "Takes a minute and warms the phone. Best while cool or charging.",
            listOf("sm idle-maint run"), action = true),
        Tweak("kill", "Clear background apps", "Closes apps running in the background.",
            "Frees RAM briefly; apps reload when you open them. If notifications act up, reboot.",
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
            "Uses more battery. No smoothness gain showed up in tests.",
            listOf("settings put system peak_refresh_rate $hz.0", "settings put system min_refresh_rate $hz.0"),
            listOf("settings delete system peak_refresh_rate", "settings delete system min_refresh_rate")),
        Tweak("blur", "Disable window blur", "Turns off the blur behind the notification shade and system panels.",
            "Less GPU work, flatter look. Not measured. Pull down the shade to see if it worked.",
            listOf("settings put global disable_window_blurs 1"), listOf("settings delete global disable_window_blurs")),
        Tweak("scan", "Stop background scanning", "Stops Wi-Fi and Bluetooth scanning while those radios are off.",
            "Tiny battery saving. Can make location and Find My Device less accurate.",
            listOf("settings put global wifi_scan_always_enabled 0", "settings put global ble_scan_always_enabled 0"),
            listOf("settings delete global wifi_scan_always_enabled", "settings delete global ble_scan_always_enabled")),
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
