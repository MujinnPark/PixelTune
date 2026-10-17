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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asFrameworkPaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
            MaterialTheme(colorScheme = neuScheme(isSystemInDarkTheme())) {
                App(status.intValue, hz, actions, toggles, packs, prefs)
            }
        }
    }

    override fun onResume() { super.onResume(); status.intValue = Shell.status() }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(listener); super.onDestroy() }
}

// ---------- palette (light/dark) ----------
private class Pal(
    val bg: Color, val hi: Color, val lo: Color, val card: Color,
    val text: Color, val sub: Color, val line: Color,
    val blue: Color, val blueHi: Color, val cyan: Color,
    val red: Color, val redBg: Color, val amber: Color, val thumb: Color,
)
private val Light = Pal(
    bg = Color(0xFFE8ECF2), hi = Color.White, lo = Color(0xFFBCC5D6), card = Color.Transparent,
    text = Color(0xFF2B3347), sub = Color(0xFF68728A), line = Color(0xFFBCC5D6).copy(alpha = .55f),
    blue = Color(0xFF2F6BFF), blueHi = Color(0xFF5B9BFF), cyan = Color(0xFF2BD0E6),
    red = Color(0xFFD92D20), redBg = Color(0xFFD92D20).copy(alpha = .12f), amber = Color(0xFFF5B800), thumb = Color(0xFFF6F8FC),
)
private val Dark = Pal(
    bg = Color(0xFF23262D), hi = Color(0xFF30343D), lo = Color(0xFF15171C), card = Color.Transparent,
    text = Color(0xFFE6E9F2), sub = Color(0xFF9AA2B5), line = Color(0xFF15171C).copy(alpha = .70f),
    blue = Color(0xFF4D8DFF), blueHi = Color(0xFF7BAEFF), cyan = Color(0xFF22D3EE),
    red = Color(0xFFFF6B63), redBg = Color(0xFFFF6B63).copy(alpha = .16f), amber = Color(0xFFF5B800), thumb = Color(0xFFDDE2EC),
)
private val LocalPal = staticCompositionLocalOf { Light }
private val Green = Color(0xFF16A34A)

