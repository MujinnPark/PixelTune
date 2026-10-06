package dev.pixeltune

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands through Shizuku (adb-level, no root). */
object Shell {
    /** 0 = Shizuku not running, 1 = running but not granted, 2 = ready */
    fun status(): Int = try {
        if (!Shizuku.pingBinder()) 0
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) 2 else 1
    } catch (_: Throwable) { 0 }

    // Shizuku 13 made newProcess private, so call it reflectively.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    fun run(script: String): String {
        val p = newProcess.invoke(null, arrayOf("sh", "-c", "( $script ) 2>&1"), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }

    /** Runs commands in batches. Lines ending in "Success" count as OK; anything else is a message. */
    fun runAll(cmds: List<String>, chunk: Int = 60): ShellResult {
        var ok = 0
        val other = mutableListOf<String>()
        val raw = StringBuilder()
        for (batch in cmds.chunked(chunk)) {
            val out = run(batch.joinToString("\n"))
            raw.append(out).append('\n')
            for (line in out.lines()) {
                when {
                    line.isBlank() -> {}
                    line.trimEnd().endsWith("Success") -> ok++
                    else -> other += line.take(200)
                }
            }
        }
        return ShellResult(ok, other, raw.toString())
    }
}

data class ShellResult(val ok: Int, val other: List<String>, val raw: String)
