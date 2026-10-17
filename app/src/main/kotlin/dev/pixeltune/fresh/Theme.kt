package dev.pixeltune.fresh

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object PT {
    val Bg = Color(0xFFF3F5FC)
    val Ink = Color(0xFF141B34)
    val Muted = Color(0xFF6B7391)
    val Card = Color.White
    val Line = Color(0xFFE6E9F5)
    val Blue = Color(0xFF3B6CFF)
    val Cyan = Color(0xFF14C8F0)
    val Violet = Color(0xFF8B5CF6)
    val Mint = Color(0xFF16C79A)
    val Amber = Color(0xFFFFB020)
    val Rose = Color(0xFFFF5C7A)
    val Brand = Brush.linearGradient(listOf(Blue, Violet))
    val Fresh = Brush.linearGradient(listOf(Cyan, Mint))
    val Warm = Brush.linearGradient(listOf(Amber, Rose))
}

@Composable
fun PixelTuneFreshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PT.Blue, background = PT.Bg,
            surface = PT.Card, onSurface = PT.Ink
        ),
        content = content
    )
}
