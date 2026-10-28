package dev.pixeltune

import android.os.Build

data class DeviceInfo(
    val ok: Boolean,                 // false = the scan itself failed; everything stays blocked
    val vendor: String, val model: String, val sdk: Int,
    val rom: String,                 // informational only, never used for gating
    val writable: Boolean,           // can we really write and read back a setting through Shizuku?
    val needs: Map<String, Boolean>, // result of every "needs" probe used by any tweak
    val hint: String,                // what to do if writable == false
)

/**
 * One Shizuku call that looks at the device, so tweaks can be skipped instead of blindly applied.
 * "needs" probes in packs.json: pkg:<package>, prop:<name> (non-empty), setting:<ns>:<key> (not "null").
 */
object DeviceScan {
    private val SAFE = Regex("[A-Za-z0-9._:-]+")

    private fun probeCmd(n: String): String? {
        if (!SAFE.matches(n)) return null
        val p = n.split(":")
        return when {
            p[0] == "pkg" && p.size == 2 -> "pm path ${p[1]} >/dev/null 2>&1 && echo 1 || echo 0"
            p[0] == "prop" && p.size == 2 -> "[ -n \"\$(getprop ${p[1]})\" ] && echo 1 || echo 0"
            p[0] == "setting" && p.size == 3 ->
                "[ \"\$(settings get ${p[1]} ${p[2]} 2>/dev/null)\" != \"null\" ] && echo 1 || echo 0"
            else -> null
        }
    }

    fun hintFor(vendor: String) = when (vendor) {
        "xiaomi" -> "Open Developer options and turn on “USB debugging (Security settings)”, then restart Shizuku."
        "oplus" -> "Open Developer options. On ColorOS 15 and older, turn off “Permission monitoring”. " +
            "On ColorOS 16 and newer, turn on “Disable system optimization” (it may only appear with the system language set to English). " +
            "Labels on realme UI are unverified. Then restart Shizuku."
        "vivo" -> "Restart Shizuku (Wireless debugging) and grant access again. If writes stay blocked, look in Developer options for a security or permission-monitoring switch. Labels on Funtouch OS / OriginOS are unverified."
        "samsung" -> "Restart Shizuku (Wireless debugging) and check that the grant is still active."
        else -> "Restart Shizuku and grant access again."
    }

    fun scan(all: List<Tweak>, vendor: String): DeviceInfo = runCatching {
        val needs = all.flatMap { it.needs }.distinct()
        val script = buildString {
            append("echo W=\$(settings put global pixeltune_probe 1 >/dev/null 2>&1; settings get global pixeltune_probe 2>/dev/null; settings delete global pixeltune_probe >/dev/null 2>&1)\n")
            append("echo P_ONEUI=\$(getprop ro.build.version.oneui)\n")
            append("echo P_HYPER=\$(getprop ro.mi.os.version.name)\n")
            append("echo P_MIUI=\$(getprop ro.miui.ui.version.name)\n")
            append("echo P_OPLUS=\$(getprop ro.build.version.oplusrom)\n")
            needs.forEachIndexed { i, n -> probeCmd(n)?.let { append("echo N$i=\$($it)\n") } }
        }
        val kv = Shell.run(script).lines().mapNotNull { l ->
            val i = l.indexOf('='); if (i > 0) l.substring(0, i) to l.substring(i + 1).trim() else null
        }.toMap()
        val rom = listOf("One UI" to "P_ONEUI", "HyperOS" to "P_HYPER", "MIUI" to "P_MIUI", "ColorOS/realme UI" to "P_OPLUS")
            .firstOrNull { !kv[it.second].isNullOrBlank() }?.let { "${it.first} ${kv[it.second]}" } ?: "stock"
        val found = needs.mapIndexed { i, n -> n to (kv["N$i"] == "1") }.toMap() // unparseable probes fail closed
        DeviceInfo(true, vendor, Build.MODEL, Build.VERSION.SDK_INT, rom, kv["W"] == "1", found, hintFor(vendor))
    }.getOrElse {
        DeviceInfo(false, vendor, Build.MODEL, Build.VERSION.SDK_INT, "unknown", false, emptyMap(), hintFor(vendor))
    }

    /** Checks that need no shell: vendor and API level. Used before the scan finishes and in AutoReset. */
    fun staticReason(t: Tweak, vendor: String, sdk: Int): String? = when {
        t.vendors.isNotEmpty() && vendor !in t.vendors -> "Only for ${t.vendors.joinToString("/")} devices"
        sdk < t.minSdk -> "Needs Android API ${t.minSdk} or newer"
        sdk > t.maxSdk -> "Not tested above API ${t.maxSdk}"
        else -> null
    }

    /** null = compatible. */
    fun reason(t: Tweak, d: DeviceInfo): String? {
        staticReason(t, d.vendor, d.sdk)?.let { return it }
        if (!d.ok) return "Device scan failed. Reopen the app."
        val miss = t.needs.filter { d.needs[it] != true }
        if (miss.isNotEmpty()) return "Not found on this device: ${miss.joinToString { it.substringAfter(':') }}"
        if (!d.writable && t.apply.any { it.startsWith("settings ") }) return "Setting writes are blocked (see the setup note)"
        return null
    }
}
