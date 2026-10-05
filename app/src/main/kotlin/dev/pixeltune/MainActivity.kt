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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val on = remember {
        mutableStateMapOf<String, Boolean>().also { m -> (toggles + packs).forEach { m[it.id] = prefs.getBoolean(it.id, false) } }
    }
    val canRun = status == 2 && !busy

    fun run(t: Tweak, enable: Boolean) {
        scope.launch {
            busy = true
            val cmds = if (enable) t.apply else t.revert
            val (n, out) = withContext(Dispatchers.IO) {
                runCatching { Shell.runAll(cmds) }.getOrElse { 0 to "Failed: ${it.message}" }
            }
            if (!t.action) { on[t.id] = enable; prefs.edit().putBoolean(t.id, enable).apply() }
            log = "${t.title}: ${if (enable) "applied" else "reverted"} (${cmds.size} commands, $n message lines)\n$out"
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
