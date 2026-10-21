@file:OptIn(ExperimentalAnimationApi::class)

package com.aura.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun isDarkTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Frosted-glass surface: translucent gradient fill + bright rim light. */
@Composable
fun Modifier.glass(shape: Shape, forceDark: Boolean? = null): Modifier {
    val dark = forceDark ?: isDarkTheme()
    val base = if (dark) Color.White else Color.Black
    val fill = Brush.linearGradient(listOf(base.copy(if (dark) 0.16f else 0.07f), base.copy(if (dark) 0.05f else 0.03f)))
    val rim = Brush.linearGradient(
        listOf(base.copy(if (dark) 0.38f else 0.14f), base.copy(if (dark) 0.04f else 0.02f), base.copy(if (dark) 0.18f else 0.08f))
    )
    return this.clip(shape).background(fill, shape).border(1.dp, rim, shape)
}

/** Approximated motion blur: blur radius is animated by the transition, 0 = crystal clear. */
fun Modifier.motionBlur(radius: Dp): Modifier = if (radius > 0.5.dp) this.blur(radius) else this

/**
 * GPU blur whose radius is read lazily at draw time: animating it never recomposes anything
 * (Modifier.blur(radius) would recompose every frame). No-op below Android 12.
 */
fun Modifier.motionBlurLazy(radius: () -> Dp): Modifier = this.graphicsLayer {
    val px = radius().toPx()
    renderEffect = if (px > 1f) BlurEffect(px, px, TileMode.Clamp) else null
}

/** Springy press feedback instead of a flat ripple. */
@Composable
fun Modifier.bouncyClick(pressed: Float = 0.95f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (down) pressed else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** One-shot rise + fade entrance. */
@Composable
fun Modifier.fadeUp(delayMs: Int = 0): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }
}

/**
 * Slowly drifting accent-coloured light behind the whole app. The gradients are drawn once into GPU layers and only
 * the layers' translation is animated, so it costs almost nothing per frame.
 */
@Composable
fun AuroraBackground() {
    val dark = isDarkTheme()
    val t = rememberInfiniteTransition(label = "aurora")
    val p1 = t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a1")
    val p2 = t.animateFloat(0f, 1f, infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse), label = "a2")
    val base = if (dark) Color.Black else Color(0xFFF6F6F9)
    val k = if (dark) 0.34f else 0.22f
    val acc = Accent
    BoxWithConstraints(Modifier.fillMaxSize().background(base)) {
        val w = maxWidth; val h = maxHeight
        val blob = maxOf(w, h) * 1.3f
        Box(
            Modifier.size(blob).offset(x = -blob * 0.3f, y = -blob * 0.3f)
                .graphicsLayer { translationX = p1.value * w.toPx() * 0.6f; translationY = p2.value * h.toPx() * 0.25f }
                .background(Brush.radialGradient(listOf(acc.copy(k), Color.Transparent)))
        )
        Box(
            Modifier.size(blob).offset(x = w - blob * 0.7f, y = h - blob * 0.7f)
                .graphicsLayer { translationX = -p2.value * w.toPx() * 0.5f; translationY = -p1.value * h.toPx() * 0.3f }
                .background(Brush.radialGradient(listOf(Color(0xFF5E5CE6).copy(k * 0.8f), Color.Transparent)))
        )
    }
}

/** Content that is blurred while entering/leaving an AnimatedVisibility / AnimatedContent and sharp once settled. */
@Composable
fun AnimatedVisibilityScope.MotionBlurBox(maxBlur: Dp = 16.dp, content: @Composable () -> Unit) {
    val r = transition.animateDp(transitionSpec = { tween(420) }, label = "mb") { if (it == EnterExitState.Visible) 0.dp else maxBlur }
    Box(Modifier.fillMaxSize().motionBlurLazy { r.value }) { content() }
}

@Composable
fun PlayPauseIcon(playing: Boolean, size: Dp, tint: Color) {
    AnimatedContent(
        playing, contentAlignment = Alignment.Center, label = "pp",
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(spring(0.6f, 500f), initialScale = 0.4f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(targetScale = 0.4f))
        }
    ) { p -> Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = tint, modifier = Modifier.size(size)) }
}

@Composable
fun HeartIcon(on: Boolean, offTint: Color) {
    val s = remember { Animatable(1f) }
    LaunchedEffect(on) { if (on) { s.snapTo(0.5f); s.animateTo(1f, spring(0.3f, 380f)) } }
    Icon(
        if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (on) Accent else offTint,
        modifier = Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }
    )
}

/**
 * Custom slider: track and thumb share one centre line (the Material slider mis-centres custom thumbs),
 * and the thumb springs larger while dragged.
 */
@Composable
fun AuraSlider(
    value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit,
    activeColor: Color, inactiveColor: Color, modifier: Modifier = Modifier,
    thumbSize: Dp = 14.dp, trackHeight: Dp = 4.dp
) {
    val density = LocalDensity.current
    var w by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1.45f else 1f, spring(0.5f, 500f), label = "thumb")
    val v = value.coerceIn(0f, 1f)
    val thumbPx = with(density) { thumbSize.toPx() }
    val r = thumbPx / 2f
    val travel = (w - thumbPx).coerceAtLeast(1f)
    // The gesture handlers below live for the whole composition (pointerInput(Unit)), so they must read the
    // *current* width and callbacks, not the values captured on first composition (width was still 0 then,
    // which made every touch map to 100% and skipped the song).
    val widthState = rememberUpdatedState(w)
    val onChange = rememberUpdatedState(onValueChange)
    val onFinish = rememberUpdatedState(onValueChangeFinished)
    fun map(x: Float): Float {
        val t = (widthState.value - thumbPx).coerceAtLeast(1f)
        return ((x - r) / t).coerceIn(0f, 1f)
    }
    Box(
        modifier.fillMaxWidth().height(40.dp).onSizeChanged { w = it.width }
            .pointerInput(Unit) { detectTapGestures { o -> onChange.value(map(o.x)); onFinish.value() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; onChange.value(map(o.x)) },
                    onDragEnd = { dragging = false; onFinish.value() },
                    onDragCancel = { dragging = false; onFinish.value() }
                ) { change, _ -> change.consume(); onChange.value(map(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape).background(inactiveColor))
        Box(Modifier.width(with(density) { (r + v * travel).toDp() }).height(trackHeight).clip(CircleShape).background(activeColor))
        Box(
            Modifier.offset { IntOffset((v * travel).roundToInt(), 0) }.size(thumbSize)
                .graphicsLayer { scaleX = grow; scaleY = grow }
                .shadow(4.dp, CircleShape).background(activeColor, CircleShape)
        )
    }
}