/** Material colours remapped so dialogs, text fields and text buttons follow the neumorphic palette. */
private fun neuScheme(dark: Boolean): ColorScheme {
    val p = if (dark) Dark else Light
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.blue, onPrimary = Color.White,
        background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text, onSurfaceVariant = p.sub,
        surfaceContainerHigh = p.bg, surfaceVariant = p.line,
        outline = p.sub, outlineVariant = p.line,
    )
}

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
    var confirmReset by remember { mutableStateOf(false) }
    var resScale by remember { mutableStateOf(prefs.getInt("res_scale", 100)) }
    var resPick by remember { mutableStateOf(90) }
    var resConfirm by remember { mutableStateOf(0) }
    var keepNotif by remember { mutableStateOf(prefs.getBoolean("keep_notif", true)) }
    var resetOnBoot by remember { mutableStateOf(prefs.getBoolean("reset_on_boot", true)) }
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
    val alive = remember { (prefs.getString("alive_sel", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableStateList() }
    var pickAlive by remember { mutableStateOf(false) }
    fun aliveTweak() = Tweak(
        id = "alive", title = "Keep apps alive", desc = "",
        apply = alive.flatMap { listOf("cmd deviceidle whitelist +$it", "cmd appops set $it RUN_ANY_IN_BACKGROUND allow") },
        revert = alive.flatMap { listOf("cmd deviceidle whitelist -$it", "cmd appops set $it RUN_ANY_IN_BACKGROUND default") },
        action = true,
    )
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
    /** Sets the display to pct% of native size and density (100 = reset). Returns an error text, or null on success. */
    suspend fun setScale(pct: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (pct >= 100) {
                Shell.run("wm size reset; wm density reset")
                null
            } else {
                val s = Shell.run("wm size")
                val d = Shell.run("wm density")
                val ms = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(s) ?: return@runCatching "Couldn't read the screen size"
                val md = Regex("Physical density:\\s*(\\d+)").find(d) ?: return@runCatching "Couldn't read the screen density"
                val w = ms.groupValues[1].toInt(); val h = ms.groupValues[2].toInt(); val dpi = md.groupValues[1].toInt()
                Shell.run("wm size ${w * pct / 100 / 4 * 4}x${h * pct / 100 / 4 * 4}; wm density ${dpi * pct / 100}")
                null
            }
        }.getOrElse { it.message ?: "Failed" }
    }
    fun applyRes(pct: Int) {
        scope.launch {
            busy = true
            val before = resScale
            prefs.edit().putBoolean("res_pending", true).apply()
            val err = setScale(pct)
            if (err == null) { resScale = pct; prefs.edit().putInt("res_scale", pct).apply() }
            history.add(0, Entry("Display resolution", System.currentTimeMillis(), if (err == null) 1 else 2,
                if (err == null) (if (pct >= 100) "Reset" else "Confirm") else "Failed",
                err ?: if (pct >= 100) "Display size is back to native." else "Set to $pct%. Confirm within 15 seconds or it reverts."))
            busy = false
            if (err != null || pct >= 100) { prefs.edit().putBoolean("res_pending", false).apply(); return@launch }
            resConfirm = 15
            while (resConfirm > 0) { delay(1000); resConfirm-- }
            if (prefs.getBoolean("res_pending", false)) {
                setScale(before)
                resScale = before
                prefs.edit().putInt("res_scale", before).putBoolean("res_pending", false).apply()
                history.add(0, Entry("Display resolution", System.currentTimeMillis(), 1, "Reverted", "Not confirmed in time, so the display size went back to $before%."))
            }
        }
    }
    // Safety net: an unconfirmed change left over from a crash or a closed app is reset on the next launch.
    LaunchedEffect(status) {
        if (status == 2 && resConfirm == 0 && !busy && prefs.getBoolean("res_pending", false)) {
            setScale(100)
            resScale = 100
            prefs.edit().putInt("res_scale", 100).putBoolean("res_pending", false).apply()
            history.add(0, Entry("Display resolution", System.currentTimeMillis(), 1, "Reset", "An unconfirmed change was found at startup, so the display size was reset."))
        }
    }
    // After a reboot, turn tweaks off once Shizuku is up; reload the screen state when that happens.
    LaunchedEffect(status) { if (status == 2) AutoReset.runIfNeeded(ctx.applicationContext) }
    val resetVer = AutoReset.version.intValue
    LaunchedEffect(resetVer) {
        if (resetVer > 0) {
            (toggles + packs).forEach { on[it.id] = prefs.getBoolean(it.id, false) }
            resScale = prefs.getInt("res_scale", 100)
            history.add(0, Entry("Reset after reboot", System.currentTimeMillis(), 0, "Done",
                "All tweaks were turned off after the reboot. Compile state was left alone."))
        }
    }
    fun resetAll() {
        scope.launch {
            busy = true
            (toggles + packs).forEach { exec(it, false) }
            if (alive.isNotEmpty()) exec(aliveTweak(), false)
            setScale(100)
            resScale = 100
            prefs.edit().putInt("res_scale", 100).putBoolean("res_pending", false).apply()
            actions.firstOrNull { it.id == "reset" }?.let { exec(it, true) }
            history.add(0, Entry("Reset everything", System.currentTimeMillis(), 1, "Reboot to finish",
                "Tweaks are back to Android's defaults and app compilation is reset. Debug properties and some services only fully clear after a reboot."))
            busy = false
        }
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
    fun isOpen(k: String, def: Boolean) = open[k] ?: def

    Box(Modifier.fillMaxSize().background(p.bg)) {
    Column(Modifier.fillMaxSize()) {
        Header(status) { showSettings = true }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 28.dp).clip(RoundedCornerShape(50)), color = p.blue, trackColor = p.line)
        AnimatedContent(
            targetState = tab, modifier = Modifier.weight(1f), label = "tab",
            transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 20 }) togetherWith fadeOut(tween(120)) },
        ) { cur ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
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
                        if (status == 1) NeuButton(onClick = { runCatching { Shizuku.requestPermission(1) } }, modifier = Modifier.fillMaxWidth(), primary = true) { Text("Grant access") }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        NeuButton(onClick = { runMany((toggles + packs).filter { on[it.id] == true }.map { it to false }) }, enabled = canRun && activeOn > 0, modifier = Modifier.weight(1f)) { Text("Turn all off") }
                        NeuButton(onClick = { runMany(listOfNotNull(animTweak?.takeIf { on[it.id] != true }?.let { it to true }, aotAction?.let { it to true })) }, enabled = canRun, modifier = Modifier.weight(1f), primary = true) { Text("Apply recommended") }
                    }
                    Text("Recommended = faster animations + Compile apps. Compile was the only tweak that measurably sped things up (about 17% faster cold start in WhatsApp). Re-run it after big app updates. Undo it from the Actions tab.",
                        color = p.sub, fontSize = 12.sp)
                    NeuButton(onClick = { confirmReset = true }, enabled = canRun, modifier = Modifier.fillMaxWidth(), contentColor = p.red) { Text("Reset everything") }
                    LaunchedEffect(Unit) { while (true) { delay(5000); statsTick++ } }
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
                                NeuButton(onClick = { run(t, true) }, enabled = canRun, primary = true) { Text("Run") }
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            NeuButton(onClick = { pickAlive = false; showSelPicker = true }, enabled = canRun, modifier = Modifier.weight(1f)) { Text("Choose apps") }
                            NeuButton(onClick = { run(selTweak(), true) }, enabled = canRun && sel.isNotEmpty(), modifier = Modifier.weight(1f), primary = true) { Text("Compile") }
                        }
                        if (sel.isNotEmpty()) TextButton(onClick = { run(selTweak(), false) }, enabled = canRun) { Text("Reset these apps", color = p.sub) }
                    }
                }
                Panel {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Keep apps alive", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Exempts the apps you pick (chat apps, for example) from Doze and background limits so their notifications arrive on time. Costs a little battery for those apps only.",
                            color = p.sub, fontSize = 13.sp)
                        val anames = apps.associate { it.second to it.first }
                        Text(if (alive.isEmpty()) "No apps chosen yet." else alive.joinToString(", ") { anames[it] ?: it }, color = p.text, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            NeuButton(onClick = { pickAlive = true; showSelPicker = true }, enabled = canRun, modifier = Modifier.weight(1f)) { Text("Choose apps") }
                            NeuButton(onClick = { run(aliveTweak(), true) }, enabled = canRun && alive.isNotEmpty(), modifier = Modifier.weight(1f), primary = true) { Text("Allow") }
                        }
                        if (alive.isNotEmpty()) TextButton(onClick = { run(aliveTweak(), false) }, enabled = canRun) { Text("Reset these apps", color = p.sub) }
                    }
                }
                }
                2 -> {
                    val std = packs.filter { !it.risky }
                    val risky = packs.filter { it.risky }
                    val onToggle = { t: Tweak, v: Boolean -> if (v && t.risky) confirm = t else run(t, v) }
                    Accordion("System tweaks", Icons.Filled.Refresh, isOpen("d", true), { open["d"] = !isOpen("d", true) },
                        badges = { CountChip(toggles.count { on[it.id] == true }, p.amber, Color.Black) }) {
                        toggles.forEachIndexed { i, t -> if (i > 0) Hairline(); ToggleRow(t, on[t.id] == true, canRun) { onToggle(t, it) } }
                    }
                    Accordion("Display resolution", Icons.Filled.Settings, isOpen("res", false), { open["res"] = !isOpen("res", false) },
                        badges = { if (resScale < 100) Chip("$resScale%", p.amber, Color.Black) }) {
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Draws the whole screen at a lower resolution and scales it up. Less GPU work means cooler and smoother in heavy games, " +
                                "but a softer picture. Open apps may restart when it changes. A 15-second timer reverts it unless you tap Keep.",
                                color = p.sub, fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                listOf(100, 90, 80, 70).forEach { v -> NeuChoice("$v%", resPick == v) { resPick = v } }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                NeuButton(onClick = { applyRes(resPick) }, enabled = canRun && resPick != resScale, modifier = Modifier.weight(1f), primary = true) { Text(if (resPick >= 100) "Restore native" else "Apply $resPick%") }
                                NeuButton(onClick = { applyRes(100) }, enabled = canRun && resScale < 100, modifier = Modifier.weight(1f)) { Text("Reset display size") }
                            }
                        }
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
                            NeuButton(onClick = { runBench() }, enabled = canRun, modifier = Modifier.fillMaxWidth(), primary = true) { Text(if (benching) benchMsg else "Run test") }
                            if (!benching && benchMsg.isNotBlank()) Text(benchMsg, color = p.sub, fontSize = 12.sp)
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
                    if (results.isNotEmpty()) NeuButton(onClick = { results.clear(); Bench.save(prefs, results) }, modifier = Modifier.fillMaxWidth()) { Text("Clear results") }
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
                    if (history.isNotEmpty()) NeuButton(onClick = { history.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Clear history") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        }
        BottomNav(tab) { tab = it }
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
        title = { Text(if (pickAlive) "Choose apps to keep alive" else "Choose apps to compile") },
        text = {
            Column {
                OutlinedTextField(value = selQuery, onValueChange = { selQuery = it }, singleLine = true,
                    placeholder = { Text("Search") }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { userOnly = !userOnly }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = userOnly, onCheckedChange = { userOnly = it }, colors = CheckboxDefaults.colors(checkedColor = p.blue))
                    Text("User apps only", fontSize = 14.sp)
                }
                val target = if (pickAlive) alive else sel
                fun saveTarget() { prefs.edit().putString(if (pickAlive) "alive_sel" else "aot_sel", target.joinToString(",")).apply() }
                val shown = apps.filter { (l, pk) -> (!userOnly || pk in userPkgs) && l.contains(selQuery, ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.second }) { (label, pkg) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (pkg in target) target.remove(pkg) else target.add(pkg); saveTarget() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = pkg in target, colors = CheckboxDefaults.colors(checkedColor = p.blue),
                                onCheckedChange = { if (it) { if (pkg !in target) target.add(pkg) } else target.remove(pkg); saveTarget() })
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
    if (resConfirm > 0) AlertDialog(
        onDismissRequest = {},
        title = { Text("Keep this resolution?") },
        text = { Text("Reverting in $resConfirm s unless you tap Keep.") },
        confirmButton = { TextButton(onClick = { prefs.edit().putBoolean("res_pending", false).apply(); resConfirm = 0 }) { Text("Keep") } },
        dismissButton = { TextButton(onClick = { resConfirm = 0 }) { Text("Revert now") } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset everything?") },
        text = {
            Text("This turns off every PixelTune tweak and deletes the values they set, so those settings go back to Android's defaults " +
                "(including any you changed yourself on the same keys). It also undoes app compilation, the keep-alive list and any display-size change. " +
                "Reboot afterwards to finish.")
        },
        confirmButton = { TextButton(onClick = { confirmReset = false; resetAll() }) { Text("Reset", color = p.red) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
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
                    NeuSwitch(checked = keepNotif, onCheckedChange = { keepNotif = it; prefs.edit().putBoolean("keep_notif", it).apply() })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Reset tweaks after reboot", fontWeight = FontWeight.Medium)
                        Text("After each reboot, turns every tweak off again once Shizuku starts. Compile is left alone.", fontSize = 13.sp)
                    }
                    NeuSwitch(checked = resetOnBoot, onCheckedChange = { resetOnBoot = it; prefs.edit().putBoolean("reset_on_boot", it).apply() })
                }
                Spacer(Modifier.height(12.dp))
                Text("Logs are saved to Download/PixelTune · v$ver", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
    )
}

// ---------- neumorphism primitives ----------
private fun DrawScope.neuFill(fill: Color, shadow: Color, dx: Float, dy: Float, blur: Float, r: Float) {
    val paint = Paint()
    paint.asFrameworkPaint().apply {
        color = fill.toArgb()
        setShadowLayer(blur, dx, dy, shadow.toArgb())
    }
    drawContext.canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
}

private fun DrawScope.neuShadowPath(path: Path, fill: Color, shadow: Color, dx: Float, dy: Float, blur: Float) {
    val paint = Paint()
    paint.asFrameworkPaint().apply {
        color = fill.toArgb()
        setShadowLayer(blur, dx, dy, shadow.toArgb())
    }
    drawContext.canvas.drawPath(path, paint)
}

/** Raised surface: light shadow top-left, dark shadow bottom-right. [brush] paints a gradient face, [glow] tints the dark shadow. */
private fun Modifier.neuRaised(p: Pal, radius: Dp, depth: Dp = 7.dp, brush: Brush? = null, glow: Color? = null): Modifier =
    drawBehind {
        val r = radius.toPx()
        val d = depth.toPx()
        neuFill(p.bg, glow ?: p.lo, d, d, d * 2f, r)
        neuFill(p.bg, p.hi, -d, -d, d * 2f, r)
        if (brush != null) drawRoundRect(brush, cornerRadius = CornerRadius(r))
    }

/** Pressed-in surface: dark inner shadow top-left, light inner shadow bottom-right. */
private fun Modifier.neuInset(p: Pal, radius: Dp, depth: Dp = 4.dp): Modifier =
    drawBehind {
        val r = radius.toPx()
        val d = depth.toPx()
        val rr = RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))
        val hole = Path().apply { addRoundRect(rr) }
        drawRoundRect(p.bg, cornerRadius = CornerRadius(r))
        clipPath(hole) {
            val m = 60.dp.toPx()
            val frame = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-m, -m, size.width + m, size.height + m))
                addRoundRect(rr)
            }
            neuShadowPath(frame, p.bg, p.lo, d, d, d * 2f)
            neuShadowPath(frame, p.bg, p.hi, -d, -d, d * 2f)
        }
    }

