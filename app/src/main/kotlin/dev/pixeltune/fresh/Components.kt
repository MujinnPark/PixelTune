package dev.pixeltune.fresh

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(PT.Bg).drawBehind {
            fun blob(c: Color, x: Float, y: Float, r: Float) {
                val o = Offset(size.width * x, size.height * y)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .30f), Color.Transparent), o, r), r, o)
            }
            blob(PT.Cyan, .95f, .02f, size.width * .75f)
            blob(PT.Violet, .0f, .30f, size.width * .70f)
            blob(PT.Mint, .9f, .75f, size.width * .65f)
        },
        content = content
    )
}

@Composable
fun PTCard(
    modifier: Modifier = Modifier,
    glow: Color = PT.Blue,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(16.dp, shape, ambientColor = glow.copy(.15f), spotColor = glow.copy(.25f))
            .background(PT.Card, shape)
            .border(1.dp, PT.Line, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = PT.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Chip(trailing, PT.Blue)
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(color.copy(.12f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun GradientRing(progress: Float, big: String, small: String, modifier: Modifier = Modifier.size(210.dp)) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            val brush = Brush.sweepGradient(listOf(PT.Cyan, PT.Blue, PT.Violet, PT.Violet), center)
            drawArc(PT.Line, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            rotate(135f) {
                drawArc(brush, 0f, 270f * p, false, tl, sz, alpha = .25f,
                    style = Stroke(stroke + 14.dp.toPx(), cap = StrokeCap.Round))
                drawArc(brush, 0f, 270f * p, false, tl, sz,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, color = PT.Ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text(small, color = PT.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    PTCard(modifier, glow = accent) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(value, color = PT.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = PT.Muted, fontSize = 12.sp)
    }
}

@Composable
fun Meter(title: String, valueText: String, fraction: Float, brush: Brush) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "meter")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(title, color = PT.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = PT.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(PT.Line)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(brush))
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(PT.Bg, CircleShape).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val base = Modifier.weight(1f).clip(CircleShape)
            Box(
                (if (on) base.background(PT.Brand) else base)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, color = if (on) Color.White else PT.Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val base = modifier.clip(CircleShape)
    Box(
        (if (primary) base.background(PT.Brand) else base.background(PT.Bg))
            .clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (primary) Color.White else PT.Ink)
    }
}

@Composable
fun QuickAction(emoji: String, title: String, sub: String, brush: Brush, glow: Color,
                modifier: Modifier = Modifier, onClick: () -> Unit) {
    PTCard(modifier.clickable { onClick() }, glow = glow) {
        Box(Modifier.size(42.dp).background(brush, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = PT.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(sub, color = PT.Muted, fontSize = 12.sp)
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, color: Color = PT.Blue) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = values.max(); val mn = values.min()
        val r = if (mx - mn > 0f) mx - mn else 1f
        val dx = size.width / (values.size - 1)
        fun y(v: Float) = size.height * .9f - (v - mn) / r * size.height * .8f
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * dx, y(v)) }
        }
        val fill = Path().apply {
            addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun TweakRow(emoji: String, title: String, note: String, checked: Boolean,
             risky: Boolean = false, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(PT.Bg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = PT.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (risky) { Spacer(Modifier.width(6.dp)); Chip("risky", PT.Rose) }
            }
            Text(note, color = PT.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PT.Blue, checkedThumbColor = Color.White,
                uncheckedTrackColor = PT.Line, uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
