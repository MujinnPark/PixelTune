package dev.pixeltune

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val bgTop: Color, val bgBottom: Color, val blobs: List<Color>,
    val glass: Color, val glassHi: Color, val edgeHi: Color, val edgeLo: Color,
    val card: Color, val text: Color, val sub: Color, val line: Color,
    val blue: Color, val red: Color, val redBg: Color, val amber: Color, val navy: Color, val scrim: Color,
)
private val Light = Pal(
    bgTop = Color(0xFFE6EDFF), bgBottom = Color(0xFFFFF0E4),
    blobs = listOf(Color(0xFF5B8CFF).copy(alpha = .85f), Color(0xFFB07CFF).copy(alpha = .70f), Color(0xFF4FE3C8).copy(alpha = .60f), Color(0xFFFFA45C).copy(alpha = .65f), Color(0xFFFF7EB6).copy(alpha = .60f)),
    glass = Color.White.copy(alpha = .30f), glassHi = Color.White.copy(alpha = .55f),
    edgeHi = Color.White.copy(alpha = .95f), edgeLo = Color.White.copy(alpha = .35f),
    card = Color.Transparent, text = Color(0xFF14172B), sub = Color(0xFF4B5575), line = Color(0xFF14172B).copy(alpha = .10f),
    blue = Color(0xFF2F6BFF), red = Color(0xFFD92D20), redBg = Color(0xFFD92D20).copy(alpha = .14f),
    amber = Color(0xFFF5B800), navy = Color(0xFF0B1020).copy(alpha = .70f), scrim = Color.Transparent,
)
private val Dark = Pal(
    bgTop = Color(0xFF0B1026), bgBottom = Color(0xFF0A0E1C),
    blobs = listOf(Color(0xFF3B6BFF).copy(alpha = .80f), Color(0xFF8B5CF6).copy(alpha = .70f), Color(0xFF14B8A6).copy(alpha = .55f), Color(0xFFFF8A4C).copy(alpha = .50f), Color(0xFFEC4899).copy(alpha = .50f)),
    glass = Color.White.copy(alpha = .05f), glassHi = Color.White.copy(alpha = .12f),
    edgeHi = Color.White.copy(alpha = .40f), edgeLo = Color.White.copy(alpha = .06f),
    card = Color.Transparent, text = Color(0xFFF4F6FF), sub = Color(0xFFB4BBD4), line = Color.White.copy(alpha = .14f),
    blue = Color(0xFF5AA2FF), red = Color(0xFFFF6B63), redBg = Color(0xFFFF6B63).copy(alpha = .18f),
    amber = Color(0xFFF5B800), navy = Color(0xFF0B1020).copy(alpha = .62f), scrim = Color.Black.copy(alpha = .16f),
)
private val LocalPal = staticCompositionLocalOf { Light }
private class Fx(val t: State<Float>, val size: State<IntSize>)
private val LocalFx = staticCompositionLocalOf<Fx?> { null }
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
    var liquid by remember { mutableStateOf(prefs.getBoolean("fx_anim", true)) }
    val tState: State<Float> = if (liquid) {
        rememberInfiniteTransition(label = "liquid").animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "t")
    } else remember { mutableStateOf(.15f) }
    val sizeState = remember { mutableStateOf(IntSize.Zero) }
    val fx = remember(liquid) { Fx(tState, sizeState) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val history = remember { mutableStateListOf<Entry>() }
    val on = remember {
        mutableStateMapOf<String, Boolean>().also { m -> (toggles + packs).forEach { m[it.id] = prefs.getBoolean(it.id, false) } }
    }
    val canRun = status == 2 && !busy
    val activeOn = on.values.count { it }
    val total = on.size
    val riskyOn = packs.count { it.risky && on[it.id] == true }
    val animTweak = toggles.firstOrNull { it.id == "anim" }
    val aotAction = actions.firstOrNull { it.id == "aot" }
    var statsTick by remember { mutableIntStateOf(0) }
    var benching by remember { mutableStateOf(false) }
    var benchMsg by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var benchPkg by remember { mutableStateOf(prefs.getString("bench_pkg", "com.android.settings") ?: "com.android.settings") }
    var benchLabel by remember { mutableStateOf(prefs.getString("bench_label", "Settings") ?: "Settings") }
    val results = remember { Bench.load(prefs).toMutableStateList() }
    val apps by produceState(emptyList<Pair<String, String>>()) { value = withContext(Dispatchers.IO) { Bench.launchableApps(ctx) } }
    val userPkgs by produceState(emptySet<String>()) { value = withContext(Dispatchers.IO) { Bench.userPackages(ctx) } }
    val sel = remember { (prefs.getString("aot_sel", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableStateList() }
    var showSelPicker by remember { mutableStateOf(false) }
    var selQuery by remember { mutableStateOf("") }
    var userOnly by remember { mutableStateOf(true) }
    fun saveSel() { prefs.edit().putString("aot_sel", sel.joinToString(",")).apply() }
    fun selTweak() = Tweak(
        id = "aotsel", title = "Compile selected apps", desc = "",
        apply = sel.map { "cmd package compile -m speed-profile $it" },
        revert = sel.map { "cmd package compile --reset $it" }, action = true,
    )
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
            val fails = if (enable) r.failed else 0 // reverting flags that were never set is expected to be rejected
            val level = when {
                cmds.isNotEmpty() && fails >= cmds.size -> 2
                fails > 0 || r.other.any { Regex("(?i)exception|error|fail|denied|not found").containsMatchIn(it) } -> 1
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
                if (r.failed > 0) append(
                    if (enable) " · ${r.failed} rejected by Android" + (if (r.ok == 0) " (${(cmds.size - r.failed).coerceAtLeast(0)} accepted)" else "")
                    else " · ${r.failed} were never set, nothing to remove"
                )
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
    fun runBench() {
        scope.launch {
            busy = true; benching = true; benchMsg = "Starting..."
            val dm = ctx.resources.displayMetrics
            val comp = ctx.packageManager.getLaunchIntentForPackage(benchPkg)?.component?.flattenToShortString()
            val r = withContext(Dispatchers.IO) {
                if (comp == null) Result.failure<BenchResult>(IllegalStateException("$benchLabel has no launcher activity"))
                else Bench.run(benchPkg, benchLabel, comp, ctx.packageName, activeOn, dm.widthPixels, dm.heightPixels) { benchMsg = it }
            }
            r.onSuccess {
                results.add(0, it); Bench.save(prefs, results)
                benchMsg = if (it.frames < 50) "Only ${it.frames} frames rendered. Pick a scrollable app (Settings, Chrome)." else "Done."
            }.onFailure { benchMsg = "Failed: ${it.message}" }
            benching = false; busy = false
        }
    }
    fun runVerify() {
        scope.launch {
            busy = true
            val names = apps.associate { it.second to it.first }
            val onTweaks = (toggles + packs).filter { on[it.id] == true }
            val chosen = sel.toList()
            val guard = keepNotif
            val report = withContext(Dispatchers.IO) {
                val lines = mutableListOf<String>()
                val full = StringBuilder()
                var total = 0
                var matched = 0
                onTweaks.forEach { t ->
                    val r = Verify.check(t.apply.filter { !(guard && Tweaks.touchesNotifications(it)) })
                    total += r.total; matched += r.matched
                    lines += "${t.title}: ${r.matched}/${r.total} match"
                    r.bad.take(3).forEach { lines += "  - $it" }
                    r.bad.forEach { full.append(t.title).append(": ").append(it).append('\n') }
                }
                chosen.forEach { pk -> lines += "${names[pk] ?: pk}: compiled ${Verify.compileStatus(pk)}" }
                Triple(lines, total to matched, full.toString())
            }
            val (lines, tm, full) = report
            val (total, matched) = tm
            val level = when { total == 0 || matched == total -> 0; matched == 0 -> 2; else -> 1 }
            val label = when {
                onTweaks.isEmpty() && chosen.isEmpty() -> "Nothing to verify"
                level == 0 -> "Verified"
                level == 2 -> "Not applied"
                else -> "Partly applied"
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val saved = withContext(Dispatchers.IO) {
                LogStore.save(ctx, "pixeltune_verify_$stamp.txt", lines.joinToString("\n") + "\n\nAll differences:\n" + full)
            }
            history.add(0, Entry(
                "Verify applied tweaks", System.currentTimeMillis(), level, label,
                (if (lines.isEmpty()) "Switch a tweak on or choose apps to compile first." else lines.joinToString("\n")) +
                    (if (saved != null) "\nLog: $saved" else ""),
            ))
            tab = 4
            busy = false
        }
    }
    fun isOpen(k: String, def: Boolean) = open[k] ?: def

    CompositionLocalProvider(LocalFx provides fx) {
    Box(Modifier.fillMaxSize().onSizeChanged { sizeState.value = it }.drawBehind { drawScene(p, tState.value, size.width, size.height) }) {
    Column(Modifier.fillMaxSize()) {
        Header(status) { showSettings = true }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = p.blue, trackColor = p.line)
        AnimatedContent(
            targetState = tab, modifier = Modifier.weight(1f), label = "tab",
            transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 20 }) togetherWith fadeOut(tween(120)) },
        ) { cur ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (cur) {
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
                    val tick = statsTick
                    val am = ctx.getSystemService(ActivityManager::class.java)
                    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                    val sf = StatFs(Environment.getDataDirectory().path)
                    val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val tempC = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
                    val lvl = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    Panel {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Phone status", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { statsTick = tick + 1 }) { Icon(Icons.Filled.Refresh, "Refresh", tint = p.sub) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Stat(Formatter.formatShortFileSize(ctx, mi.availMem), "RAM free")
                                Stat(Formatter.formatShortFileSize(ctx, sf.availableBytes), "Storage free")
                                Stat(String.format("%.1f°C", tempC), "Battery $lvl%")
                            }
                        }
                    }
                }
                1 -> {
                Panel {
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
                Panel {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Compile selected apps", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Compiles only the apps you pick. Much faster than every package, and compile is the one tweak that measurably sped up launches. Re-run after those apps update.",
                            color = p.sub, fontSize = 13.sp)
                        val names = apps.associate { it.second to it.first }
                        Text(if (sel.isEmpty()) "No apps chosen yet." else sel.joinToString(", ") { names[it] ?: it }, color = p.text, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showSelPicker = true }, enabled = canRun, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Brush.linearGradient(listOf(p.edgeHi, p.edgeLo))), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                            ) { Text("Choose apps") }
                            Button(
                                onClick = { run(selTweak(), true) }, enabled = canRun && sel.isNotEmpty(), modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                            ) { Text("Compile") }
                        }
                        if (sel.isNotEmpty()) TextButton(onClick = { run(selTweak(), false) }, enabled = canRun) { Text("Reset these apps", color = p.sub) }
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
                    Accordion("Cleanup & privacy packs", Icons.Filled.Build, isOpen("c", false), { open["c"] = !isOpen("c", false) },
                        badges = { CountChip(std.count { on[it.id] == true }, p.amber, Color.Black) }) {
                        std.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                    Accordion("Risky packs", Icons.Filled.Warning, isOpen("r", false), { open["r"] = !isOpen("r", false) },
                        badges = { CountChip(riskyOn, p.red, Color.White) }) {
                        risky.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                }
                3 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { runMany((toggles + packs).filter { on[it.id] == true }.map { it to false }) },
                            enabled = canRun && activeOn > 0, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Brush.linearGradient(listOf(p.edgeHi, p.edgeLo))), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                        ) { Text("Turn all off") }
                        Button(
                            onClick = { runMany(listOfNotNull(animTweak?.takeIf { on[it.id] != true }?.let { it to true }, aotAction?.let { it to true })) },
                            enabled = canRun, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                        ) { Text("Apply recommended") }
                    }
                    Text("Recommended = faster animations + Compile apps. Compile was the only tweak that measurably sped things up (about 17% faster cold start in WhatsApp). Re-run it after big app updates. Undo it from the Actions tab.",
                        color = p.sub, fontSize = 12.sp)
                    LaunchedEffect(Unit) { while (true) { delay(5000); statsTick++ } }
                    Panel {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Smoothness test", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Cold-starts the app 5 times, then scrolls it for about 20 seconds and counts dropped frames. " +
                                "Keep the screen on and don't touch the phone (about 50 s). It swipes on screen, so use a harmless scrollable app like Settings.",
                                color = p.sub, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("App: $benchLabel", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showPicker = true }, enabled = canRun) { Text("Change", color = p.blue) }
                            }
                            Button(
                                onClick = { runBench() }, enabled = canRun, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                            ) { Text(if (benching) benchMsg else "Run test") }
                            if (!benching && benchMsg.isNotBlank()) Text(benchMsg, color = p.sub, fontSize = 12.sp)
                        }
                    }
                    Panel {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Verify tweaks", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Reads each tweak's values back from Android and checks they match, and shows the compile state of the apps you chose. " +
                                "This proves a tweak is applied. It does not prove it makes anything faster; the smoothness test does that.",
                                color = p.sub, fontSize = 13.sp)
                            Button(
                                onClick = { runVerify() }, enabled = canRun, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.White),
                            ) { Text("Verify now") }
                        }
                    }
                    results.forEachIndexed { i, r ->
                        val prev = results.drop(i + 1).firstOrNull { it.pkg == r.pkg }
                        val dS = prev?.let { r.medianMs - it.medianMs } ?: 0
                        val dJ = prev?.let { r.jankPct - it.jankPct } ?: 0f
                        val gate = prev?.let { maxOf(it.medianMs * 0.10, 20.0) } ?: 0.0
                        val better = prev != null && (dS <= -gate || dJ <= -3f)
                        val worse = prev != null && (dS >= gate || dJ >= 3f)
                        val label = when {
                            prev == null -> "Baseline"
                            better && worse -> "Mixed"
                            better -> "Better"
                            worse -> "Worse"
                            else -> "No clear change"
                        }
                        val col = when (label) { "Better" -> Green; "Worse" -> p.red; "Mixed" -> p.amber; else -> p.sub }
                        val mins = ((System.currentTimeMillis() - r.ts) / 60000).toInt()
                        IssueCard(
                            label, col, if (label == "Worse") p.redBg else p.card, if (mins < 1) "Just now" else "$mins min ago",
                            "${r.label}: ${r.medianMs} ms start · ${"%.1f".format(r.jankPct)}% jank",
                            "${r.tweaksOn} tweaks on · start ${r.minMs}-${r.maxMs} ms · ${r.frames} frames · 90th pct ${r.p90} ms" +
                                (prev?.let { "\nvs previous: ${"%+d".format(dS)} ms start, ${"%+.1f".format(dJ)} pt jank" } ?: ""),
                        )
                    }
                    if (results.size >= 2) Text("Changes under about 10% (or 20 ms, or 3 points of jank) are treated as noise.", color = p.sub, fontSize = 12.sp)
                    if (results.isNotEmpty()) OutlinedButton(
                        onClick = { results.clear(); Bench.save(prefs, results) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Brush.linearGradient(listOf(p.edgeHi, p.edgeLo))), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                    ) { Text("Clear results") }
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
                        border = BorderStroke(1.dp, Brush.linearGradient(listOf(p.edgeHi, p.edgeLo))), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text),
                    ) { Text("Clear history") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        }
        BottomNav(tab) { tab = it }
    }
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
    if (showSelPicker) AlertDialog(
        onDismissRequest = { showSelPicker = false },
        title = { Text("Choose apps to compile") },
        text = {
            Column {
                OutlinedTextField(value = selQuery, onValueChange = { selQuery = it }, singleLine = true,
                    placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { userOnly = !userOnly }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = userOnly, onCheckedChange = { userOnly = it }, colors = CheckboxDefaults.colors(checkedColor = p.blue))
                    Text("User apps only", fontSize = 14.sp)
                }
                val shown = apps.filter { (l, pk) -> (!userOnly || pk in userPkgs) && l.contains(selQuery, ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.second }) { (label, pkg) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (pkg in sel) sel.remove(pkg) else sel.add(pkg); saveSel() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = pkg in sel, colors = CheckboxDefaults.colors(checkedColor = p.blue),
                                onCheckedChange = { if (it) { if (pkg !in sel) sel.add(pkg) } else sel.remove(pkg); saveSel() })
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSelPicker = false }) { Text("Done") } },
    )
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text("Choose app") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.second }) { (label, pkg) ->
                    Text(label, Modifier.fillMaxWidth().clickable {
                        benchPkg = pkg; benchLabel = label
                        prefs.edit().putString("bench_pkg", pkg).putString("bench_label", label).apply()
                        showPicker = false
                    }.padding(vertical = 12.dp), fontSize = 16.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } },
    )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Liquid animation", fontWeight = FontWeight.Medium)
                        Text("Animated glass background. Turn off to save battery and heat.", fontSize = 13.sp)
                    }
                    Switch(checked = liquid, onCheckedChange = { liquid = it; prefs.edit().putBoolean("fx_anim", it).apply() })
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
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulseA")
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(80.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PixelTune", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(when (status) { 2 -> Green; 1 -> p.amber; else -> p.red }))
                Spacer(Modifier.width(6.dp))
                Text(when (status) { 2 -> "Shizuku connected"; 1 -> "Needs permission"; else -> "Shizuku not running" }, color = p.sub, fontSize = 13.sp)
            }
        }
        IconButton(onClick = onGear) { Icon(Icons.Filled.Settings, "Settings", tint = p.sub) }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassBox(modifier, 26.dp, Color.Transparent, content)
}

