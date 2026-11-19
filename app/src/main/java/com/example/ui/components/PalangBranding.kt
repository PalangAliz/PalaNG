package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.LeopardSpotDarkInner
import com.example.ui.theme.LeopardSpotDarkOuter
import com.example.ui.theme.LeopardSpotLightInner
import com.example.ui.theme.LeopardSpotLightOuter
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PumpkinYellow

private data class LeopardRosetteSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val rotationDeg: Float,
    val isRosette: Boolean = true
)

private val LEOPARD_PATTERN_LAYOUT = listOf(
    LeopardRosetteSpec(0.86f, 0.11f, 0.22f, 24f, isRosette = true),
    LeopardRosetteSpec(0.14f, 0.23f, 0.19f, -35f, isRosette = true),
    LeopardRosetteSpec(0.76f, 0.42f, 0.25f, 52f, isRosette = true),
    LeopardRosetteSpec(0.22f, 0.62f, 0.23f, 18f, isRosette = true),
    LeopardRosetteSpec(0.88f, 0.74f, 0.17f, -22f, isRosette = false),
    LeopardRosetteSpec(0.52f, 0.89f, 0.24f, 68f, isRosette = true),
    LeopardRosetteSpec(0.08f, 0.90f, 0.15f, -15f, isRosette = false)
)

@Composable
fun PalangBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val baseColor = MaterialTheme.colorScheme.background
    val outerSpotColor = if (isDark) LeopardSpotDarkOuter.copy(alpha = 0.85f) else LeopardSpotLightOuter.copy(alpha = 0.72f)
    val innerSpotColor = if (isDark) LeopardSpotDarkInner.copy(alpha = 0.80f) else LeopardSpotLightInner.copy(alpha = 0.88f)
    val ambientGlow = if (isDark) PumpkinYellow.copy(alpha = 0.045f) else PumpkinYellow.copy(alpha = 0.035f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ambientGlow, Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                    radius = size.maxDimension * 0.65f
                )
            )
            val minDim = size.minDimension
            LEOPARD_PATTERN_LAYOUT.forEach { spec ->
                val center = Offset(size.width * spec.xFraction, size.height * spec.yFraction)
                val radius = minDim * spec.radiusFraction
                if (spec.isRosette) {
                    drawLeopardRosette(
                        center = center,
                        radius = radius,
                        rotationDeg = spec.rotationDeg,
                        outerColor = outerSpotColor,
                        innerColor = innerSpotColor
                    )
                } else {
                    rotate(degrees = spec.rotationDeg, pivot = center) {
                        drawOval(
                            color = outerSpotColor,
                            topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.56f),
                            size = Size(radius * 1.44f, radius * 1.12f)
                        )
                    }
                }
            }
        }
        content()
    }
}

private fun DrawScope.drawLeopardRosette(
    center: Offset,
    radius: Float,
    rotationDeg: Float,
    outerColor: Color,
    innerColor: Color
) {
    rotate(degrees = rotationDeg, pivot = center) {
        // Large organic broken outer ring formed by 3 overlapping curved blotches
        drawOval(
            color = outerColor,
            topLeft = Offset(center.x - radius * 0.96f, center.y - radius * 0.72f),
            size = Size(radius * 1.18f, radius * 0.82f)
        )
        drawOval(
            color = outerColor,
            topLeft = Offset(center.x - radius * 0.14f, center.y - radius * 0.86f),
            size = Size(radius * 1.06f, radius * 1.04f)
        )
        drawOval(
            color = outerColor,
            topLeft = Offset(center.x - radius * 0.74f, center.y + radius * 0.08f),
            size = Size(radius * 1.30f, radius * 0.78f)
        )
        // Soft inner core patch creating authentic leopard rosette contrast
        drawOval(
            color = innerColor,
            topLeft = Offset(center.x - radius * 0.42f, center.y - radius * 0.34f),
            size = Size(radius * 0.82f, radius * 0.66f)
        )
    }
}
