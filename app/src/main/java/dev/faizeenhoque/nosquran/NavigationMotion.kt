package dev.faizeenhoque.nosquran

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private val primaryRoutes = listOf("Home", "Quran", "Search", "Saved", "Settings")
private val modalRoutes = setOf("player", "details")
private val ReturnEase = CubicBezierEasing(.2f, 0f, .2f, 1f)

private fun String?.section() = this?.substringBefore('/') ?: "Home"

fun NavGraphBuilder.nosDestination(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route) { entry ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content(entry)
        }
    }
}

private fun direction(from: String, to: String, pop: Boolean): Int {
    val a = primaryRoutes.indexOf(from)
    val b = primaryRoutes.indexOf(to)
    return if (a >= 0 && b >= 0) if (b > a) 1 else -1 else if (pop) -1 else 1
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.nosScreenEnter(
    motion: MotionPolicy,
    pop: Boolean = false,
): EnterTransition {
    if (motion.reduced) return EnterTransition.None
    val from = initialState.destination.route.section()
    val to = targetState.destination.route.section()
    val tabs = from in primaryRoutes && to in primaryRoutes
    val duration = motion.duration(if (tabs) MotionTokens.Standard else MotionTokens.Return)
    val fade =
        fadeIn(tween(duration, easing = PrecisionEase), initialAlpha = if (pop) .65f else .2f)
    if (motion.calm) return fade
    // Settings has a deliberate shared-axis entrance even when restored as a root tab.
    if (to == "Settings" || from == "Settings") {
        val sign = (if (motion.rtl) -1 else 1) * direction(from, to, pop)
        return fadeIn(tween(motion.duration(MotionTokens.Return), easing = PrecisionEase)) +
            slideInHorizontally(
                tween(motion.duration(MotionTokens.Return), easing = PrecisionEase)
            ) {
                (sign * it / 4 * motion.travel).toInt()
            }
    }
    if (tabs)
        return fade +
            slideInHorizontally(tween(duration, easing = PrecisionEase)) {
                (direction(from, to, pop) * (if (motion.rtl) -1 else 1) * it / 10 * motion.travel)
                    .toInt()
            }
    if (to in modalRoutes && !pop)
        return fade +
            slideInVertically(tween(motion.duration(MotionTokens.Screen), easing = PrecisionEase)) {
                (it / 7 * motion.travel).toInt()
            }
    if (from in modalRoutes && pop) return fade
    val slide =
        slideInHorizontally(tween(duration, easing = ReturnEase)) {
            (if (motion.rtl) -1 else 1) * (if (pop) -it / 10 else (it / 6 * motion.travel).toInt())
        }
    val depth =
        if (
            !pop &&
                motion.expressive &&
                to in setOf("reciters", "downloads", "stats", "about", "privacy", "licenses")
        )
            scaleIn(tween(duration, easing = PrecisionEase), initialScale = .985f)
        else EnterTransition.None
    return fade + slide + depth
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.nosScreenExit(
    motion: MotionPolicy,
    pop: Boolean = false,
): ExitTransition {
    if (motion.reduced) return ExitTransition.None
    val from = initialState.destination.route.section()
    val to = targetState.destination.route.section()
    val tabs = from in primaryRoutes && to in primaryRoutes
    val duration = motion.duration(if (tabs) MotionTokens.Standard else MotionTokens.Return)
    val fade = fadeOut(tween(duration, easing = LinearEasing))
    if (motion.calm) return fade
    if (to == "Settings" || from == "Settings") {
        val sign = (if (motion.rtl) -1 else 1) * direction(from, to, pop)
        return fadeOut(tween(motion.duration(MotionTokens.Return))) +
            slideOutHorizontally(
                tween(motion.duration(MotionTokens.Return), easing = PrecisionEase)
            ) {
                (-sign * it / 10 * motion.travel).toInt()
            }
    }
    if (tabs)
        return fade +
            slideOutHorizontally(tween(duration, easing = PrecisionEase)) {
                (-direction(from, to, pop) * (if (motion.rtl) -1 else 1) * it / 14 * motion.travel)
                    .toInt()
            }
    if (from in modalRoutes && pop)
        return fade +
            slideOutVertically(
                tween(motion.duration(MotionTokens.Screen), easing = PrecisionEase)
            ) {
                (it / 7 * motion.travel).toInt()
            }
    if (to in modalRoutes && !pop) return fade
    return if (pop)
        slideOutHorizontally(tween(duration, easing = ReturnEase)) { if (motion.rtl) -it else it }
    else
        fade +
            slideOutHorizontally(tween(duration, easing = PrecisionEase)) {
                if (motion.rtl) it / 18 else -it / 18
            }
}
