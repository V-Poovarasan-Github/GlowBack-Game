package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PASS 5.25: Perfect Neon Splash Screen Centering & Animation.
 *
 * - Background: Dark navy (#0F172A).
 * - Exact Dead-Center: 120dp Glowback App Icon and Ripple Canvas share the exact same
 *   center anchor, ensuring rings radiate with 100% mathematical symmetry from the pads.
 * - Title Anchor: "GLOWBACK" text with green/red signature dots is offset directly below
 *   the center anchor (+104dp) so it never pushes the icon off center.
 * - Animation: 1200ms one-shot ripple expansion with FastOutSlowInEasing, followed by a
 *   300ms smooth dissolve fade into the game screen.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    val splashBgColor = Color(0xFF0F172A)
    val neonCyan = Color(0xFF00E5FF)
    val neonBlue = Color(0xFF38BDF8)
    val dotGreen = Color(0xFF00E676)
    val dotRed = Color(0xFFFF1744)

    // 1200ms smooth one-shot ripple expansion
    var startPulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startPulse = true
    }

    val rippleProgress by animateFloatAsState(
        targetValue = if (startPulse) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "SplashRippleProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(splashBgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Center Container: App Icon + Ripple Canvas sharing the exact same center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(360.dp)
                .semantics { contentDescription = "Splash Ripples" }
                .testTag("splash_ripples"),
            contentAlignment = Alignment.Center
        ) {
            // Ripple Canvas centered behind the icon
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 60.dp.toPx()
                val maxRadius = 165.dp.toPx()

                // Staggered concentric ripple rings
                val rings = listOf(
                    rippleProgress,
                    ((rippleProgress - 0.22f) / 0.78f).coerceIn(0f, 1f),
                    ((rippleProgress - 0.44f) / 0.56f).coerceIn(0f, 1f)
                )

                for (p in rings) {
                    if (p in 0.01f..0.999f) {
                        val radius = baseRadius + (maxRadius - baseRadius) * p
                        val alpha = (1f - p).coerceIn(0f, 1f) * 0.8f
                        // Outer fine neon ring
                        drawCircle(
                            color = neonCyan.copy(alpha = alpha),
                            radius = radius,
                            center = centerOffset,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Softer halo ring slightly inside
                        drawCircle(
                            color = neonBlue.copy(alpha = alpha * 0.45f),
                            radius = (radius - 2.5.dp.toPx()).coerceAtLeast(0f),
                            center = centerOffset,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
            }

            // Authentic 120dp Glowback App Icon at exact dead-center
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = neonCyan,
                        spotColor = neonCyan
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.glowback_app_icon),
                    contentDescription = "Glowback App Icon",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("splash_icon")
                )
            }
        }

        // Signature Dots + "GLOWBACK" Title anchored below the center anchor
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 104.dp)
                .testTag("splash_title")
        ) {
            // Green signature dot
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(dotGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Red signature dot
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(dotRed)
            )
            Spacer(modifier = Modifier.width(14.dp))
            // Bold white title
            Text(
                text = "GLOWBACK",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}
