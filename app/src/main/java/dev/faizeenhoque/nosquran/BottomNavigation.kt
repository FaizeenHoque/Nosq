package dev.faizeenhoque.nosquran

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*

private val primaryTabs =
    listOf(
        Triple("Home", "Today", R.drawable.ic_house),
        Triple("Quran", "Quran", R.drawable.ic_book_open),
        Triple("Search", "Search", R.drawable.ic_search),
        Triple("Saved", "Saved", R.drawable.ic_bookmark),
        Triple("Settings", "Settings", R.drawable.ic_settings),
    )

@Composable
fun NosBottomNavigation(selectedRoute: String, onSelect: (String) -> Unit) {
    val motion = LocalNosMotion.current
    val selectedIndex = primaryTabs.indexOfFirst { it.first == selectedRoute }.coerceAtLeast(0)
    val indicator =
        animateFloatAsState(
            selectedIndex.toFloat(),
            tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase),
            label = "navigation indicator",
        )
    val accent = MaterialTheme.colorScheme.primary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.widthIn(max = 840.dp)
                .fillMaxWidth()
                .height(76.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val slot = size.width / primaryTabs.size
                val position = if (rtl) primaryTabs.lastIndex - indicator.value else indicator.value
                drawRoundRect(
                    accent,
                    Offset(slot * (position + .5f) - 12.dp.toPx(), size.height - 7.dp.toPx()),
                    Size(24.dp.toPx(), 2.dp.toPx()),
                    CornerRadius(2.dp.toPx()),
                )
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                primaryTabs.forEach { (route, label, icon) ->
                    val selected = selectedRoute == route
                    val press = rememberPressFeedback(.965f)
                    val progress =
                        animateFloatAsState(
                            if (selected) 1f else 0f,
                            tween(motion.duration(MotionTokens.Fast), easing = PrecisionEase),
                            label = "tab emphasis",
                        )
                    val tint by
                        animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            tween(motion.duration(MotionTokens.Fast)),
                            label = "tab tint",
                        )
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .then(press.modifier)
                            .selectable(
                                selected,
                                role = Role.Tab,
                                interactionSource = press.source,
                                indication = LocalIndication.current,
                                onClick = {
                                    if (!selected) {
                                        press.confirm()
                                        onSelect(route)
                                    }
                                },
                            )
                            .padding(top = 12.dp, bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painterResource(icon),
                            null,
                            Modifier.size(21.dp).graphicsLayer {
                                scaleX = 1f + .06f * progress.value
                                scaleY = scaleX
                                translationY = -1.5.dp.toPx() * progress.value
                            },
                            tint = tint,
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}
