package dev.pixeltune

data class VerifyResult(val total: Int, val matched: Int, val bad: List<String>)

/** Reads values back from Android to confirm a tweak is actually applied (not that it speeds anything up). */
object Verify {
    private fun probe(cmd: String): Triple<String, String, String>? {
        val p = cmd.trim().split(Regex("\\s+"))
        return when {
            p.size >= 5 && p[0] == "settings" && p[1] == "put" -> Triple("settings get ${p[2]} ${p[3]}", p[4], p[3])
            p.size >= 5 && p[0] == "device_config" && p[1] == "put" -> Triple("device_config get ${p[2]} ${p[3]}", p[4], p[3])
            p.size >= 3 && p[0] == "setprop" -> Triple("getprop ${p[1]}", p[2], p[1])
            else -> null
        }
    }

    fun check(cmds: List<String>): VerifyResult {
        val probes = cmds.mapNotNull { probe(it) }
        var matched = 0
        val bad = mutableListOf<String>()
        for (batch in probes.chunked(40)) {
            val script = batch.mapIndexed { i, pr -> "echo \"R$i=\$(${pr.first} 2>&1 | head -n 1)\"" }.joinToString("\n")
            val out = Shell.run(script).lines()
            batch.forEachIndexed { i, (_, want, label) ->
                val got = out.firstOrNull { it.startsWith("R$i=") }?.substringAfter("=")?.trim() ?: "?"
                if (got.equals(want, ignoreCase = true)) matched++ else bad += "$label: wanted $want, got ${if (got == "null" || got.isEmpty()) "not set" else got}"
            }
        }
        return VerifyResult(probes.size, matched, bad)
    }

    /** e.g. "speed-profile (cmdline)" from dumpsys package. */
    fun compileStatus(pkg: String): String {
        val out = Shell.run("dumpsys package $pkg | grep -E '\\[status=' | head -n 1")
        val st = Regex("status=([\\w-]+)").find(out)?.groupValues?.get(1) ?: return "unknown"
        val rs = Regex("reason=([\\w-]+)").find(out)?.groupValues?.get(1)
        return if (rs == null) st else "$st ($rs)"
    }
}