/** Click with a springy press-in squish and no ripple. */
private fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s = animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = .5f, stiffness = 400f), label = "press")
    this.graphicsLayer { scaleX = s.value; scaleY = s.value }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

// ---------- building blocks ----------
@Composable
private fun Header(status: Int, onGear: () -> Unit) {
    val p = LocalPal.current
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulseA")
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).neuRaised(p, 24.dp, 5.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(84.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PixelTune", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(when (status) { 2 -> Green; 1 -> p.amber; else -> p.red }))
                Spacer(Modifier.width(6.dp))
                Text(when (status) { 2 -> "Shizuku connected"; 1 -> "Needs permission"; else -> "Shizuku not running" }, color = p.sub, fontSize = 13.sp)
            }
        }
        Box(Modifier.size(46.dp).neuRaised(p, 23.dp, 5.dp).clip(CircleShape).bounceClick(onGear), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Settings, "Settings", tint = p.sub, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(modifier.fillMaxWidth().neuRaised(p, 28.dp, 8.dp), content = content)
}

/** Pill button. Soft = raised and presses in; primary = blue gradient face. */
@Composable
private fun NeuButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, contentColor: Color? = null, content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fg = when { primary -> Color.White; contentColor != null -> contentColor; else -> p.text }
    val look = when {
        primary -> Modifier.neuRaised(p, 28.dp, if (pressed) 3.dp else 6.dp, Brush.linearGradient(listOf(p.blueHi, p.blue)), p.blue.copy(alpha = .45f))
        pressed -> Modifier.neuInset(p, 28.dp, 4.dp)
        else -> Modifier.neuRaised(p, 28.dp, 6.dp)
    }
    Row(
        modifier.graphicsLayer { alpha = if (enabled) 1f else .45f }
            .then(look)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** Selectable pill: raised when off, pressed-in with blue text when selected. */
@Composable
private fun NeuChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Text(
        text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) p.blue else p.text,
        modifier = (if (selected) Modifier.neuInset(p, 22.dp, 3.dp) else Modifier.neuRaised(p, 22.dp, 4.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

/** Pressed-in track with a raised thumb; the track fills with the blue gradient when on. */
@Composable
private fun NeuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val p = LocalPal.current
    val f by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = .7f, stiffness = 500f), label = "switch")
    Box(
        Modifier.graphicsLayer { alpha = if (enabled) 1f else .5f }
            .size(width = 58.dp, height = 32.dp)
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null, onValueChange = onCheckedChange,
            )
            .neuInset(p, 16.dp, 3.dp)
            .drawBehind {
                val a = f.coerceIn(0f, 1f)
                if (a > 0f) drawRoundRect(
                    Brush.horizontalGradient(listOf(p.blue, p.cyan)),
                    cornerRadius = CornerRadius(size.height / 2f), alpha = a * .9f,
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset { IntOffset((4.dp + 26.dp * f).roundToPx(), 0) }
                .size(24.dp).neuRaised(p, 12.dp, 3.dp).clip(CircleShape).background(p.thumb),
        )
    }
}

