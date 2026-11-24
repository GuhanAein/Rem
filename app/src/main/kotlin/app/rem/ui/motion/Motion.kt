package app.rem.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Shared motion tokens so the whole app moves with one personality: quick, soft, a little bouncy. */
object MotionTokens {
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val snappy = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    const val STAGGER_MS = 55L
}

/** When false (tests, reduced motion), infinite ambient animations freeze on a pleasant frame. */
val LocalAmbientMotion = compositionLocalOf { true }

/** Scales down a touch while pressed and springs back: makes every tap feel physical. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MotionTokens.bouncy, label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Rises and fades in once, staggered by [index]. Items already shown (tracked in [seen]) don't replay
 * when they scroll back into view.
 */
fun Modifier.staggeredEntrance(key: Any, index: Int, seen: MutableSet<Any>, enabled: Boolean = true): Modifier = composed {
    val play = enabled && LocalAmbientMotion.current && key !in seen
    val progress = remember(key) { Animatable(if (play) 0f else 1f) }
    val distance = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(key) {
        if (play) {
            seen += key
            delay(index.coerceAtMost(10) * MotionTokens.STAGGER_MS)
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * distance
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** A diagonal light sweep that glides across the content every few seconds. */
fun Modifier.shimmer(periodMs: Int = 2800, enabled: Boolean = true): Modifier = composed {
    if (!enabled || !LocalAmbientMotion.current) return@composed this
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "sweep",
    )
    drawWithContent {
        drawContent()
        val x = size.width * t
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(x - size.width * 0.35f, 0f),
                end = Offset(x, size.height),
            ),
            blendMode = BlendMode.SrcOver,
        )
    }
}

/** Gentle vertical bob, like something floating. */
fun Modifier.floating(amplitudeDp: Float = 6f, periodMs: Int = 3200, phase: Float = 0f): Modifier = composed {
    val ambient = LocalAmbientMotion.current
    if (!ambient) return@composed this
    val t by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t",
    )
    val px = with(LocalDensity.current) { amplitudeDp.dp.toPx() }
    graphicsLayer {
        val v = if (ambient) t else 0.25f
        translationY = sin((v + phase) * 2f * PI.toFloat()) * px
    }
}

/** A bell-like wobble: a quick shake, then rest. */
fun Modifier.ringing(periodMs: Int = 2600, maxDegrees: Float = 16f): Modifier = composed {
    val ambient = LocalAmbientMotion.current
    if (!ambient) return@composed this
    val t by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t",
    )
    graphicsLayer {
        val v = if (ambient) t else 0.9f
        // Shake during the first 30% of the cycle, decaying.
        rotationZ = if (v < 0.3f) {
            val k = v / 0.3f
            sin(k * 6f * PI.toFloat()) * maxDegrees * (1f - k)
        } else 0f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f)
    }
}

/**
 * A living gradient: soft colour blobs drift slowly over a base brush. Used behind the hero,
 * the alarm screen and onboarding.
 */
@Composable
fun AuroraBackground(
    base: Brush,
    blobs: List<Color>,
    modifier: Modifier = Modifier,
    speedMs: Int = 14000,
) {
    val time = if (LocalAmbientMotion.current) {
        val t by rememberInfiniteTransition(label = "aurora").animateFloat(
            0f, 1f, infiniteRepeatable(tween(speedMs, easing = LinearEasing)), label = "t",
        )
        t
    } else 0.18f
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        blobs.forEachIndexed { i, color ->
            val a = (time + i / blobs.size.toFloat()) * 2f * PI.toFloat()
            val cx = w * (0.5f + 0.42f * cos(a + i))
            val cy = h * (0.45f + 0.35f * sin(a * (1 + i % 2) + i * 1.7f))
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r * 0.55f),
                radius = r * 0.55f,
                center = Offset(cx, cy),
            )
        }
    }
}

private data class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float, val isRect: Boolean)

private val CONFETTI_COLORS = listOf(
    Color(0xFF5B4BFF), Color(0xFFA855F7), Color(0xFFFF6B6B), Color(0xFFF59E0B),
    Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFFEC4899),
)

/**
 * A one-shot confetti burst. Change [trigger] to fire again. Drawn in an overlay box that fills
 * its parent; particles fly from [origin] (fraction of the box) with gravity.
 */
@Composable
fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier, origin: Offset = Offset(0.5f, 0.4f), count: Int = 70, durationMs: Int = 1400) {
    if (trigger == null) return
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        val rnd = Random(trigger.hashCode())
        List(count) {
            Particle(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.75f,
                color = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)],
                size = 5f + rnd.nextFloat() * 7f,
                spin = (rnd.nextFloat() - 0.5f) * 1080f,
                isRect = rnd.nextBoolean(),
            )
        }
    }
    LaunchedEffect(trigger) { launch { progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) } }
    val p = progress.value
    if (p >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val o = Offset(size.width * origin.x, size.height * origin.y)
        val reach = minOf(size.width, size.height) * 0.6f
        val dp = density
        particles.forEach { pt ->
            val d = reach * pt.speed * p
            val x = o.x + cos(pt.angle) * d
            val y = o.y + sin(pt.angle) * d + (p * p) * reach * 0.9f // gravity
            val alpha = (1f - p).coerceIn(0f, 1f)
            val s = pt.size * dp
            rotate(pt.spin * p, Offset(x, y)) {
                if (pt.isRect) drawRect(pt.color.copy(alpha = alpha), Offset(x - s / 2, y - s / 4), androidx.compose.ui.geometry.Size(s, s / 2))
                else drawCircle(pt.color.copy(alpha = alpha), s / 2.5f, Offset(x, y))
            }
        }
    }
}

/** Numbers that roll up or down when they change, like an odometer. */
@Composable
fun AnimatedCounter(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn()) togetherWith
                (slideOutVertically { if (up) -it else it } + fadeOut())
        },
        modifier = modifier,
        label = "counter",
    ) { v -> Text("$v", style = style, color = color) }
}

/** Counts from 0 to [target] once on first show. */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 900): Int {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    return anim.value.toInt()
}

// ----- Shared element plumbing (card -> edit screen "container transform") -----

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks a shared element; a no-op when not inside a shared transition (e.g. previews, tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedElement(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalNavAnimatedScope.current
    if (shared == null || anim == null) return@composed this
    with(shared) {
        this@composed.sharedElement(
            rememberSharedContentState(key),
            anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 380f) },
        )
    }
}

/** Shared bounds (the container morphs; content cross-fades). */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBounds(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalNavAnimatedScope.current
    if (shared == null || anim == null) return@composed this
    with(shared) {
        this@composed.sharedBounds(
            rememberSharedContentState(key),
            anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** True unless the user turned animations off in Android's accessibility/developer settings. */
fun systemAnimationsEnabled(context: android.content.Context): Boolean =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
