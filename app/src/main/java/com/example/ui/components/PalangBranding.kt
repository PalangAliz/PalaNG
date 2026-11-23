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
 * Fierce roaring wild panther emblem built from sharp predatory strokes,
 * furrowed V-brow ridges, blazing amber slit eyes, and bared canine fangs.
 */
@Composable
fun PalangLineArtLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PalaNG Logo",
    forceDarkInk: Boolean? = null
) {
    val isDark = forceDarkInk ?: LocalIsDarkTheme.current
    val inkColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subtleInk = inkColor.copy(alpha = 0.80f)
    val eyeGlow = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val pupilColor = if (isDark) Color(0xFF090D16) else Color(0xFF0B0F17)
    val mouthBg = if (isDark) Color(0xFF0B0F17).copy(alpha = 0.85f) else Color(0xFF111827)
    val fangColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFFFFFFFF)

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
                width = 3.1f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Miter
            )
            val featureStroke = Stroke(
                width = 2.4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val detailStroke = Stroke(
                width = 1.85f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // 1. Dark roaring maw interior so bared white fangs pop with fierce contrast
            val roarMawFill = Path().apply {
                moveTo(38f, 65f)
                quadraticTo(54f, 60f, 70f, 65f)
                lineTo(73f, 77.5f)
                quadraticTo(54f, 88f, 35f, 77.5f)
                close()
            }
            drawPath(roarMawFill, color = mouthBg)

            // 2. Fierce outer head silhouette: pinned-back pointed ears, jagged cheek ruff & heavy jaw
            val headContour = Path().apply {
                moveTo(54f, 18f)
                lineTo(43f, 21f)
                lineTo(34f, 25f)
                lineTo(20f, 17f)
                lineTo(24f, 35f)
                lineTo(15f, 43f)
                lineTo(22f, 52f)
                lineTo(17f, 60f)
                lineTo(26f, 69f)
                lineTo(24f, 75f)
                lineTo(35f, 84f)
                lineTo(41f, 92f)
                lineTo(54f, 95f)
                lineTo(67f, 92f)
                lineTo(73f, 84f)
                lineTo(84f, 75f)
                lineTo(82f, 69f)
                lineTo(91f, 60f)
                lineTo(86f, 52f)
                lineTo(93f, 43f)
                lineTo(84f, 35f)
                lineTo(88f, 17f)
                lineTo(74f, 25f)
                lineTo(65f, 21f)
                close()
            }
            drawPath(headContour, color = inkColor, style = outerStroke)

            // 3. Furrowed angry V-brows, snarling nasal wrinkles, nose diamond & jaw frame
            val browAndSnarl = Path().apply {
                // Angry V-shaped furrowed brow ridges
                moveTo(27f, 36f)
                lineTo(41f, 42f)
                lineTo(51f, 50f)
                moveTo(81f, 36f)
                lineTo(67f, 42f)
                lineTo(57f, 50f)

                // Sharp inner ear notches
                moveTo(27f, 24.5f)
                lineTo(27f, 32f)
                lineTo(33f, 29f)
                moveTo(81f, 24.5f)
                lineTo(81f, 32f)
                lineTo(75f, 29f)

                // Aggressive nose bridge wrinkle creases
                moveTo(41f, 53.5f)
                lineTo(47.5f, 55.5f)
                moveTo(67f, 53.5f)
                lineTo(60.5f, 55.5f)

                // Flared snarling upper lip & muzzle lines
                moveTo(35f, 59f)
                quadraticTo(44f, 54f, 50f, 59f)
                moveTo(73f, 59f)
                quadraticTo(64f, 54f, 58f, 59f)

                // Sharp angular nose shield
                moveTo(46f, 59.5f)
                lineTo(54f, 65f)
                lineTo(62f, 59.5f)
                lineTo(54f, 56f)
                close()
            }
            drawPath(browAndSnarl, color = inkColor, style = featureStroke)
            drawPath(roarMawFill, color = inkColor, style = featureStroke)

            // 4. Bared upper & lower razor canine fangs + jagged incisors
            val fangs = Path().apply {
                // Left upper fang
                moveTo(40f, 64f)
                lineTo(43.2f, 75.5f)
                lineTo(46.2f, 64f)
                close()
                // Right upper fang
                moveTo(61.8f, 64f)
                lineTo(64.8f, 75.5f)
                lineTo(68f, 64f)
                close()
                // Upper incisors
                moveTo(48.2f, 64f)
                lineTo(50f, 68f)
                lineTo(52f, 64f)
                lineTo(54f, 68f)
                lineTo(56f, 64f)
                lineTo(58f, 68f)
                lineTo(59.8f, 64f)
                close()
                // Left lower fang
                moveTo(41f, 79.5f)
                lineTo(44f, 70.5f)
                lineTo(46.8f, 79.5f)
                close()
                // Right lower fang
                moveTo(61.2f, 79.5f)
                lineTo(64f, 70.5f)
                lineTo(67f, 79.5f)
                close()
            }
            drawPath(fangs, color = fangColor)

            // 5. Blazing amber slanted predatory eyes + slit pupils
            val fierceEyes = Path().apply {
                moveTo(29f, 43f)
                lineTo(44.5f, 47f)
                lineTo(38.5f, 51f)
                lineTo(31f, 48f)
                close()
                moveTo(79f, 43f)
                lineTo(63.5f, 47f)
                lineTo(69.5f, 51f)
                lineTo(77f, 48f)
                close()
            }
            drawPath(fierceEyes, color = eyeGlow)

            val pupils = Path().apply {
                moveTo(36.6f, 44.7f)
                lineTo(38.6f, 45.2f)
                lineTo(37.4f, 50.2f)
                lineTo(35.6f, 49.6f)
                close()
                moveTo(71.4f, 44.7f)
                lineTo(69.4f, 45.2f)
                lineTo(70.6f, 50.2f)
                lineTo(72.4f, 49.6f)
                close()
            }
            drawPath(pupils, color = pupilColor)

            // 6. Wild forehead claw marks, snarling cheek slashes & chin armor lines
            val wildSlashes = Path().apply {
                moveTo(54f, 22.5f)
                lineTo(54f, 32.5f)
                moveTo(47f, 25.5f)
                lineTo(49.5f, 33.5f)
                moveTo(61f, 25.5f)
                lineTo(58.5f, 33.5f)

                moveTo(22.5f, 59f)
                lineTo(32f, 61.5f)
                moveTo(85.5f, 59f)
                lineTo(76f, 61.5f)

                moveTo(44.5f, 86.5f)
                quadraticTo(54f, 89.5f, 63.5f, 86.5f)
            }
            drawPath(wildSlashes, color = subtleInk, style = detailStroke)
        }
    }
}
