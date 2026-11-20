package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun PalangBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val baseColor = MaterialTheme.colorScheme.background
    val furDrawableRes = if (isDark) R.drawable.img_leopard_coat_dark else R.drawable.img_leopard_coat_light

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Image(
            painter = painterResource(id = furDrawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (isDark) 0.56f else 0.44f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor.copy(alpha = if (isDark) 0.36f else 0.42f))
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * Refined, calm monochrome leopard face built strictly from smooth flowing Bezier curves
 * with zero sharp angular corners.
 */
@Composable
fun PalangLineArtLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PalaNG Logo",
    forceDarkInk: Boolean? = null
) {
    val isDark = forceDarkInk ?: LocalIsDarkTheme.current
    val inkColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val subtleInk = inkColor.copy(alpha = 0.72f)

    val semanticsModifier = if (contentDescription != null) {
        modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        }
    } else {
        modifier
    }

    Canvas(modifier = semanticsModifier) {
        val scale = size.minDimension / 108f
        val offsetX = (size.width - 108f * scale) / 2f
        val offsetY = (size.height - 108f * scale) / 2f

        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            val outerStroke = Stroke(
                width = 2.6f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val featureStroke = Stroke(
                width = 2.0f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val detailStroke = Stroke(
                width = 1.65f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // 1. Continuous smooth leopard face & rounded ears silhouette (zero sharp angles)
            val headContour = Path().apply {
                moveTo(54f, 23.5f)
                // Left crown -> rounded left ear -> soft temple -> cheek -> rounded chin
                cubicTo(45f, 23.5f, 38.5f, 24.8f, 34.5f, 27.2f)
                cubicTo(28.5f, 19.5f, 20.5f, 24.5f, 24.5f, 37.5f)
                cubicTo(20.5f, 48.5f, 22.5f, 60.5f, 29.5f, 68.5f)
                cubicTo(36f, 76.2f, 44.5f, 81.5f, 54f, 81.5f)
                // Right mirror: rounded chin -> cheek -> temple -> rounded right ear -> crown
                cubicTo(63.5f, 81.5f, 72f, 76.2f, 78.5f, 68.5f)
                cubicTo(85.5f, 60.5f, 87.5f, 48.5f, 83.5f, 37.5f)
                cubicTo(87.5f, 24.5f, 79.5f, 19.5f, 73.5f, 27.2f)
                cubicTo(69.5f, 24.8f, 63f, 23.5f, 54f, 23.5f)
            }
            drawPath(headContour, color = inkColor, style = outerStroke)

            // 2. Flowing brow-to-muzzle twin ribbons & inner ear curves
            val browAndMuzzle = Path().apply {
                // Left brow curving smoothly into nose bridge & muzzle arc
                moveTo(31.5f, 42.5f)
                cubicTo(38.5f, 38.5f, 45f, 42f, 46.5f, 52.5f)
                cubicTo(47.2f, 57.5f, 49.5f, 60.5f, 54f, 61.5f)
                cubicTo(54f, 65f, 50.5f, 68f, 44.5f, 67.5f)
                // Right brow mirror
                moveTo(76.5f, 42.5f)
                cubicTo(69.5f, 38.5f, 63f, 42f, 61.5f, 52.5f)
                cubicTo(60.8f, 57.5f, 58.5f, 60.5f, 54f, 61.5f)
                cubicTo(54f, 65f, 57.5f, 68f, 63.5f, 67.5f)
            }
            drawPath(browAndMuzzle, color = inkColor, style = featureStroke)

            // 3. Soft curved almond eyes + curved nose + chin curve + subtle rosette arcs
            val fineFeatures = Path().apply {
                // Left inner ear curve
                moveTo(32.5f, 31f)
                quadraticTo(27.2f, 27.8f, 27.5f, 34f)
                // Right inner ear curve
                moveTo(75.5f, 31f)
                quadraticTo(80.8f, 27.8f, 80.5f, 34f)

                // Left smooth curved eye
                moveTo(33.5f, 47.5f)
                cubicTo(37f, 44.5f, 41f, 45.2f, 43f, 48.2f)
                cubicTo(40f, 50.2f, 36.2f, 49.8f, 33.5f, 47.5f)

                // Right smooth curved eye
                moveTo(74.5f, 47.5f)
                cubicTo(71f, 44.5f, 67f, 45.2f, 65f, 48.2f)
                cubicTo(68f, 50.2f, 71.8f, 49.8f, 74.5f, 47.5f)

                // Soft curved nose crest
                moveTo(49.5f, 57.5f)
                cubicTo(52.2f, 55.5f, 55.8f, 55.5f, 58.5f, 57.5f)
                cubicTo(56.5f, 60f, 51.5f, 60f, 49.5f, 57.5f)

                // Gentle chin expression arc
                moveTo(47.5f, 73f)
                quadraticTo(54f, 76.2f, 60.5f, 73f)

                // Subtle open curved leopard rosette marks (forehead & cheeks)
                moveTo(51.5f, 30.8f)
                cubicTo(53.2f, 29.2f, 54.8f, 29.2f, 56.5f, 30.8f)
                moveTo(43.8f, 34.5f)
                cubicTo(45.5f, 32.8f, 47.2f, 33.8f, 46.2f, 36f)
                moveTo(64.2f, 34.5f)
                cubicTo(62.5f, 32.8f, 60.8f, 33.8f, 61.8f, 36f)
                moveTo(27.5f, 54f)
                cubicTo(29.8f, 52.8f, 31.8f, 54.5f, 30.2f, 57.2f)
                moveTo(80.5f, 54f)
                cubicTo(78.2f, 52.8f, 76.2f, 54.5f, 77.8f, 57.2f)
            }
            drawPath(fineFeatures, color = subtleInk, style = detailStroke)
        }
    }
}