@Composable
private fun Hairline() {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hi))
    }
}

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
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).neuRaised(p, 20.dp, 4.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = p.blue, modifier = Modifier.size(20.dp))
            }
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
        NeuSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun IssueCard(label: String, color: Color, bg: Color, time: String, title: String, body: String = "") {
    val p = LocalPal.current
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().neuRaised(p, 24.dp, 7.dp).clip(shape).background(bg).padding(16.dp)) {
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
    Column(
        Modifier.neuInset(p, 18.dp, 4.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.sub, fontSize = 12.sp)
    }
}

/** Gauge: gradient arc on a pressed-in track, with a raised disc in the middle. */
@Composable
private fun Ring(frac: Float, big: String, small: String) {
    val p = LocalPal.current
    val shown by animateFloatAsState(frac, spring(dampingRatio = .6f, stiffness = 120f), label = "ring")
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val sz = Size(size.width - w, size.height - w)
            drawArc(color = p.line, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w, cap = StrokeCap.Round))
            if (shown > 0.001f) drawArc(
                brush = Brush.linearGradient(listOf(p.blue, p.cyan), start = Offset(0f, size.height), end = Offset(size.width, 0f)),
                startAngle = 135f, sweepAngle = 270f * shown, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = sz, style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        Column(
            Modifier.size(116.dp).neuRaised(p, 58.dp, 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(big, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Text(small, fontSize = 13.sp, color = p.sub)
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    val tabs = listOf("Home" to Icons.Filled.Home, "Actions" to Icons.Filled.PlayArrow, "Tweaks" to Icons.Filled.Build, "Measure" to Icons.Filled.Search, "Log" to Icons.Filled.Info)
    val idx = animateFloatAsState(selected.toFloat(), spring(dampingRatio = .55f, stiffness = 260f), label = "navIdx")
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().neuRaised(p, 32.dp, 8.dp).padding(6.dp)) {
            val itemW = constraints.maxWidth / tabs.size.toFloat()
            Box(
                Modifier.offset { IntOffset((itemW * idx.value).roundToInt(), 0) }
                    .width(maxWidth / tabs.size).height(60.dp)
                    .neuRaised(p, 26.dp, 3.dp, Brush.linearGradient(listOf(p.blueHi, p.blue)), p.blue.copy(alpha = .45f)),
            )
            Row(Modifier.fillMaxWidth().height(60.dp)) {
                tabs.forEachIndexed { i, (label, icon) ->
                    val c = if (i == selected) Color.White else p.sub
                    Column(
                        Modifier.weight(1f).fillMaxHeight().bounceClick { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
                        Text(label, fontSize = 11.sp, color = c, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
