package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.PantherInk
import com.example.ui.theme.PumpkinYellow
import com.example.ui.theme.PumpkinYellowDeep

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
            alpha = if (isDark) 0.90f else 0.78f,
            modifier = Modifier.fillMaxSize()
        )
        content()
    }
}

/**
 * Minimalist monoline half-leopard face inside a Pumpkin Yellow shield.
 * Drawn procedurally on a 108x108 coordinate grid so stroke weights remain razor-sharp at any size.
 */
@Composable
fun PalangLineArtLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PalaNG Logo",
    forceDarkInk: Boolean? = null
) {
    val isDark = forceDarkInk ?: LocalIsDarkTheme.current
    val shieldColor = if (isDark) PumpkinYellow else PumpkinYellowDeep
    val faceLineColor = if (isDark) Color(0xFFF3F6FB) else PantherInk
    val subtleSplitColor = faceLineColor.copy(alpha = 0.35f)
    val eyeAccentColor = if (isDark) PumpkinYellow else PumpkinYellowDeep

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
            val shieldStroke = Stroke(
                width = 2.45f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val primaryStroke = Stroke(
                width = 2.15f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val fineStroke = Stroke(
                width = 1.6f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // 1. Minimalist Shield Outline
            val shieldPath = Path().apply {
                moveTo(54f, 14f)
                lineTo(86f, 25f)
                lineTo(86f, 52f)
                cubicTo(86f, 71.5f, 72.5f, 84.5f, 54f, 93f)
                cubicTo(35.5f, 84.5f, 22f, 71.5f, 22f, 52f)
                lineTo(22f, 25f)
                close()
            }
            drawPath(shieldPath, color = shieldColor, style = shieldStroke)

            // 2. Subtle vertical median axis (marks the half-face symmetry cleanly)
            drawLine(
                color = subtleSplitColor,
                start = Offset(54f, 24f),
                end = Offset(54f, 83f),
                strokeWidth = 1.2f,
                cap = StrokeCap.Round
            )

            // 3. Minimalist Half Leopard Face (Right Side)
            val headOutline = Path().apply {
                moveTo(54f, 32f)
                cubicTo(59.5f, 32f, 63.5f, 33.8f, 66f, 36f)
                lineTo(73f, 27f)
                lineTo(75.5f, 39f)
                cubicTo(78.5f, 45f, 78.5f, 52f, 75.5f, 58f)
                cubicTo(76.8f, 62.5f, 73.8f, 67.5f, 69f, 71f)
                lineTo(63.5f, 75.5f)
                cubicTo(60f, 78f, 57f, 78.8f, 54f, 78.8f)
            }
            drawPath(headOutline, color = faceLineColor, style = primaryStroke)

            // Inner ear & sleek brow
            val browAndEar = Path().apply {
                moveTo(67.5f, 36f)
                lineTo(71.5f, 31.5f)
                moveTo(56.5f, 43.5f)
                quadraticTo(63.5f, 41f, 70.5f, 44.5f)
            }
            drawPath(browAndEar, color = faceLineColor, style = fineStroke)

            // Almond eye outline + vertical slit pupil (Pumpkin Yellow accent)
            val eyePath = Path().apply {
                moveTo(58.5f, 49.5f)
                quadraticTo(64f, 46.2f, 69.5f, 49.2f)
                quadraticTo(64f, 52.2f, 58.5f, 49.5f)
            }
            drawPath(eyePath, color = eyeAccentColor, style = fineStroke)
            drawLine(
                color = eyeAccentColor,
                start = Offset(64.2f, 48f),
                end = Offset(64.2f, 50.8f),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )

            // Clean nose bridge + half nose + muzzle line + 3 minimal open C-arc leopard rosettes
            val detailPath = Path().apply {
                moveTo(54f, 46.5f)
                lineTo(56.8f, 57.5f)
                lineTo(60.2f, 57.5f)
                cubicTo(59.8f, 61.2f, 57.4f, 63f, 54f, 63f)
                lineTo(54f, 67.5f)
                quadraticTo(60.5f, 68.5f, 66f, 64.2f)

                // Forehead rosette arc
                moveTo(57.5f, 36.5f)
                cubicTo(59.8f, 35.6f, 61.5f, 37.4f, 59.8f, 39.2f)
                // Temple rosette arc
                moveTo(72f, 51.2f)
                cubicTo(74.2f, 52.2f, 73.6f, 55.2f, 71.2f, 55.0f)
                // Cheek rosette arc
                moveTo(67.5f, 58.5f)
                cubicTo(69.5f, 59.5f, 68.8f, 62.2f, 66.5f, 61.8f)
            }
            drawPath(detailPath, color = faceLineColor, style = fineStroke)
        }
    }
}
