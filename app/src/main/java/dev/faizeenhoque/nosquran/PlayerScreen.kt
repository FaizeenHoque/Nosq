package dev.faizeenhoque.nosquran

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(repo: QuranRepository, audio: Playback, expand: () -> Unit) {
    val v = repo.verse(audio.key) ?: return
    Surface(
        color = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.background,
    ) {
        Column {
            AudioProgress(audio)
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f)
                        .testTag("mini-player")
                        .clickable(onClick = expand)
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        if (audio.verseLevel) "${repo.surahs[v.surah-1].name} · ${v.number}"
                        else repo.surahs[v.surah - 1].name,
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (audio.error != null) "Audio unavailable · tap to retry"
                        else if (audio.buffering) "Connecting…" else "RECITATION",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                    )
                }
                Glyph(
                    if (audio.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (audio.playing) "Pause" else "Play",
                ) {
                    audio.toggle()
                }
                Glyph(R.drawable.ic_x, "Close player") { audio.stop() }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    repo: QuranRepository,
    state: QuranState,
    audio: Playback,
    open: (String) -> Unit,
    navigate: (String) -> Unit,
) {
    val v = repo.verse(audio.key)
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val playPress = rememberPressFeedback(.93f)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle("RECITATION", "Listen. Reflect. Repeat.")
        if (v == null) {
            EmptyState(
                "A quiet space",
                "Play an ayah from the reader to begin listening.",
                R.drawable.ic_headphones,
            )
            return@Column
        }
        NosPanel(Modifier.fillMaxWidth(), dark = true) {
            PlaybackActivity(audio.playing)
            Text(
                (if (audio.verseLevel) v.number else v.surah).toString().padStart(3, '0'),
                fontFamily = NDot57,
                fontSize = 76.sp,
            )
            Text(
                repo.surahs[v.surah - 1].name,
                modifier = Modifier,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                if (audio.verseLevel) "Ayah ${v.number} / ${repo.surahs[v.surah-1].count}"
                else "Full Surah recording"
            )
            val motion = LocalNosMotion.current
            AnimatedContent(
                state.settings.reciter,
                transitionSpec = {
                    (fadeIn(tween(motion.duration(MotionTokens.Standard))) +
                        slideInVertically(
                            tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase)
                        ) {
                            it / 4
                        }) togetherWith fadeOut(tween(motion.duration(MotionTokens.Fast)))
                },
                label = "reciter change",
            ) { reciter ->
                Text(ReciterCatalog.label(reciter), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            if (audio.verseLevel) ArabicText(v.arabic, 26f, 1.9f)
            else Text(repo.surahs[v.surah - 1].arabic, fontFamily = AmiriQuran, fontSize = 30.sp)
        }
        Reveal(audio.buffering) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackActivity(true)
                Eyebrow("Preparing recitation")
            }
        }
        audio.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
            NosButton(onClick = { audio.play(repo, v, state.settings) }) { Text("Retry playback") }
        }
        audio.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlayerTimeline(audio)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Glyph(
                if (audio.repeat) R.drawable.ic_repeat_1 else R.drawable.ic_repeat,
                if (audio.repeat) "Disable repeat" else "Repeat queue or range",
            ) {
                audio.updateRepeat(!audio.repeat)
            }
            Glyph(
                R.drawable.ic_skip_back,
                if (audio.verseLevel) "Previous ayah" else "Previous Surah",
            ) {
                if (audio.verseLevel)
                    repo.verses.getOrNull(v.index - 1)?.let { audio.play(repo, it, state.settings) }
                else
                    ReciterCatalog.resolve(state.settings.reciter)
                        ?.surahs
                        ?.filter { it < v.surah }
                        ?.maxOrNull()
                        ?.let { audio.play(repo, repo.chapter(it).first(), state.settings) }
            }
            FilledIconButton(
                onClick = {
                    playPress.confirm()
                    audio.toggle()
                },
                interactionSource = playPress.source,
                modifier = Modifier.size(76.dp).then(playPress.modifier),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
            ) {
                val motion = LocalNosMotion.current
                AnimatedContent(
                    audio.playing,
                    transitionSpec = {
                        fadeIn(tween(motion.duration(MotionTokens.Fast))) togetherWith
                            fadeOut(tween(motion.duration(MotionTokens.Fast)))
                    },
                    label = "play pause",
                ) { playing ->
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        if (playing) "Pause" else "Play",
                        Modifier.size(30.dp),
                    )
                }
            }
            Glyph(R.drawable.ic_skip_forward, if (audio.verseLevel) "Next ayah" else "Next Surah") {
                if (audio.verseLevel)
                    repo.verses.getOrNull(v.index + 1)?.let { audio.play(repo, it, state.settings) }
                else
                    ReciterCatalog.resolve(state.settings.reciter)
                        ?.surahs
                        ?.filter { it > v.surah }
                        ?.minOrNull()
                        ?.let { audio.play(repo, repo.chapter(it).first(), state.settings) }
            }
            Glyph(R.drawable.ic_book_open, "Read current ayah") { open(v.key) }
        }
        SettingRow(
            "Reciter",
            ReciterCatalog.label(state.settings.reciter),
            R.drawable.ic_headphones,
            { navigate("reciters") },
        )
        FontControl("Playback speed", state.settings.speed, .5f..2f) {
            state.update(state.settings.copy(speed = it))
            audio.controller?.setPlaybackSpeed(it)
        }
        if (audio.verseLevel) {
            SectionTitle("Repeat a passage")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    start,
                    { start = it.filter(Char::isDigit).take(3) },
                    Modifier.weight(1f),
                    label = { Text("From ayah") },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                )
                OutlinedTextField(
                    end,
                    { end = it.filter(Char::isDigit).take(3) },
                    Modifier.weight(1f),
                    label = { Text("To ayah") },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                )
            }
            NosButton(
                onClick = {
                    val a = start.toIntOrNull()
                    val b = end.toIntOrNull()
                    if (
                        a != null && b != null && a in 1..b && b <= repo.surahs[v.surah - 1].count
                    ) {
                        invalid = false
                        audio.updateRepeat(true)
                        audio.play(
                            repo,
                            repo.verses.first { it.surah == v.surah && it.number == a },
                            state.settings,
                            b,
                        )
                    } else invalid = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Repeat selected range")
            }
            NosTextButton(
                onClick = {
                    audio.updateRepeat(true)
                    audio.play(repo, v, state.settings, v.number)
                }
            ) {
                Text("Repeat this ayah only")
            }
            if (invalid)
                Text(
                    "Enter a valid range from 1 to ${repo.surahs[v.surah-1].count}.",
                    color = MaterialTheme.colorScheme.error,
                )
        }
        Text(
            "Repeat applies to the current playback queue. Starting a new recitation replaces the queue.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayerTimeline(audio: Playback) {
    val motion = LocalNosMotion.current
    val clock = remember(audio.key) { Animatable(audio.position.toFloat()) }
    var scrub by remember(audio.key) { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val press = rememberPressFeedback()
    LaunchedEffect(audio.position, audio.key, motion.reduced) {
        if (scrub == null) {
            val target = audio.position.toFloat()
            if (motion.reduced || kotlin.math.abs(target - clock.value) > 1500f)
                clock.snapTo(target)
            else
                clock.animateTo(
                    target,
                    tween(motion.duration(MotionTokens.Standard), easing = LinearEasing),
                )
        }
    }
    Slider(
        value = (scrub ?: clock.value).coerceIn(0f, audio.duration.coerceAtLeast(1).toFloat()),
        onValueChange = { scrub = it },
        onValueChangeFinished = {
            scrub?.let { target ->
                scope.launch {
                    clock.snapTo(target)
                    audio.controller?.seekTo(target.toLong())
                    scrub = null
                    press.confirm()
                }
            }
        },
        interactionSource = press.source,
        valueRange = 0f..audio.duration.coerceAtLeast(1).toFloat(),
        enabled = audio.duration > 0,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Eyebrow(timeLabel(scrub?.toLong() ?: audio.position))
        Eyebrow(timeLabel(audio.duration))
    }
}
