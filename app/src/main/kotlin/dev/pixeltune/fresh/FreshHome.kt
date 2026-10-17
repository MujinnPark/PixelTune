package dev.pixeltune.fresh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stateless: feed it your real state. Defaults are only sample values. */
@Composable
fun FreshHome(
    connected: Boolean = true,
    tweaksOn: Int = 6, tweaksTotal: Int = 11,
    refreshHz: String = "120 Hz", guardOn: Boolean = true, riskyOn: Int = 0,
    ramFreeGb: Float = 4.3f, ramTotalGb: Float = 8f,
    storageFreeGb: Float = 185f, storageTotalGb: Float = 256f,
    tempC: Float = 38.3f, battery: Int = 22,
    jankHistory: List<Float> = listOf(9f, 7.5f, 8f, 5f, 4.2f, 4.8f, 3.1f),
    refreshMode: Int = 0, onRefreshMode: (Int) -> Unit = {},
    onCompile: () -> Unit = {}, onDexopt: () -> Unit = {},
    onTrim: () -> Unit = {}, onClearBg: () -> Unit = {},
    onApplyRecommended: () -> Unit = {}, onTurnAllOff: () -> Unit = {}
) {
    PixelTuneFreshTheme {
        AuroraBackground {
            Column(
                Modifier.verticalScroll(rememberScrollState()).statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("PixelTune", color = PT.Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (connected) PT.Mint else PT.Rose, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(if (connected) "Shizuku connected" else "Shizuku offline",
                            color = PT.Muted, fontSize = 13.sp)
                    }
                }

                PTCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GradientRing(tweaksOn / tweaksTotal.toFloat(), "$tweaksOn/$tweaksTotal", "tweaks on")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Turn all off", false, Modifier.weight(1f), onTurnAllOff)
                        PillButton("Apply recommended", true, Modifier.weight(1.4f), onApplyRecommended)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(refreshHz, "Display", PT.Blue, Modifier.weight(1f))
                    StatTile(if (guardOn) "On" else "Off", "Notif. guard", PT.Mint, Modifier.weight(1f))
                    StatTile("$riskyOn", "Risky on", if (riskyOn > 0) PT.Rose else PT.Amber, Modifier.weight(1f))
                }

                SectionTitle("Phone status", "live")
                PTCard(Modifier.fillMaxWidth(), glow = PT.Cyan) {
                    Meter("RAM free", "%.1f / %.0f GB".format(ramFreeGb, ramTotalGb), ramFreeGb / ramTotalGb, PT.Fresh)
                    Meter("Storage free", "%.0f / %.0f GB".format(storageFreeGb, storageTotalGb), storageFreeGb / storageTotalGb, PT.Brand)
                    Meter("Temperature", "%.1f °C".format(tempC), tempC / 60f, PT.Warm)
                    Meter("Battery", "$battery%", battery / 100f, PT.Fresh)
                }

                SectionTitle("Refresh rate")
                PTCard(Modifier.fillMaxWidth()) {
                    Segmented(listOf("Auto", "60 Hz", "120 Hz"), refreshMode, onRefreshMode)
                }

                SectionTitle("Quick actions")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction("⚡", "Compile apps", "~17% faster cold start", PT.Brand, PT.Blue, Modifier.weight(1f), onCompile)
                    QuickAction("🧹", "Dexopt now", "Run in background", PT.Fresh, PT.Mint, Modifier.weight(1f), onDexopt)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction("🗑️", "Trim caches", "Free temp files", PT.Warm, PT.Amber, Modifier.weight(1f), onTrim)
                    QuickAction("🧊", "Clear background", "Free RAM briefly", PT.Brand, PT.Violet, Modifier.weight(1f), onClearBg)
                }

                SectionTitle("Smoothness", "jank % ↓ better")
                PTCard(Modifier.fillMaxWidth(), glow = PT.Violet) {
                    Sparkline(jankHistory, Modifier.fillMaxWidth().height(90.dp), PT.Violet)
                    Spacer(Modifier.height(6.dp))
                    Text("Last ${jankHistory.size} test runs", color = PT.Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}
