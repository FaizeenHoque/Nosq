package dev.faizeenhoque.nosquran

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

object NOS {
    val Red = Color(0xFFBD2028)
    val Space = 8.dp
    val Corner = RoundedCornerShape(20.dp)
    const val Motion = 180
}

@Composable
fun NosTheme(settings: ReaderSettings, content: @Composable () -> Unit) {
    MotionProvider(settings.reduceMotion, settings.motionProfile) {
        val dark = settings.theme == "Dark" || settings.theme == "System" && isSystemInDarkTheme()
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        val accent =
            if (settings.redAccent) {
                if (dark) Color(0xFFFF6268) else NOS.Red
            } else if (dark) Color.White else Color.Black
        val palette =
            if (dark)
                darkColorScheme(
                    primary = accent,
                    onPrimary = Color.Black,
                    background = if (settings.oled) Color.Black else Color(0xFF101011),
                    surface = Color(0xFF1C1C1E),
                    surfaceVariant = Color(0xFF28282B),
                    onSurface = Color(0xFFF2F1ED),
                    onBackground = Color(0xFFF2F1ED),
                    onSurfaceVariant = Color(0xFFAEAEAF),
                    outlineVariant = Color(0xFF363639),
                    secondaryContainer = Color(0xFF333335),
                    onSecondaryContainer = Color.White,
                )
            else
                lightColorScheme(
                    primary = accent,
                    onPrimary = Color.White,
                    background = Color(0xFFF4F3EF),
                    surface = Color(0xFFFEFDF9),
                    surfaceVariant = Color(0xFFE9E8E3),
                    onSurface = Color(0xFF171718),
                    onBackground = Color(0xFF171718),
                    onSurfaceVariant = Color(0xFF636361),
                    outlineVariant = Color(0xFFD7D6D0),
                    secondaryContainer = Color(0xFFE4E3DD),
                    onSecondaryContainer = Color(0xFF171718),
                )
        fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
            TextStyle(
                fontFamily = Geist,
                fontSize = size.sp,
                fontWeight = weight,
                lineHeight = (size * 1.45).sp,
            )
        MaterialTheme(
            colorScheme = palette,
            typography =
                Typography(
                    displayLarge =
                        TextStyle(fontFamily = NDot57, fontSize = 64.sp, lineHeight = 64.sp),
                    displayMedium = TextStyle(fontFamily = NDot57, fontSize = 44.sp),
                    headlineLarge = style(34, FontWeight.Medium),
                    headlineMedium = style(28, FontWeight.Medium),
                    headlineSmall = style(24, FontWeight.Medium),
                    titleLarge = style(22, FontWeight.Medium),
                    titleMedium = style(16, FontWeight.SemiBold),
                    titleSmall = style(14, FontWeight.Medium),
                    bodyLarge = style(16),
                    bodyMedium = style(14),
                    bodySmall = style(12),
                    labelLarge = style(14, FontWeight.Medium),
                    labelMedium = style(12, FontWeight.Medium),
                    labelSmall = style(10, FontWeight.Medium),
                ),
            shapes =
                Shapes(
                    small = RoundedCornerShape(8.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = NOS.Corner,
                    extraLarge = RoundedCornerShape(28.dp),
                ),
            content = { ThemeReveal("$dark:${settings.oled}:${settings.redAccent}", content) },
        )
    }
}

@Composable
fun Glyph(id: Int, label: String, action: () -> Unit) {
    MotionGlyph(id, label, action)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.7.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun PageTitle(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(subtitle)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.displayMedium)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, detail: String = "") {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(title, Modifier.weight(1f))
        if (detail.isNotEmpty()) Eyebrow(detail)
    }
}

@Composable
fun NosPanel(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = NOS.Corner,
        color =
            if (dark) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
        contentColor =
            if (dark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    detail: String,
    icon: Int? = null,
    action: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val press = rememberPressFeedback(.99f)
    Row(
        Modifier.fillMaxWidth()
            .then(if (action != null) press.modifier else Modifier)
            .then(
                if (action != null)
                    Modifier.clickable(
                        interactionSource = press.source,
                        indication = LocalIndication.current,
                        onClick = {
                            press.confirm()
                            action()
                        },
                    )
                else Modifier
            )
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null)
            Icon(
                painterResource(icon),
                null,
                Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty())
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (trailing != null) trailing()
        else if (action != null)
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

@Composable
fun ToggleRow(title: String, detail: String, value: Boolean, change: (Boolean) -> Unit) {
    val press = rememberPressFeedback()
    SettingRow(
        title,
        detail,
        trailing = {
            Switch(
                checked = value,
                modifier = Modifier.semantics { contentDescription = title }.then(press.modifier),
                interactionSource = press.source,
                onCheckedChange = {
                    press.confirm()
                    change(it)
                },
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    )
}

@Composable
fun ChoiceRow(title: String, choices: List<String>, value: String, change: (String) -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                val press = rememberPressFeedback()
                FilterChip(
                    selected = value == choice,
                    onClick = {
                        if (value != choice) press.confirm()
                        change(choice)
                    },
                    modifier = press.modifier,
                    interactionSource = press.source,
                    label = { Text(choice) },
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: Int = R.drawable.ic_book_open) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SurahRow(
    s: Surah,
    progress: Float = 0f,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    open: () -> Unit,
) {
    val animatedProgress =
        animateFloatAsState(
            progress,
            tween(LocalNosMotion.current.duration(MotionTokens.Standard), easing = PrecisionEase),
            label = "surah progress",
        )
    val press = rememberPressFeedback(.985f)
    Row(
        modifier
            .fillMaxWidth()
            .then(press.modifier)
            .clickable(
                interactionSource = press.source,
                indication = LocalIndication.current,
                onClick = {
                    press.confirm()
                    open()
                },
            )
            .padding(vertical = if (compact) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(s.id.toString().padStart(2, '0'), fontFamily = NDot57, fontSize = 24.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                s.name,
                modifier = Modifier,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${s.meaning} · ${s.count} ayahs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress > 0)
                LinearProgressIndicator(
                    progress = { animatedProgress.value },
                    modifier = Modifier.width(80.dp).height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                s.arabic,
                modifier = Modifier.widthIn(max = 110.dp),
                fontFamily = AmiriQuran,
                fontSize = 23.sp,
                lineHeight = 40.sp,
            )
            Eyebrow(s.origin)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
}
