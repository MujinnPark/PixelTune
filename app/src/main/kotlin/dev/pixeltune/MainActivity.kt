package dev.pixeltune

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val status = mutableIntStateOf(0)
    private val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> status.intValue = Shell.status() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(listener)
        val hz = runCatching {
            getSystemService(DisplayManager::class.java).getDisplay(0).supportedModes.maxOf { it.refreshRate }.toInt()
        }.getOrDefault(120)
        val prefs = getSharedPreferences("tweaks", MODE_PRIVATE)
        val packs = Tweaks.packs(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) {
                    App(status.intValue, Tweaks.actions(), Tweaks.toggles(hz), packs, prefs)
                }
            }
        }
    }

    override fun onResume() { super.onResume(); status.intValue = Shell.status() }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(listener); super.onDestroy() }
}

@Composable
fun App(status: Int, actions: List<Tweak>, toggles: List<Tweak>, packs: List<Tweak>, prefs: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Nothing run yet.") }
    var confirm by remember { mutableStateOf<Tweak?>(null) }
    val ctx = LocalContext.current
    var keepNotif by remember { mutableStateOf(prefs.getBoolean("keep_notif", true)) }
    val on = remember {
        mutableStateMapOf<String, Boolean>().also { m -> (toggles + packs).forEach { m[it.id] = prefs.getBoolean(it.id, false) } }
    }
    val canRun = status == 2 && !busy

    fun run(t: Tweak, enable: Boolean) {
        scope.launch {
            busy = true
            val all = if (enable) t.apply else t.revert
            val skipped = if (enable && keepNotif) all.filter { Tweaks.touchesNotifications(it) } else emptyList()
            val cmds = all - skipped.toSet()
            log = "Running ${t.title}... this can take a few minutes."
            val r = withContext(Dispatchers.IO) {
                runCatching { Shell.runAll(cmds) }.getOrElse { ShellResult(0, 0, listOf("Failed: ${it.message}"), "") }
            }
            if (!t.action) {
                val applied = enable && r.failed < cmds.size
                on[t.id] = applied; prefs.edit().putBoolean(t.id, applied).apply()
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val saved = withContext(Dispatchers.IO) {
                LogStore.save(ctx, "pixeltune_${t.id}_$stamp.txt", buildString {
                    append("PixelTune log: ${t.title} (${if (enable) "apply" else "revert"}) $stamp\n")
                    append("Sent ${cmds.size} command(s), skipped ${skipped.size} (notification guard)\n\n")
                    if (skipped.isNotEmpty()) { append("Skipped:\n"); skipped.forEach { append(it).append('\n') }; append('\n') }
                    append("Commands:\n"); cmds.forEach { append(it).append('\n') }
                    append("\nFull output:\n").append(r.raw)
                })
            }
            log = buildString {
                append("${t.title}: ${if (enable) "applied" else "reverted"}\n")
                append("${cmds.size} command(s) sent")
                if (skipped.isNotEmpty()) append(" · ${skipped.size} skipped to keep notifications")
                if (r.ok > 0) append(" · ${r.ok} packages OK")
                if (r.failed > 0) append(" · ${r.failed} rejected by Android" + if (r.ok == 0) " (${(cmds.size - r.failed).coerceAtLeast(0)} accepted)" else "")
                append(" · ${r.other.size} other message(s)")
                r.other.take(10).forEach { append("\n$it") }
                if (r.other.size > 10) append("\n...and ${r.other.size - 10} more")
                append(if (saved != null) "\nFull log saved: $saved" else "\nCould not save the log file.")
            }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        item {
            Text("PixelTune", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(when (status) {
                        0 -> "Shizuku isn't running. Start it (Wireless debugging), then reopen this app."
                        1 -> "Shizuku is running. Grant PixelTune access."
                        else -> "Connected via Shizuku."
                    })
                    if (status == 1) Button(onClick = { runCatching { Shizuku.requestPermission(1) } }, modifier = Modifier.padding(top = 8.dp)) { Text("Grant access") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep notifications untouched", style = MaterialTheme.typography.titleMedium)
                    Text("Skips tweaks that change notification features (history, smart notifications, message warnings) and standby limits.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = keepNotif, onCheckedChange = { keepNotif = it; prefs.edit().putBoolean("keep_notif", it).apply() })
            }
            Text("Actions", style = MaterialTheme.typography.titleLarge)
        }
        items(actions, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(t.title, style = MaterialTheme.typography.titleMedium); Text(t.desc, style = MaterialTheme.typography.bodySmall); NoteText(t) }
                FilledTonalButton(onClick = { run(t, true) }, enabled = canRun) { Text("Run") }
            }
        }
        item { Text("Tweaks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp)) }
        items(toggles + packs, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        if (t.risky) Text("  RISKY", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(t.desc, style = MaterialTheme.typography.bodySmall); NoteText(t)
                }
                Switch(checked = on[t.id] == true, enabled = canRun, onCheckedChange = { v ->
                    if (v && t.risky) confirm = t else run(t, v)
                })
            }
        }
        item {
            Text("Log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SelectionContainer { Text(log, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirm?.let { t ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Apply risky pack?") },
            text = { Text("${t.desc}\n\nNote: ${t.note}\n\nYou can switch it off any time to revert.") },
            confirmButton = { TextButton(onClick = { confirm = null; run(t, true) }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NoteText(t: Tweak) {
    if (t.note.isNotBlank()) Text("Note: ${t.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
