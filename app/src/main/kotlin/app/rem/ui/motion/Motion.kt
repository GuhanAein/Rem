package app.rem.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Motion principles: quiet and physical. Things respond instantly to touch, settle with a soft spring,
 * and never move just to decorate.
 */
object MotionTokens {
    val gentle = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    const val STAGGER_MS = 30L
}

/** False in tests and when the user turned animations off: entrances and loops are skipped. */
val LocalAmbientMotion = compositionLocalOf { true }

/** A barely-there press response (like a physical button). */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MotionTokens.gentle, label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades in with a short rise, once; staggered slightly by [index]. */
fun Modifier.staggeredEntrance(key: Any, index: Int, seen: MutableSet<Any>, enabled: Boolean = true): Modifier = composed {
    val play = enabled && LocalAmbientMotion.current && key !in seen
    val progress = remember(key) { Animatable(if (play) 0f else 1f) }
    val distance = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(key) {
        if (play) {
            seen += key
            delay(index.coerceAtMost(8) * MotionTokens.STAGGER_MS)
            progress.animateTo(1f, tween(320))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance
    }
}

/** Numbers that roll when they change. */
@Composable
fun AnimatedCounter(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(220)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(180)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(180)))
        },
        modifier = modifier,
        label = "counter",
    ) { v -> Text("$v", style = style, color = color) }
}

// ----- Shared element plumbing (row -> details "container transform") -----

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedElement(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalNavAnimatedScope.current
    if (shared == null || anim == null) return@composed this
    with(shared) {
        this@composed.sharedElement(rememberSharedContentState(key), anim, boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = 420f) })
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBounds(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val anim = LocalNavAnimatedScope.current
    if (shared == null || anim == null) return@composed this
    with(shared) {
        this@composed.sharedBounds(
            rememberSharedContentState(key), anim,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.9f, stiffness = 420f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** True unless the user turned animations off in Android settings. */
fun systemAnimationsEnabled(context: android.content.Context): Boolean =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
