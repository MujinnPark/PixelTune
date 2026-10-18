package dev.pixeltune

import android.content.SharedPreferences

data class VerifyResult(val total: Int, val stuck: Int, val bad: List<String>)

/** Reads whole settings namespaces with one shell call. Keys come back as "ns:key". */
object SettingsReader {
    fun read(namespaces: Collection<String>): Map<String, String> {
        val ns = namespaces.filter { it == "global" || it == "secure" || it == "system" }.distinct()
        if (ns.isEmpty()) return emptyMap()
        val out = Shell.run(ns.joinToString("; ") { "echo @@$it; settings list $it" })
        val map = HashMap<String, String>()
        var cur = ""
        for (line in out.lines()) {
            if (line.startsWith("@@")) { cur = line.substring(2); continue }
            val i = line.indexOf('=')
            if (cur.isNotEmpty() && i > 0) map["$cur:${line.substring(0, i)}"] = line.substring(i + 1)
        }
        return map
    }
}

/**
 * Remembers what a settings key held before a tweak touched it, so revert restores the OEM value
 * instead of deleting it. Tweaks without a snapshot fall back to their own revert list.
 */
object Snapshot {
    private val PUT = Regex("^settings (?:put|delete) (global|secure|system) ([A-Za-z0-9._-]+)")

    fun keysOf(cmds: List<String>): List<Pair<String, String>> =
        cmds.mapNotNull { PUT.find(it)?.destructured?.let { (ns, k) -> ns to k } }.distinct()

    private fun key(t: Tweak, p: Pair<String, String>) = "snap:${t.id}:${p.first}:${p.second}"

    fun has(prefs: SharedPreferences, t: Tweak) = keysOf(t.apply).any { prefs.contains(key(t, it)) }

    fun capture(prefs: SharedPreferences, t: Tweak) {
        val keys = keysOf(t.apply).filter { !prefs.contains(key(t, it)) }
        if (keys.isEmpty()) return
        val cur = SettingsReader.read(keys.map { it.first })
        if (cur.isEmpty()) return // couldn't read anything: don't record "null" for keys we never saw
        val e = prefs.edit()
        keys.forEach { e.putString(key(t, it), cur["${it.first}:${it.second}"] ?: "null") }
        e.apply()
    }

    fun revertCmds(prefs: SharedPreferences, t: Tweak): List<String> {
        val snapped = keysOf(t.apply).filter { prefs.contains(key(t, it)) }
        if (snapped.isEmpty()) return t.revert
        val keep = t.revert.filter { c ->
            snapped.none { (ns, k) -> Regex("^settings (put|delete) $ns ${Regex.escape(k)}( |$)").containsMatchIn(c) }
        }
        return keep + snapped.map { p ->
            val v = prefs.getString(key(t, p), "null") ?: "null"
            if (v == "null") "settings delete ${p.first} ${p.second}"
            else "settings put ${p.first} ${p.second} '${v.replace("'", "'\\''")}'"
        }
    }

    fun clear(prefs: SharedPreferences, t: Tweak) {
        val e = prefs.edit()
        keysOf(t.apply).forEach { e.remove(key(t, it)) }
        e.apply()
    }
}

/** After an apply, reads the settings back to see which writes actually stuck. */
object Verify {
    private val PUT = Regex("^settings put (global|secure|system) ([A-Za-z0-9._-]+) (.+)$")

    fun settings(cmds: List<String>): VerifyResult {
        val want = LinkedHashMap<String, String>()
        cmds.forEach { c -> PUT.find(c)?.destructured?.let { (ns, k, v) -> want["$ns:$k"] = v.trim().trim('"', '\'') } }
        if (want.isEmpty()) return VerifyResult(0, 0, emptyList())
        val cur = SettingsReader.read(want.keys.map { it.substringBefore(':') })
        if (cur.isEmpty()) return VerifyResult(0, 0, emptyList()) // can't read back: treat as unverifiable
        val bad = want.filter { (k, v) -> cur[k] != v }.map { (k, v) -> "$k (wanted $v, got ${cur[k] ?: "null"})" }
        return VerifyResult(want.size, want.size - bad.size, bad)
    }
}
