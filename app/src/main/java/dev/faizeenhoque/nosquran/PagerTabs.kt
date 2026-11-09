package dev.faizeenhoque.nosquran

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun NosPagerTabs(state: PagerState, labels: List<String>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val motion = LocalNosMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val indicatorColor = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val slot = size.width / labels.size
            val progress =
                (state.currentPage + state.currentPageOffsetFraction).coerceIn(
                    0f,
                    labels.lastIndex.toFloat(),
                )
            val position = if (rtl) labels.lastIndex - progress else progress
            drawRoundRect(
                indicatorColor,
                Offset(position * slot + 4.dp.toPx(), 4.dp.toPx()),
                Size(slot - 8.dp.toPx(), size.height - 8.dp.toPx()),
                CornerRadius(11.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val press = rememberPressFeedback()
                val emphasis =
                    (1f - abs(state.currentPage + state.currentPageOffsetFraction - index))
                        .coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .then(press.modifier)
                        .selectable(
                            state.currentPage == index,
                            role = Role.Tab,
                            interactionSource = press.source,
                            indication = LocalIndication.current,
                            onClick = {
                                press.confirm()
                                scope.launch {
                                    if (motion.reduced) state.scrollToPage(index)
                                    else
                                        state.animateScrollToPage(
                                            index,
                                            animationSpec =
                                                tween(
                                                    motion.duration(MotionTokens.Standard),
                                                    easing = PrecisionEase,
                                                ),
                                        )
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            lerp(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.onSurface,
                                emphasis,
                            ),
                    )
                }
            }
        }
    }
}
