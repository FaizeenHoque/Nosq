package dev.faizeenhoque.nosquran

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object MotionTokens {
    const val Press = 110
    const val Fast = 140
    const val Standard = 240
    const val Screen = 360
    const val Exit = 220
    const val Return = 320
    const val Departure = 180
    const val Stagger = 35
    const val ActivityCycle = 1200
    val EnterEase = CubicBezierEasing(.16f, 1f, .3f, 1f)
    val ExitEase = CubicBezierEasing(.4f, 0f, 1f, 1f)
}

@Immutable
data class MotionPolicy(
    val reduced: Boolean = false,
    val profile: String = "Balanced",
    val rtl: Boolean = false,
) {
    val expressive
        get() = profile == "Expressive" && !reduced

    val calm
        get() = profile == "Calm" || reduced

    val travel
        get() =
            when {
                reduced -> 0f
                calm -> .45f
                expressive -> 1.18f
                else -> 1f
            }

    fun duration(ms: Int = MotionTokens.Standard) =
        when {
            reduced -> 0
            calm -> (ms * .85f).toInt()
            expressive -> (ms * 1.08f).toInt()
            else -> ms
        }
}

val LocalNosMotion = staticCompositionLocalOf { MotionPolicy() }
val PrecisionEase = MotionTokens.EnterEase

@Composable
fun MotionProvider(
    reduced: Boolean,
    profile: String = "Balanced",
    content: @Composable () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val rtl =
        androidx.compose.ui.platform.LocalLayoutDirection.current ==
            androidx.compose.ui.unit.LayoutDirection.Rtl
    var systemDisabled by remember {
        mutableStateOf(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    systemDisabled =
                        Settings.Global.getFloat(
                            resolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(
        LocalNosMotion provides
            remember(reduced, systemDisabled, profile, rtl) {
                MotionPolicy(reduced || systemDisabled, profile, rtl)
            },
        content = content,
    )
}

@Composable
fun Reveal(visible: Boolean, animateLayout: Boolean = true, content: @Composable () -> Unit) {
    val motion = LocalNosMotion.current
    val duration = if (animateLayout) motion.duration(MotionTokens.Standard) else 0
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration, easing = PrecisionEase)),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration, easing = PrecisionEase)),
    ) {
        content()
    }
}

@Composable
fun MotionGlyph(id: Int, label: String, action: () -> Unit) {
    val motion = LocalNosMotion.current
    val press = rememberPressFeedback(.93f)
    IconButton(
        onClick = {
            press.confirm()
            action()
        },
        interactionSource = press.source,
        modifier = press.modifier,
    ) {
        AnimatedContent(
            id,
            transitionSpec = {
                (fadeIn(tween(motion.duration(MotionTokens.Fast))) +
                    scaleIn(
                        tween(motion.duration(MotionTokens.Fast)),
                        initialScale = .86f,
                    )) togetherWith fadeOut(tween(motion.duration(MotionTokens.Press)))
            },
            label = "control state",
        ) { icon ->
            Icon(painterResource(icon), label, Modifier.size(21.dp))
        }
    }
}

/** Smooths the measured playback clock in the draw phase, without recomposing Arabic text. */
@Composable
fun AudioProgress(audio: Playback) {
    val motion = LocalNosMotion.current
    val fraction =
        if (audio.duration > 0) (audio.position.toFloat() / audio.duration).coerceIn(0f, 1f) else 0f
    val progress =
        animateFloatAsState(
            fraction,
            tween(motion.duration(MotionTokens.Standard), easing = LinearEasing),
            label = "playback clock",
        )
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = NOS.Red,
        trackColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Activity indicator, not an invented waveform or audio amplitude. Only exists during playback. */
@Composable
fun PlaybackActivity(playing: Boolean) {
    val motion = LocalNosMotion.current
    val color = LocalContentColor.current
    if (!playing || motion.reduced) {
        Canvas(Modifier.size(20.dp)) {
            repeat(3) {
                drawCircle(
                    color.copy(alpha = if (playing) 1f else .3f),
                    radius = 1.5.dp.toPx(),
                    center =
                        androidx.compose.ui.geometry.Offset(
                            (4 + it * 6).dp.toPx(),
                            size.height / 2,
                        ),
                )
            }
        }
    } else {
        val transition = rememberInfiniteTransition(label = "playback activity")
        val phase =
            transition.animateFloat(
                0f,
                3f,
                infiniteRepeatable(tween(MotionTokens.ActivityCycle, easing = LinearEasing)),
                label = "dot phase",
            )
        Canvas(Modifier.size(20.dp)) {
            repeat(3) { i ->
                drawCircle(
                    color.copy(alpha = if (phase.value.toInt() % 3 == i) 1f else .25f),
                    radius = 1.5.dp.toPx(),
                    center =
                        androidx.compose.ui.geometry.Offset((4 + i * 6).dp.toPx(), size.height / 2),
                )
            }
        }
    }
}

@Composable
fun ExpandableSettings(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val rotation =
        animateFloatAsState(
            if (expanded) 90f else 0f,
            tween(LocalNosMotion.current.duration(MotionTokens.Standard), easing = PrecisionEase),
            label = "section disclosure",
        )
    Column {
        NosTextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Eyebrow(title, Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                if (expanded) "Collapse $title" else "Expand $title",
                Modifier.size(18.dp).graphicsLayer { rotationZ = rotation.value },
            )
        }
        Reveal(expanded) { Column(content = content) }
    }
}

