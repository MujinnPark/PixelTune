package dev.pixeltune

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(listener)
        val hz = runCatching {
            getSystemService(DisplayManager::class.java).getDisplay(0).supportedModes.maxOf { it.refreshRate }.toInt()
        }.getOrDefault(120)
        val prefs = getSharedPreferences("tweaks", MODE_PRIVATE)
        val actions = Tweaks.actions()
        val toggles = Tweaks.toggles(hz)
        val packs = Tweaks.packs(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(status.intValue, hz, actions, toggles, packs, prefs)
            }
        }
    }

    override fun onResume() { super.onResume(); status.intValue = Shell.status() }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(listener); super.onDestroy() }
}

// ---------- palette (light/dark) ----------
private class Pal(
    val bg: Color, val card: Color, val text: Color, val sub: Color, val line: Color,
    val blue: Color, val red: Color, val redBg: Color, val amber: Color, val navy: Color,
)
private val Light = Pal(Color(0xFFF4F5F7), Color.White, Color(0xFF14171F), Color(0xFF6B7280), Color(0xFFE6E8EC),
    Color(0xFF0A7BF0), Color(0xFFD92D20), Color(0xFFFDECEC), Color(0xFFF5B800), Color(0xFF1B1F2E))
private val Dark = Pal(Color(0xFF0F1117), Color(0xFF1A1D27), Color(0xFFF2F3F7), Color(0xFF9AA0AE), Color(0xFF2A2E3A),
    Color(0xFF3B93FF), Color(0xFFFF6B63), Color(0xFF3A1E21), Color(0xFFF5B800), Color(0xFF1B1F2E))
private val LocalPal = staticCompositionLocalOf { Light }
private val Green = Color(0xFF16A34A)

private data class Entry(val title: String, val time: Long, val level: Int, val label: String, val summary: String)

@Composable
fun App(status: Int, hz: Int, actions: List<Tweak>, toggles: List<Tweak>, packs: List<Tweak>, prefs: SharedPreferences) {
    CompositionLocalProvider(LocalPal provides (if (isSystemInDarkTheme()) Dark else Light)) {
        Body(status, hz, actions, toggles, packs, prefs)
    }
}