/** Liquid glass: a blurred slice of the animated backdrop, a tint, a bright rim and a specular glow. */
@Composable
private fun GlassBox(modifier: Modifier, radius: Dp, tint: Color, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val fx = LocalFx.current
    val shape = RoundedCornerShape(radius)
    val blur = remember { BlurEffect(40f, 40f, TileMode.Clamp) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxWidth().clip(shape).onGloballyPositioned { pos = it.positionInRoot() }) {
        if (fx != null) Box(
            Modifier.matchParentSize()
                .graphicsLayer { renderEffect = blur; clip = true }
                .drawBehind {
                    val sz = fx.size.value
                    translate(-pos.x, -pos.y) { drawScene(p, fx.t.value, sz.width.toFloat(), sz.height.toFloat()) }
                },
        )
        Box(Modifier.matchParentSize().background(p.scrim).background(Brush.linearGradient(listOf(p.glassHi, p.glass))).background(tint))
        Column(content = content)
        Box(
            Modifier.matchParentSize().drawBehind {
                val r = radius.toPx()
                drawRoundRect(
                    Brush.linearGradient(listOf(p.edgeHi, Color.Transparent, p.edgeLo), start = Offset.Zero, end = Offset(size.width, size.height)),
                    cornerRadius = CornerRadius(r), style = Stroke(1.5.dp.toPx()),
                )
                val i = 3.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = .07f), topLeft = Offset(i, i), size = Size(size.width - 2 * i, size.height - 2 * i),
                    cornerRadius = CornerRadius(r - i), style = Stroke(2.dp.toPx()),
                )
                drawRoundRect(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent), center = Offset(size.width * .12f, 0f), radius = size.width * .55f),
                    cornerRadius = CornerRadius(r),
                )
            },
        )
    }
}

