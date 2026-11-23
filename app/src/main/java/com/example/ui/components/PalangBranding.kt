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
            alpha = if (isDark) 0.78f else 0.56f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor.copy(alpha = if (isDark) 0.24f else 0.30f))
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * Fierce roaring wild panther emblem with jagged predator contours, furrowed V-brows,
 * blazing amber slanted eyes, bared razor canine fangs, and forehead claw slash marks.
 */
@Composable
fun PalangLineArtLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PalaNG Logo",
    forceDarkInk: Boolean? = null
) {
    val isDark = forceDarkInk ?: LocalIsDarkTheme.current
    val inkColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val headFill = if (isDark) Color(0xFF141923) else Color(0xFFF1F5F9)
    val roarMawFill = Color(0xFF080B10)
    val fangsColor = Color(0xFFFFFFFF)
    val eyeAmber = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val slashAmber = if (isDark) Color(0xFFFFAB2E) else Color(0xFFD97706)
    val pupilColor = Color(0xFF070A0F)

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
            val contourStroke = Stroke(
                width = 3.0f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Miter
            )
            val detailStroke = Stroke(
                width = 2.15f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Miter
            )
            val slashStroke = Stroke(
                width = 2.2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // 1. Sharp pinned-back ears & jagged roaring panther head silhouette
            val headContour = Path().apply {
                moveTo(54f, 22f)
                lineTo(41f, 25.5f)
                lineTo(22f, 18f)
                lineTo(26f, 35.5f)
                lineTo(12f, 49f)
                lineTo(19.5f, 54.5f)
                lineTo(14.5f, 64.5f)
                lineTo(26.5f, 70f)
                lineTo(30.5f, 85f)
                lineTo(41f, 92f)
                lineTo(54f, 96f)
                lineTo(67f, 92f)
                lineTo(77.5f, 85f)
                lineTo(81.5f, 70f)
                lineTo(93.5f, 64.5f)
                lineTo(88.5f, 54.5f)
                lineTo(96f, 49f)
                lineTo(82f, 35.5f)
                lineTo(86f, 18f)
                lineTo(67f, 25.5f)
                close()
            }
            drawPath(headContour, color = headFill)

            // 2. Dark roaring open maw cavity
            val mawPath = Path().apply {
                moveTo(35.5f, 68f)
                lineTo(44f, 61.5f)
                lineTo(54f, 59.5f)
                lineTo(64f, 61.5f)
                lineTo(72.5f, 68f)
                lineTo(69f, 86f)
                lineTo(54f, 91f)
                lineTo(39f, 86f)
                close()
            }
            drawPath(mawPath, color = roarMawFill)

            // 3. Bared razor canine fangs & sharp incisors
            val leftUpperFang = Path().apply {
                moveTo(39.2f, 67f)
                lineTo(43.5f, 82.8f)
                lineTo(47.4f, 68.2f)
                close()
            }
            val rightUpperFang = Path().apply {
                moveTo(68.8f, 67f)
                lineTo(64.5f, 82.8f)
                lineTo(60.6f, 68.2f)
                close()
            }
            val leftLowerFang = Path().apply {
                moveTo(43f, 85.2f)
                lineTo(46f, 74.5f)
                lineTo(49f, 85.2f)
                close()
            }
            val rightLowerFang = Path().apply {
                moveTo(65f, 85.2f)
                lineTo(62f, 74.5f)
                lineTo(59f, 85.2f)
                close()
            }
            val upperIncisors = Path().apply {
                moveTo(48.5f, 66.2f)
                lineTo(50.3f, 71.2f)
                lineTo(52.1f, 66.5f)
                lineTo(54f, 71.5f)
                lineTo(55.9f, 66.5f)
                lineTo(57.7f, 71.2f)
                lineTo(59.5f, 66.2f)
                close()
            }
            drawPath(leftUpperFang, color = fangsColor)
            drawPath(rightUpperFang, color = fangsColor)
            drawPath(leftLowerFang, color = fangsColor)
            drawPath(rightLowerFang, color = fangsColor)
            drawPath(upperIncisors, color = fangsColor)

            // 4. Outer fierce contour stroke
            drawPath(headContour, color = inkColor, style = contourStroke)

            // 5. Blazing amber slanted predatory eyes + vertical slit pupils
            val leftEye = Path().apply {
                moveTo(29.5f, 41.5f)
                lineTo(45.5f, 48.5f)
                lineTo(39f, 53.2f)
                lineTo(28.5f, 48f)
                close()
            }
            val rightEye = Path().apply {
                moveTo(78.5f, 41.5f)
                lineTo(62.5f, 48.5f)
                lineTo(69f, 53.2f)
                lineTo(79.5f, 48f)
                close()
            }
            drawPath(leftEye, color = eyeAmber)
            drawPath(rightEye, color = eyeAmber)

            val leftPupil = Path().apply {
                moveTo(36.8f, 44.3f)
                lineTo(38.1f, 48.2f)
                lineTo(36.8f, 51.8f)
                lineTo(35.5f, 48.2f)
                close()
            }
            val rightPupil = Path().apply {
                moveTo(71.2f, 44.3f)
                lineTo(72.5f, 48.2f)
                lineTo(71.2f, 51.8f)
                lineTo(69.9f, 48.2f)
                close()
            }
            drawPath(leftPupil, color = pupilColor)
            drawPath(rightPupil, color = pupilColor)

            // 6. Furrowed V-brow ridges, nose shield & snarling cheek creases
            val facialDetails = Path().apply {
                // Aggressive V-brow ridges
                moveTo(29f, 35.5f)
                lineTo(49f, 46.8f)
                lineTo(54f, 48.8f)
                lineTo(59f, 46.8f)
                lineTo(79f, 35.5f)

                // Angular nose shield
                moveTo(47.5f, 54f)
                lineTo(54f, 51.5f)
                lineTo(60.5f, 54f)
                lineTo(57.2f, 59.8f)
                lineTo(54f, 61.8f)
                lineTo(50.8f, 59.8f)
                close()

                // Snarling upper lip arch
                moveTo(35.5f, 68f)
                lineTo(44f, 61.5f)
                lineTo(54f, 59.5f)
                lineTo(64f, 61.5f)
                lineTo(72.5f, 68f)

                // Predator whisker / cheek slashes
                moveTo(21f, 57f)
                lineTo(32.5f, 59f)
                moveTo(87f, 57f)
                lineTo(75.5f, 59f)
                moveTo(23f, 63f)
                lineTo(31.5f, 64f)
                moveTo(85f, 63f)
                lineTo(76.5f, 64f)
            }
            drawPath(facialDetails, color = inkColor, style = detailStroke)

            // 7. Signature amber forehead claw slash marks
            val slashMarks = Path().apply {
                moveTo(47.5f, 28f)
                lineTo(50f, 36.5f)
                moveTo(54f, 26f)
                lineTo(54f, 38.5f)
                moveTo(60.5f, 28f)
                lineTo(58f, 36.5f)
            }
            drawPath(slashMarks, color = slashAmber, style = slashStroke)
        }
    }
}
