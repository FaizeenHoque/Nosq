package dev.faizeenhoque.nosquran

import android.app.Activity
import android.content.*
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ArabicText(text: String, size: Float, spacing: Float) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        fontFamily = AmiriQuran,
        fontSize = size.sp,
        lineHeight = (size * spacing).sp,
        textAlign = TextAlign.Right,
        style = TextStyle(textDirection = TextDirection.Rtl),
    )
}

fun displayNumber(number: Int, style: String) =
    if (style == "Arabic")
        number
            .toString()
            .map { if (it.isDigit()) ('٠'.code + it.digitToInt()).toChar() else it }
            .joinToString("")
    else number.toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerseBlock(
    v: Verse,
    settings: ReaderSettings,
    saved: Boolean,
    active: Boolean,
    selected: Boolean = false,
    animateLayout: Boolean = true,
    actions: () -> Unit,
    playAyah: (() -> Unit)? = null,
    isPlaying: Boolean = false,
) {
    val motion = LocalNosMotion.current
    val highlight =
        animateColorAsState(
            if (selected || active) MaterialTheme.colorScheme.surfaceVariant
            else androidx.compose.ui.graphics.Color.Transparent,
            tween(motion.duration(MotionTokens.Fast)),
            label = "verse focus",
        )
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    highlight.value,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                )
            }
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    )
                    actions()
                },
                onLongClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    actions()
                },
            )
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                displayNumber(v.number, settings.verseStyle),
                fontFamily = NDot57,
                fontSize = 24.sp,
                color =
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (active) Eyebrow("NOW PLAYING")
            if (playAyah != null) {
                Glyph(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) "Pause ayah ${v.key}" else "Play ayah ${v.key}",
                    playAyah,
                )
            }
            BookmarkConfirmation(saved) {
                Icon(
                    painterResource(R.drawable.ic_bookmark),
                    "Saved verse",
                    Modifier.padding(horizontal = 12.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                painterResource(R.drawable.ic_ellipsis_vertical),
                "Verse ${v.key} actions",
                Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ArabicText(v.arabic, settings.arabicSize, settings.lineSpacing)
        Reveal(settings.transliteration, animateLayout) {
            Text(
                v.transliteration,
                fontSize = (settings.translationSize - 1).sp,
                lineHeight = (settings.translationSize * 1.65f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Reveal(settings.language != "None", animateLayout) {
            Text(
                v.translation(settings.language),
                fontSize = settings.translationSize.sp,
                lineHeight = (settings.translationSize * 1.65f).sp,
            )
        }
    }
    if (settings.separators)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    repo: QuranRepository,
    state: QuranState,
    key: String,
    audio: Playback,
    back: () -> Unit,
    open: (String) -> Unit,
    actions: (Verse) -> Unit,
    navigate: (String) -> Unit,
    selectedKey: String? = null,
) {
    val target = repo.verse(key) ?: repo.verses.first()
    val surah = repo.surahs[target.surah - 1]
    val verses = remember(target.surah) { repo.chapter(target.surah) }
    val list =
        rememberLazyListState(
            initialFirstVisibleItemIndex = if (target.number == 1) 0 else target.number
        )
    var page by rememberSaveable(key) { mutableIntStateOf(target.number) }
    var initialPositionApplied by rememberSaveable(key) { mutableStateOf(false) }
    var focusedVerse by
        remember(key) { mutableStateOf<String?>(if (target.number > 1) target.key else null) }
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    val compactHeader by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 1 } }
    var jumpDialog by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf("") }
    var controls by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val scrolling by remember(list) { derivedStateOf { list.isScrollInProgress } }
    val motion = LocalNosMotion.current
    val activity = context as? Activity
    DisposableEffect(settings.keepAwake) {
        val window = activity?.window
        if (settings.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    fun changeLayout(mode: String) {
        if (mode == settings.layout) return
        if (mode == "Pages") {
            page = list.firstVisibleItemIndex.coerceIn(1, verses.size)
        } else {
            list.requestScrollToItem(if (page == 1) 0 else page)
        }
        state.update(settings.copy(layout = mode))
    }
    LaunchedEffect(key) {
        if (!initialPositionApplied) {
            list.scrollToItem(if (target.number == 1) 0 else target.number)
            page = target.number
            initialPositionApplied = true
        }
        delay(1400)
        focusedVerse = null
    }
    LaunchedEffect(list, settings.layout, key) {
        if (settings.layout == "Scroll")
            snapshotFlow {
                list.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index > 0 && it.index <= verses.size }
                    ?.index
            }
                .distinctUntilChanged()
                .collect { index ->
                    if (index != null) verses.getOrNull(index - 1)?.let { state.position(it) }
                }
    }
    LaunchedEffect(page, settings.layout, key) {
        if (settings.layout == "Pages") state.position(verses[page - 1])
    }
    // Observe media-item changes, never the per-frame playback position.
    LaunchedEffect(
        audio.key,
        audio.playing,
        audio.verseLevel,
        settings.followRecitation,
        settings.layout,
    ) {
        if (!settings.followRecitation || !audio.playing || !audio.verseLevel) return@LaunchedEffect
        val current = repo.verse(audio.key) ?: return@LaunchedEffect
        if (current.surah != surah.id) return@LaunchedEffect
        if (settings.layout == "Pages") {
            page = current.number
        } else {
            // Let an active touch/fling finish before following the recitation.
            snapshotFlow { list.isScrollInProgress }.first { !it }
            scrollToVerse(list, current.number, motion)
        }
    }
    val supportsAyahs = ReciterCatalog.resolve(settings.reciter)?.verseLevel == true
    fun playAyah(verse: Verse) {
        if (audio.verseLevel && audio.key == verse.key && audio.playing) audio.toggle()
        else audio.play(repo, verse, settings, end = verse.number)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(R.drawable.ic_arrow_left, "Back", back)
            Column(Modifier.weight(1f).clickable { info = true }) {
                Text(surah.name, style = MaterialTheme.typography.titleMedium)
                Reveal(!compactHeader) { Eyebrow("${surah.origin} / ${surah.count} AYAHS") }
            }
            Glyph(R.drawable.ic_hash, "Jump to ayah") {
                jump = ""
                jumpDialog = true
            }
            Glyph(R.drawable.ic_headphones, "Open audio player") {
                if (audio.key == null) {
                    val verse =
                        if (settings.layout == "Pages") verses[page - 1]
                        else verses[(list.firstVisibleItemIndex - 1).coerceIn(0, verses.lastIndex)]
                    audio.play(repo, verse, settings)
                }
                navigate("player")
            }
            Glyph(R.drawable.ic_type, "Reading controls") { controls = true }
            Glyph(
                if (settings.layout == "Pages") R.drawable.ic_rows_3 else R.drawable.ic_columns_2,
                if (settings.layout == "Pages") "Switch to scroll layout"
                else "Switch to page layout",
            ) {
                changeLayout(if (settings.layout == "Pages") "Scroll" else "Pages")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (settings.layout == "Scroll") {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                item { ReaderHeader(surah, repo, settings) { info = true } }
                items(verses, key = { it.key }, contentType = { "ayah" }) { v ->
                    VerseBlock(
                        v,
                        settings,
                        v.key in state.savedKeys,
                        audio.verseLevel && audio.key == v.key,
                        actions = { actions(v) },
                        playAyah = if (supportsAyahs) ({ playAyah(v) }) else null,
                        isPlaying = audio.verseLevel && audio.key == v.key && audio.playing,
                        selected = selectedKey == v.key || focusedVerse == v.key,
                        animateLayout = !scrolling,
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        NosTextButton(
                            enabled = surah.id > 1,
                            onClick = { open("${surah.id-1}:1") },
                        ) {
                            Text("← Previous")
                        }
                        NosTextButton(
                            enabled = surah.id < 114,
                            onClick = { open("${surah.id+1}:1") },
                        ) {
                            Text("Next Surah →")
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp)) {
                if (page == 1) item { ReaderHeader(surah, repo, settings) { info = true } }
                item(key = verses[page - 1].key) {
                    val v = verses[page - 1]
                    VerseBlock(
                        v,
                        settings,
                        v.key in state.savedKeys,
                        audio.verseLevel && audio.key == v.key,
                        selected = selectedKey == v.key || focusedVerse == v.key,
                        actions = { actions(v) },
                        playAyah = if (supportsAyahs) ({ playAyah(v) }) else null,
                        isPlaying = audio.verseLevel && audio.key == v.key && audio.playing,
                    )
                }
            }
        }
        ReaderPositionLine(list, page, verses.size, settings.layout)
        AnimatedContent(
            settings.layout,
            transitionSpec = {
                (fadeIn(tween(motion.duration(MotionTokens.Standard))) +
                    slideInVertically(
                        tween(motion.duration(MotionTokens.Standard), easing = PrecisionEase)
                    ) {
                        it / 5
                    }) togetherWith fadeOut(tween(motion.duration(MotionTokens.Fast)))
            },
            label = "reading mode controls",
        ) { mode ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (mode == "Pages")
                    Glyph(R.drawable.ic_chevron_left, "Previous ayah") { if (page > 1) page-- }
                else
                    Glyph(R.drawable.ic_play, "Play from reading position") {
                        val index = (list.firstVisibleItemIndex - 1).coerceIn(0, verses.lastIndex)
                        audio.play(repo, verses[index], settings)
                    }
                val number =
                    if (mode == "Pages") page
                    else
                        remember(list, verses) {
                                derivedStateOf {
                                    list.firstVisibleItemIndex.coerceIn(1, verses.size)
                                }
                            }
                            .value
                NosTextButton(
                    onClick = {
                        jump = ""
                        jumpDialog = true
                    }
                ) {
                    Eyebrow("AYAH $number / ${surah.count} · JUZ ${verses[number-1].juz}")
                }
                if (mode == "Pages")
                    Glyph(R.drawable.ic_chevron_right, "Next ayah") {
                        if (page < verses.size) page++
                    }
                else
                    Glyph(R.drawable.ic_columns_2, "Switch to page mode") {
                        changeLayout("Pages")
                    }
            }
        }
    }
    if (controls)
        ModalBottomSheet(onDismissRequest = { controls = false }) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text("Reading controls", style = MaterialTheme.typography.headlineMedium)
                NosTextButton(
                    onClick = {
                        controls = false
                        navigate("Settings")
                    }
                ) {
                    Text("All settings →")
                }
                FontControl("Arabic size", settings.arabicSize, 22f..52f) {
                    state.update(settings.copy(arabicSize = it))
                }
                FontControl("Translation size", settings.translationSize, 12f..26f) {
                    state.update(settings.copy(translationSize = it))
                }
                FontControl("Line spacing", settings.lineSpacing, 1.5f..2.5f) {
                    state.update(settings.copy(lineSpacing = it))
                }
                ChoiceRow("Layout", listOf("Scroll", "Pages"), settings.layout, ::changeLayout)
                ChoiceRow("Translation", listOf("English", "Bengali", "None"), settings.language) {
                    state.update(settings.copy(language = it))
                }
                ToggleRow("Transliteration", "Pronunciation aid", settings.transliteration) {
                    state.update(settings.copy(transliteration = it))
                }
                ToggleRow(
                    "Follow recitation",
                    "Scroll or turn pages to the playing ayah · verse recordings only",
                    settings.followRecitation,
                ) {
                    state.update(settings.copy(followRecitation = it))
                }
                if (!supportsAyahs) {
                    NosTextButton(
                        onClick = {
                            controls = false
                            navigate("reciters")
                        }
                    ) {
                        Text("Choose a verse-by-verse reciter")
                    }
                }
                ToggleRow("Keep screen awake", "Only while reading", settings.keepAwake) {
                    state.update(settings.copy(keepAwake = it))
                }
                ChoiceRow("Appearance", listOf("System", "Light", "Dark"), settings.theme) {
                    state.update(settings.copy(theme = it))
                }
            }
        }
    if (jumpDialog)
        NosAlertDialog(
            onDismissRequest = { jumpDialog = false },
            title = { Text("Go to ayah") },
            text = {
                Column {
                    Text("${surah.name} · 1–${surah.count}")
                    OutlinedTextField(
                        jump,
                        { jump = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text("Ayah number") },
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                    )
                }
            },
            confirmButton = {
                NosTextButton(
                    enabled = jump.toIntOrNull() in 1..surah.count,
                    onClick = {
                        val n = jump.toInt()
                        page = n
                        jumpJob?.cancel()
                        jumpJob = scope.launch {
                            scrollToVerse(list, if (n == 1) 0 else n, motion)
                            focusedVerse = verses[n - 1].key
                            delay(1400)
                            focusedVerse = null
                        }
                        jumpDialog = false
                    },
                ) {
                    Text("Go")
                }
            },
            dismissButton = { NosTextButton(onClick = { jumpDialog = false }) { Text("Cancel") } },
        )
    if (info)
        NosAlertDialog(
            onDismissRequest = { info = false },
            title = { Text(surah.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ArabicText(surah.arabic, 36f, 1.8f)
                    Text(surah.meaning)
                    Text(
                        "Surah ${surah.id} · ${surah.count} ayahs\n${surah.origin} revelation\nJuz ${verses.first().juz}–${verses.last().juz}"
                    )
                    Text(
                        "Arabic text: Uthmani / Hafs, Kufan numbering. Translation and source details are available in About.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { NosTextButton(onClick = { info = false }) { Text("Close") } },
        )
}

@Composable
private fun ReaderHeader(
    s: Surah,
    repo: QuranRepository,
    settings: ReaderSettings,
    info: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = info).padding(top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            s.id.toString().padStart(3, '0'),
            fontFamily = NDot57,
            fontSize = 40.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(s.arabic, fontFamily = AmiriQuran, fontSize = 40.sp, lineHeight = 76.sp)
        Eyebrow(s.meaning)
        if (s.id != 1 && s.id != 9)
            Text(
                repo.verses.first().arabic,
                fontFamily = AmiriQuran,
                fontSize = 26.sp,
                lineHeight = 60.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
    }
}

@Composable
fun FontControl(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit,
) {
    val press = rememberPressFeedback()
    val motion = LocalNosMotion.current
    val label =
        if (range.endInclusive < 3) String.format(java.util.Locale.ENGLISH, "%.1f", value)
        else value.toInt().toString()
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Crossfade(
                label,
                animationSpec = tween(motion.duration(MotionTokens.Fast)),
                label = "slider value",
            ) { shown ->
                Text(shown, fontFamily = NDot57, fontSize = 20.sp)
            }
        }
        Slider(
            value,
            onValueChange = change,
            onValueChangeFinished = { press.confirm() },
            interactionSource = press.source,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Quran", text))
}

fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share ayah",
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseActions(
    v: Verse,
    repo: QuranRepository,
    state: QuranState,
    audio: Playback,
    dismiss: () -> Unit,
    navigate: (String) -> Unit,
    notify: (String) -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalNosMotion.current
    var panel by remember(v.key) { mutableStateOf("actions") }
    var note by
        remember(v.key) { mutableStateOf(state.saved.find { it.key == v.key }?.note.orEmpty()) }
    var collection by
        remember(v.key) {
            mutableStateOf(state.saved.find { it.key == v.key }?.collection ?: "Saved verses")
        }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(panel != "actions") { panel = "actions" }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Eyebrow("${repo.surahs[v.surah-1].name} / AYAH ${v.number}")
            AnimatedContent(
                panel,
                transitionSpec = {
                    ((fadeIn(tween(motion.duration(MotionTokens.Standard))) +
                            slideInVertically(
                                tween(
                                    motion.duration(MotionTokens.Standard),
                                    easing = PrecisionEase,
                                )
                            ) {
                                it / 16
                            }) togetherWith fadeOut(tween(motion.duration(MotionTokens.Fast))))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                tween(
                                    motion.duration(MotionTokens.Standard),
                                    easing = PrecisionEase,
                                )
                            }
                        )
                },
                label = "verse action panel",
            ) { current ->
                Column(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                    Text(
                        when (current) {
                            "note" -> "Your reflection"
                            "tafsir" -> "Tafsir & sources"
                            "saved" -> "Ayah saved"
                            "removed" -> "Bookmark removed"
                            else -> "A moment to reflect"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    when (current) {
                        "note" -> {
                            OutlinedTextField(
                                note,
                                { note = it.take(20000) },
                                Modifier.fillMaxWidth(),
                                label = { Text("Personal note") },
                                minLines = 3,
                            )
                            ChoiceRow("Collection", state.collections.sorted(), collection) {
                                collection = it
                            }
                            NosButton(
                                onClick = {
                                    state.annotate(v.key, note, collection)
                                    notify("Note saved")
                                    panel = "saved"
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save note & bookmark")
                            }
                            NosTextButton(onClick = { panel = "actions" }) {
                                Text("← Back to actions")
                            }
                        }
                        "saved",
                        "removed" -> {
                            BookmarkConfirmation(true) {
                                Icon(
                                    painterResource(
                                        if (current == "saved") R.drawable.ic_check
                                        else R.drawable.ic_bookmark
                                    ),
                                    null,
                                    Modifier.padding(vertical = 12.dp).size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                if (current == "saved")
                                    "Kept in your saved verses. Return to it whenever you need."
                                else "This ayah has been removed from your saved verses.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(20.dp))
                            if (current == "saved")
                                NosOutlinedButton(
                                    onClick = { panel = "note" },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Add a personal note")
                                }
                            NosTextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                                Text("Continue reading")
                            }
                        }
                        "tafsir" -> {
                            Eyebrow("HUMAN COMMENTARY / IBN KATHIR")
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Published commentary is available through Quran.com. No offline Tafsir edition is bundled; commentary is kept distinct from the Quranic text.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(20.dp))
                            NosButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                "https://quran.com/${v.surah}:${v.number}/tafsirs/en-tafisr-ibn-kathir"
                                            ),
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open published commentary ↗")
                            }
                            NosOutlinedButton(
                                onClick = {
                                    dismiss()
                                    navigate("details/${v.key}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Verse details & sources")
                            }
                            NosTextButton(onClick = { panel = "actions" }) {
                                Text("← Back to actions")
                            }
                        }
                        else -> {
                            SettingRow(
                                "Play recitation",
                                ReciterCatalog.label(state.settings.reciter),
                                R.drawable.ic_play,
                                {
                                    audio.play(repo, v, state.settings)
                                    dismiss()
                                },
                            )
                            if (
                                ReciterCatalog.resolve(state.settings.reciter)?.verseLevel == true
                            ) {
                                SettingRow(
                                    "Play this ayah only",
                                    "Stop after ayah ${v.key}",
                                    R.drawable.ic_play,
                                    {
                                        audio.play(repo, v, state.settings, end = v.number)
                                        dismiss()
                                    },
                                )
                            }
                            val saved = v.key in state.savedKeys
                            SettingRow(
                                if (saved) "Remove bookmark" else "Bookmark ayah",
                                "Saved on this device",
                                R.drawable.ic_bookmark,
                                {
                                    state.toggle(v.key)
                                    panel = if (saved) "removed" else "saved"
                                },
                            )
                            SettingRow(
                                "Copy Arabic",
                                "Exact Quranic text",
                                R.drawable.ic_copy,
                                {
                                    copyText(context, v.arabic)
                                    notify("Arabic copied")
                                    dismiss()
                                },
                            )
                            SettingRow(
                                "Copy translation",
                                if (state.settings.language == "Bengali") "Muhiuddin Khan"
                                else "Saheeh International",
                                R.drawable.ic_languages,
                                {
                                    copyText(context, v.translation(state.settings.language))
                                    notify("Translation copied")
                                    dismiss()
                                },
                            )
                            SettingRow(
                                "Share ayah",
                                "Arabic, translation & reference",
                                R.drawable.ic_share_2,
                                {
                                    shareText(
                                        context,
                                        "${v.arabic}\n\n${v.translation(state.settings.language)}\nQuran ${v.key} · ${if(state.settings.language=="Bengali")"Muhiuddin Khan" else "Saheeh International"}",
                                    )
                                    dismiss()
                                },
                            )
                            SettingRow(
                                "Personal note",
                                "Reflect and organize into a collection",
                                R.drawable.ic_pencil,
                                { panel = "note" },
                            )
                            SettingRow(
                                "Tafsir & verse details",
                                "Sources and commentary",
                                R.drawable.ic_scroll_text,
                                { panel = "tafsir" },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPositionLine(list: LazyListState, page: Int, total: Int, layout: String) {
    val visible by
        remember(list, total) { derivedStateOf { list.firstVisibleItemIndex.coerceIn(1, total) } }
    val progress =
        animateFloatAsState(
            (if (layout == "Pages") page else visible).toFloat() / total,
            tween(LocalNosMotion.current.duration(MotionTokens.Standard), easing = PrecisionEase),
            label = "reader position",
        )
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
    )
}

/** Avoid laying out hundreds of Arabic verses for a long-distance programmatic jump. */
private suspend fun scrollToVerse(list: LazyListState, target: Int, motion: MotionPolicy) {
    if (motion.reduced) {
        list.scrollToItem(target)
        return
    }
    val distance = target - list.firstVisibleItemIndex
    if (kotlin.math.abs(distance) > 14)
        list.scrollToItem((target - if (distance > 0) 3 else -3).coerceAtLeast(0))
    list.animateScrollToItem(target)
}