/** The animated backdrop: a gradient with slowly drifting colored orbs (all drawn, no bitmaps). */
private fun DrawScope.drawScene(p: Pal, t: Float, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    drawRect(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom), startY = 0f, endY = h), size = Size(w, h))
    val a = t * 2f * PI.toFloat()
    fun orb(c: Color, cx: Float, cy: Float, r: Float) = drawCircle(
        Brush.radialGradient(listOf(c, c.copy(alpha = c.alpha * .5f), Color.Transparent), center = Offset(cx, cy), radius = r),
        radius = r, center = Offset(cx, cy),
    )
    orb(p.blobs[0], w * (.22f + .16f * sin(a)), h * (.16f + .06f * cos(2 * a)), w * .75f)
    orb(p.blobs[1], w * (.85f + .10f * cos(a)), h * (.40f + .10f * sin(2 * a)), w * .70f)
    orb(p.blobs[2], w * (.15f + .12f * cos(3 * a)), h * (.70f + .08f * sin(a)), w * .80f)
    orb(p.blobs[3], w * (.80f + .12f * sin(2 * a)), h * (.92f + .04f * cos(a)), w * .70f)
    orb(p.blobs[4], w * (.50f + .20f * sin(3 * a)), h * (.55f + .10f * cos(2 * a)), w * .45f)
}

