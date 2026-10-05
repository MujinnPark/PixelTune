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

    /** Runs commands in batches; returns (message line count, trimmed output). */
    fun runAll(cmds: List<String>, chunk: Int = 60): Pair<Int, String> {
        var n = 0
        val sb = StringBuilder()
        for (batch in cmds.chunked(chunk)) {
            val o = run(batch.joinToString("\n"))
            if (o.isNotBlank()) { n += o.lines().size; sb.appendLine(o.take(300)) }
        }
        return n to sb.toString().take(2000)
    }
}