/** The palette changes once; subsequent frames only update the cached layer's opacity. */
@Composable
fun ThemeReveal(themeKey: String, content: @Composable () -> Unit) {
    val motion = LocalNosMotion.current
    val opacity = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(themeKey) }
    LaunchedEffect(themeKey, motion.reduced) {
        if (previous != themeKey && !motion.reduced) {
            previous = themeKey
            opacity.snapTo(.72f)
            opacity.animateTo(
                1f,
                tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase),
            )
        } else {
            previous = themeKey
            opacity.snapTo(1f)
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }) { content() }
}

@Stable
class PressFeedback(
    val source: MutableInteractionSource,
    val modifier: Modifier,
    val confirm: () -> Unit,
)

@Composable
fun rememberPressFeedback(depth: Float = .975f): PressFeedback {
    val motion = LocalNosMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            if (pressed) 1f - (1f - depth) * motion.travel else 1f,
            tween(motion.duration(MotionTokens.Press), easing = PrecisionEase),
            label = "tactile press",
        )
    val haptic = LocalHapticFeedback.current
    val confirm =
        remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
    return remember(source, scale, confirm) {
        PressFeedback(
            source,
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
            confirm,
        )
    }
}

@Composable
fun NosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPressFeedback()
    Button(
        onClick = {
            press.confirm()
            onClick()
        },
        modifier = modifier.then(press.modifier),
        enabled = enabled,
        interactionSource = press.source,
        colors = colors,
        content = content,
    )
}

@Composable
fun NosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPressFeedback(.985f)
    TextButton(
        onClick = {
            press.confirm()
            onClick()
        },
        modifier = modifier.then(press.modifier),
        enabled = enabled,
        interactionSource = press.source,
        content = content,
    )
}

@Composable
fun NosOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPressFeedback()
    OutlinedButton(
        onClick = {
            press.confirm()
            onClick()
        },
        modifier = modifier.then(press.modifier),
        enabled = enabled,
        interactionSource = press.source,
        content = content,
    )
}

/** Bounded, once-per-visit entrances for interface sections, never for verse glyphs. */
@Composable
fun StaggeredEntrance(order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = LocalNosMotion.current
    var appeared by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (appeared || motion.reduced) 1f else 0f) }
    LaunchedEffect(motion.reduced) {
        if (motion.reduced || appeared) progress.snapTo(1f)
        else {
            delay((order.coerceIn(0, 4) * MotionTokens.Stagger).toLong())
            progress.animateTo(
                1f,
                tween(motion.duration(MotionTokens.Screen), easing = PrecisionEase),
            )
        }
        appeared = true
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx() * motion.travel
        }
    ) {
        content()
    }
}

/** Counter animation is restricted to statistics, never Quran references or verse numbers. */
@Composable
fun AnimatedStatNumber(value: Int, digits: Int = 1, size: TextUnit = 38.sp) {
    val motion = LocalNosMotion.current
    var entered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val count =
        animateIntAsState(
            if (entered || motion.reduced) value else 0,
            tween(motion.duration(MotionTokens.Screen), easing = PrecisionEase),
            label = "statistic count",
        )
    Text(
        count.value.toString().padStart(digits, '0'),
        fontFamily = NDot57,
        fontSize = size,
        modifier =
            Modifier.clearAndSetSemantics {
                text = AnnotatedString(value.toString().padStart(digits, '0'))
            },
    )
}

@Composable
fun BookmarkConfirmation(saved: Boolean, content: @Composable () -> Unit) {
    val motion = LocalNosMotion.current
    AnimatedVisibility(
        saved,
        enter =
            fadeIn(tween(motion.duration(MotionTokens.Fast))) +
                scaleIn(
                    tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase),
                    initialScale = .7f,
                ),
        exit =
            fadeOut(tween(motion.duration(MotionTokens.Fast))) +
                scaleOut(tween(motion.duration(MotionTokens.Fast)), targetScale = .8f),
    ) {
        content()
    }
}

@Composable
fun NosAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val motion = LocalNosMotion.current
    val entrance = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(motion.reduced) {
        if (motion.reduced) entrance.snapTo(1f)
        else
            entrance.animateTo(
                1f,
                tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase),
            )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        modifier =
            Modifier.graphicsLayer {
                alpha = entrance.value
                scaleX = .97f + .03f * entrance.value
                scaleY = scaleX
            },
    )
}