/** Click with a springy press-in squish and no ripple. */
private fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s = animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = .5f, stiffness = 400f), label = "press")
    this.graphicsLayer { scaleX = s.value; scaleY = s.value }.clickable(interactionSource = src, indication = null, onClick = onClick)
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
            Modifier.fillMaxWidth().bounceClick(onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = p.sub, modifier = Modifier.size(22.dp))
            Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            badges()
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = p.sub)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = .75f, stiffness = 300f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
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
    GlassBox(Modifier, 20.dp, bg) {
        Column(Modifier.padding(14.dp)) {
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
    val shown by animateFloatAsState(frac, spring(dampingRatio = .6f, stiffness = 120f), label = "ring")
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 16.dp.toPx()
            val sz = Size(size.width - w, size.height - w)
            drawArc(color = p.line, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w, cap = StrokeCap.Round))
            if (shown > 0.001f) drawArc(color = p.blue.copy(alpha = .28f), startAngle = 135f, sweepAngle = 270f * shown, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w * 1.8f, cap = StrokeCap.Round))
            if (shown > 0.001f) drawArc(color = p.blue, startAngle = 135f, sweepAngle = 270f * shown, useCenter = false,
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
    val tabs = listOf("Home" to Icons.Filled.Home, "Actions" to Icons.Filled.PlayArrow, "Tweaks" to Icons.Filled.Build, "Measure" to Icons.Filled.Search, "Log" to Icons.Filled.Info)
    val idx = animateFloatAsState(selected.toFloat(), spring(dampingRatio = .55f, stiffness = 260f), label = "navIdx")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        GlassBox(Modifier, 32.dp, Color.Transparent) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
                val itemW = constraints.maxWidth / tabs.size.toFloat()
                Box(
                    Modifier.offset { IntOffset((itemW * idx.value).roundToInt(), 0) }
                        .width(maxWidth / tabs.size).height(60.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .08f))))
                        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .1f))), RoundedCornerShape(26.dp)),
                )
                Row(Modifier.fillMaxWidth().height(60.dp)) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        val c = if (i == selected) p.text else p.sub
                        Column(
                            Modifier.weight(1f).fillMaxHeight().bounceClick { onSelect(i) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
                            Text(label, fontSize = 11.sp, color = c)
                        }
                    }
                }
            }
        }
    }
}