@Composable
private fun Body(status: Int, hz: Int, actions: List<Tweak>, toggles: List<Tweak>, packs: List<Tweak>, prefs: SharedPreferences) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Tweak?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var keepNotif by remember { mutableStateOf(prefs.getBoolean("keep_notif", true)) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val history = remember { mutableStateListOf<Entry>() }
    val on = remember {
        mutableStateMapOf<String, Boolean>().also { m -> (toggles + packs).forEach { m[it.id] = prefs.getBoolean(it.id, false) } }
    }
    val canRun = status == 2 && !busy
    val activeOn = on.values.count { it }
    val total = on.size
    val riskyOn = packs.count { it.risky && on[it.id] == true }
    val recommended = (toggles + packs).filter { it.id in setOf("anim", "settings_std", "device_config_std") }
    val recOn = recommended.all { on[it.id] == true }
    val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    suspend fun exec(t: Tweak, enable: Boolean) {
            val all = if (enable) t.apply else t.revert
            val skipped = if (enable && keepNotif) all.filter { Tweaks.touchesNotifications(it) } else emptyList()
            val cmds = all - skipped.toSet()
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
            val level = when {
                cmds.isNotEmpty() && r.failed >= cmds.size -> 2
                r.failed > 0 || r.other.any { Regex("(?i)exception|error|fail|denied|not found").containsMatchIn(it) } -> 1
                else -> 0
            }
            val label = when {
                level == 2 -> "Rejected"
                level == 1 -> "Needs attention"
                !enable -> "Reverted"
                t.action -> "Done"
                else -> "Applied"
            }
            val summary = buildString {
                append("${cmds.size} command(s) sent")
                if (skipped.isNotEmpty()) append(" · ${skipped.size} skipped for notifications")
                if (r.ok > 0) append(" · ${r.ok} packages OK")
                if (r.failed > 0) append(" · ${r.failed} rejected by Android" + if (r.ok == 0) " (${(cmds.size - r.failed).coerceAtLeast(0)} accepted)" else "")
                if (r.other.isNotEmpty()) append(" · ${r.other.size} other message(s)")
                r.other.take(3).forEach { append("\n$it") }
                append(if (saved != null) "\nLog: $saved" else "\nCould not save the log file.")
            }
            history.add(0, Entry(t.title, System.currentTimeMillis(), level, label, summary))
    }
    fun run(t: Tweak, enable: Boolean) {
        scope.launch { busy = true; exec(t, enable); busy = false }
    }
    fun runMany(list: List<Pair<Tweak, Boolean>>) {
        scope.launch { busy = true; list.forEach { (t, e) -> exec(t, e) }; busy = false }
    }
    fun isOpen(k: String, def: Boolean) = open[k] ?: def

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Header(status) { showSettings = true }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = p.blue, trackColor = p.line)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    if (status != 2) {
                        IssueCard(
                            if (status == 1) "Need attention" else "Very urgent",
                            if (status == 1) p.amber else p.red,
                            if (status == 1) p.amber.copy(alpha = .16f) else p.redBg, "Now",
                            if (status == 1) "Grant Shizuku access" else "Shizuku isn't running",
                            if (status == 1) "PixelTune needs permission to run commands." else "Start Shizuku (Wireless debugging), then reopen this app.",
                        )
                        if (status == 1) Button(
                            onClick = { runCatching { Shizuku.requestPermission(1) } },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                        ) { Text("Grant access") }
                    }
                    Panel {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Ring(if (total == 0) 0f else activeOn / total.toFloat(), "$activeOn/$total", "tweaks on")
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat("$hz Hz", "Display")
                                Stat(if (keepNotif) "On" else "Off", "Notif. guard")
                                Stat("$riskyOn", "Risky on")
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { runMany((toggles + packs).filter { on[it.id] == true }.map { it to false }) },
                            enabled = canRun && activeOn > 0, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, p.line), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                        ) { Text("Turn all off") }
                        Button(
                            onClick = { runMany(recommended.filter { on[it.id] != true }.map { it to true }) },
                            enabled = canRun && !recOn, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                        ) { Text(if (recOn) "Recommended on" else "Apply recommended") }
                    }
                    Text("Recommended = faster animations, the settings pack and the ads/hibernation/storage flags. 120 Hz and risky packs stay off.",
                        color = p.sub, fontSize = 12.sp)
                }
                1 -> Panel {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        actions.forEachIndexed { i, t ->
                            if (i > 0) Hairline()
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(t.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(t.desc, color = p.sub, fontSize = 13.sp)
                                    if (t.note.isNotBlank()) Text("Note: ${t.note}", color = p.sub, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { run(t, true) }, enabled = canRun, shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 18.dp),
                                ) { Text("Run") }
                            }
                        }
                    }
                }
                2 -> {
                    val std = packs.filter { !it.risky }
                    val risky = packs.filter { it.risky }
                    val onToggle = { t: Tweak, v: Boolean -> if (v && t.risky) confirm = t else run(t, v) }
                    Accordion("Display & motion", Icons.Filled.Refresh, isOpen("d", true), { open["d"] = !isOpen("d", true) },
                        badges = { CountChip(toggles.count { on[it.id] == true }, p.amber, Color.Black) }) {
                        toggles.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                    Accordion("Cleanup packs", Icons.Filled.Build, isOpen("c", false), { open["c"] = !isOpen("c", false) },
                        badges = { CountChip(std.count { on[it.id] == true }, p.amber, Color.Black) }) {
                        std.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                    Accordion("Risky packs", Icons.Filled.Warning, isOpen("r", false), { open["r"] = !isOpen("r", false) },
                        badges = { CountChip(riskyOn, p.red, Color.White) }) {
                        risky.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                }
                else -> {
                    if (history.isEmpty()) IssueCard("Nothing yet", p.sub, p.card, "", "No runs this session", "Run an action or switch a tweak on. Full logs are saved to Download/PixelTune.")
                    history.forEach { e ->
                        val mins = ((System.currentTimeMillis() - e.time) / 60000).toInt()
                        IssueCard(
                            e.label,
                            when (e.level) { 0 -> Green; 1 -> p.amber; else -> p.red },
                            when (e.level) { 0 -> p.card; 1 -> p.amber.copy(alpha = .16f); else -> p.redBg },
                            if (mins < 1) "Just now" else "$mins min ago", e.title, e.summary,
                        )
                    }
                    if (history.isNotEmpty()) OutlinedButton(
                        onClick = { history.clear() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, p.line), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                    ) { Text("Clear history") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomNav(tab) { tab = it }
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
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Keep notifications untouched", fontWeight = FontWeight.Medium)
                        Text("Skips tweaks that change notification features and standby limits.", fontSize = 13.sp)
                    }
                    Switch(checked = keepNotif, onCheckedChange = { keepNotif = it; prefs.edit().putBoolean("keep_notif", it).apply() })
                }
                Spacer(Modifier.height(12.dp))
                Text("Logs are saved to Download/PixelTune · v$ver", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
    )
}

// ---------- building blocks ----------
@Composable
private fun Header(status: Int, onGear: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(80.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PixelTune", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(when (status) { 2 -> Green; 1 -> p.amber; else -> p.red }))
                Spacer(Modifier.width(6.dp))
                Text(when (status) { 2 -> "Shizuku connected"; 1 -> "Needs permission"; else -> "Shizuku not running" }, color = p.sub, fontSize = 13.sp)
            }
        }
        IconButton(onClick = onGear) { Icon(Icons.Filled.Settings, "Settings", tint = p.sub) }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LocalPal.current.card), content = content)
}

@Composable
private fun Hairline() { Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.line)) }

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun CountChip(n: Int, bg: Color, fg: Color) { if (n > 0) Chip("$n on", bg, fg) }

@Composable
private fun Accordion(
    title: String, icon: ImageVector, expanded: Boolean, onToggle: () -> Unit,
    badges: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPal.current
    Panel {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = p.sub, modifier = Modifier.size(22.dp))
            Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            badges()
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = p.sub)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(t: Tweak, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (t.risky) { Spacer(Modifier.width(6.dp)); Chip("RISKY", p.redBg, p.red) }
            }
            Text(t.desc, color = p.sub, fontSize = 13.sp)
            if (t.note.isNotBlank()) Text("Note: ${t.note}", color = p.sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = p.blue, checkedThumbColor = Color.White))
    }
}

@Composable
private fun IssueCard(label: String, color: Color, bg: Color, time: String, title: String, body: String = "") {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(time, color = p.sub, fontSize = 12.sp)
        }
        Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        if (body.isNotBlank()) Text(body, color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val p = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.sub, fontSize = 12.sp)
    }
}

@Composable
private fun Ring(frac: Float, big: String, small: String) {
    val p = LocalPal.current
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 16.dp.toPx()
            val sz = Size(size.width - w, size.height - w)
            drawArc(color = p.line, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(color = p.blue, startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Text(small, fontSize = 13.sp, color = p.sub)
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    val items = listOf("Home" to Icons.Filled.Home, "Actions" to Icons.Filled.PlayArrow, "Tweaks" to Icons.Filled.Build, "Log" to Icons.Filled.Info)
    val dim = Color(0xFF6B7280)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(p.navy)
            .navigationBarsPadding().padding(top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val c = if (i == selected) Color.White else dim
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, tint = c, modifier = Modifier.size(26.dp))
                Text(label, fontSize = 11.sp, color = c)
                Box(Modifier.padding(top = 3.dp).size(18.dp, 3.dp).clip(RoundedCornerShape(50))
                    .background(if (i == selected) Color(0xFF3B93FF) else Color.Transparent))
            }
        }
    }
}
