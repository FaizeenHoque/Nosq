package dev.faizeenhoque.nosquran

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    repo: QuranRepository,
    state: QuranState,
    navigate: (String) -> Unit,
    notify: (String) -> Unit,
) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf(false) }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                it.write(state.export())
                            } ?: error("Cannot open destination")
                        }
                    }
                        .onSuccess { notify("Bookmarks exported") }
                        .onFailure { notify("Export failed: ${it.localizedMessage}") }
                }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching {
                        val json =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    val bytes = input.readNBytesCompat(2_000_001)
                                    require(bytes.size <= 2_000_000) { "File is too large" }
                                    String(bytes, Charsets.UTF_8)
                                } ?: error("Cannot open file")
                            }
                        state.import(json, repo)
                    }
                        .onSuccess { notify("Bookmarks imported") }
                        .onFailure { notify("Import failed: ${it.localizedMessage}") }
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        item(key = "title") { PageTitle("SETTINGS", "Make space for your reading") }
        item(key = "01 / Appearance") {
            ExpandableSettings("01 / Appearance") {
                ChoiceRow("Theme", listOf("System", "Light", "Dark"), s.theme) {
                    state.update(s.copy(theme = it))
                }
                ToggleRow("True black", "OLED background in dark mode", s.oled) {
                    state.update(s.copy(oled = it))
                }
                ToggleRow("Signature red", "Use red for meaningful emphasis", s.redAccent) {
                    state.update(s.copy(redAccent = it))
                }
                ToggleRow("Compact library", "Fit more Surahs on screen", s.compact) {
                    state.update(s.copy(compact = it))
                }
                ChoiceRow(
                    "Motion profile",
                    listOf("Calm", "Balanced", "Expressive"),
                    s.motionProfile,
                ) {
                    state.update(s.copy(motionProfile = it))
                }
                ToggleRow("Reduce motion", "Remove navigation transitions", s.reduceMotion) {
                    state.update(s.copy(reduceMotion = it))
                }
            }
        }
        item(key = "02 / Quran display") {
            ExpandableSettings("02 / Quran display") {
                SettingRow(
                    "Translation",
                    when (s.language) {
                        "English" -> "Saheeh International · English"
                        "Bengali" -> "Muhiuddin Khan · Bengali"
                        else -> "Hidden"
                    },
                    R.drawable.ic_languages,
                    { navigate("translations") },
                )
                FontControl("Arabic type size", s.arabicSize, 22f..52f) {
                    state.update(s.copy(arabicSize = it))
                }
                ArabicText(repo.verses.first().arabic, s.arabicSize, s.lineSpacing)
                FontControl("Arabic line spacing", s.lineSpacing, 1.5f..2.5f) {
                    state.update(s.copy(lineSpacing = it))
                }
                FontControl("Translation type size", s.translationSize, 12f..26f) {
                    state.update(s.copy(translationSize = it))
                }
                Text(
                    repo.verses.first().translation(s.language),
                    fontSize = s.translationSize.sp,
                    lineHeight = (s.translationSize * 1.65f).sp,
                )
                ToggleRow("Transliteration", "A Latin pronunciation aid", s.transliteration) {
                    state.update(s.copy(transliteration = it))
                }
                ToggleRow("Verse separators", "Fine rules between ayahs", s.separators) {
                    state.update(s.copy(separators = it))
                }
                ChoiceRow("Verse numerals", listOf("Western", "Arabic"), s.verseStyle) {
                    state.update(s.copy(verseStyle = it))
                }
                ChoiceRow("Reading layout", listOf("Scroll", "Pages"), s.layout) {
                    state.update(s.copy(layout = it))
                }
                Text(
                    "Quranic Arabic always uses right-to-left text direction and Amiri Quran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "03 / Recitation") {
            ExpandableSettings("03 / Recitation") {
                SettingRow(
                    "Reciter",
                    ReciterCatalog.label(s.reciter),
                    R.drawable.ic_headphones,
                    { navigate("reciters") },
                )
                FontControl("Playback speed", s.speed, .5f..2f) { state.update(s.copy(speed = it)) }
                ToggleRow(
                    "Automatic next ayah",
                    "Continue to the end of the Surah",
                    s.autoAdvance,
                ) {
                    state.update(s.copy(autoAdvance = it))
                }
                ToggleRow(
                    "Background playback",
                    "Continue when the app is in the background",
                    s.background,
                ) {
                    state.update(s.copy(background = it))
                }
                SettingRow(
                    "Offline recitations",
                    "Download over unmetered networks",
                    R.drawable.ic_download,
                    { navigate("downloads") },
                )
            }
        }
        item(key = "04 / Reading") {
            ExpandableSettings("04 / Reading") {
                ChoiceRow("Opening screen", listOf("Home", "Quran", "Reader"), s.opening) {
                    state.update(s.copy(opening = it))
                }
                ToggleRow("Remember reading position", "Resume your last ayah", s.resume) {
                    state.update(s.copy(resume = it))
                }
                ToggleRow(
                    "Follow recitation",
                    "Follow the playing ayah in Scroll and Pages layouts",
                    s.followRecitation,
                ) {
                    state.update(s.copy(followRecitation = it))
                }
                ToggleRow("Keep screen awake", "While a Surah is open", s.keepAwake) {
                    state.update(s.copy(keepAwake = it))
                }
                ToggleRow(
                    "Reading statistics",
                    "Track opened ayahs and sessions locally",
                    s.track,
                ) {
                    state.update(s.copy(track = it))
                }
                SettingRow(
                    "Reading history",
                    "Your recent reading positions",
                    R.drawable.ic_history,
                    { navigate("history") },
                )
                SettingRow(
                    "Reading statistics",
                    "Ayahs, Surahs and sessions",
                    R.drawable.ic_gauge,
                    { navigate("stats") },
                )
            }
        }
        item(key = "05 / Notifications") {
            ExpandableSettings("05 / Notifications") {
                ReminderSettings()
            }
        }
        item(key = "06 / Data & storage") {
            ExpandableSettings("06 / Data & storage") {
                SettingRow(
                    "Offline text",
                    "Arabic, English, Bengali & transliteration bundled",
                    R.drawable.ic_database,
                    { navigate("translations") },
                )
                SettingRow(
                    "Clear temporary cache",
                    "Keep bookmarks, offline text and recitations",
                    R.drawable.ic_database,
                    {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                }
                            }
                                .onSuccess { notify("Temporary cache cleared") }
                                .onFailure { notify("Unable to clear cache") }
                        }
                    },
                )
                SettingRow(
                    "Export bookmarks",
                    "JSON · includes collections and personal notes",
                    R.drawable.ic_share_2,
                    { exporter.launch("nos-quran-bookmarks.json") },
                )
                SettingRow(
                    "Import bookmarks",
                    "Merge a nosq JSON backup",
                    R.drawable.ic_download,
                    { importer.launch(arrayOf("application/json", "text/plain")) },
                )
                SettingRow(
                    "Reset reading progress",
                    "Clear statistics and recent history",
                    R.drawable.ic_trash_2,
                    { reset = true },
                )
            }
        }
        item(key = "07 / About") {
            ExpandableSettings("07 / About") {
                SettingRow(
                    AppMetadata.Name,
                    "Version ${AppMetadata.version} · ${AppMetadata.Developer}",
                    R.drawable.ic_info,
                    { navigate("about") },
                )
                SettingRow(
                    "Sources & licenses",
                    "Quran, translations, recitations and typefaces",
                    R.drawable.ic_file_text,
                    { navigate("licenses") },
                )
                SettingRow(
                    "Privacy",
                    "Your reading stays on this device",
                    R.drawable.ic_shield,
                    { navigate("privacy") },
                )
            }
        }
    }
    if (reset)
        NosAlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset reading progress?") },
            text = {
                Text(
                    "This clears reading statistics, last position and recent history. Bookmarks and personal notes remain saved."
                )
            },
            confirmButton = {
                NosTextButton(
                    onClick = {
                        state.resetProgress()
                        reset = false
                        notify("Reading progress reset")
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = { NosTextButton(onClick = { reset = false }) { Text("Cancel") } },
        )
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        out.write(buffer, 0, count)
        remaining -= count
    }
    return out.toByteArray()
}

@Composable
fun SelectionScreen(kind: String, state: QuranState) {
    if (kind == "reciters") {
        ReciterScreen(state)
        return
    }
    val s = state.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        PageTitle("TRANSLATION", "Understanding the meaning")
        listOf(
                "English" to "Saheeh International",
                "Bengali" to "Muhiuddin Khan",
                "None" to "Arabic only",
            )
            .forEach { (lang, source) ->
                SettingRow(
                    lang,
                    source,
                    R.drawable.ic_languages,
                    { state.update(s.copy(language = lang)) },
                    trailing = {
                        RadioButton(
                            s.language == lang,
                            { state.update(s.copy(language = lang)) },
                            modifier = Modifier.semantics { contentDescription = lang },
                        )
                    },
                )
            }
        Spacer(Modifier.height(24.dp))
        Eyebrow("AVAILABLE OFFLINE")
        Spacer(Modifier.height(12.dp))
        Text(
            "Translations are interpretations of the Quran’s meaning and do not replace the Arabic text.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReciterScreen(state: QuranState) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var provider by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
    var kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All audio") }
    val editions = ReciterCatalog.editions
    LaunchedEffect(Unit) {
        ReciterCatalog.load(context)
        ReciterCatalog.refresh(context)
    }
    val matches =
        remember(editions, query, provider, kind) {
            val q = normalizeArabic(query.trim()).lowercase(java.util.Locale.ROOT)
            editions.filter { edition ->
                (provider == "All" || edition.provider == provider) &&
                    (when (kind) {
                        "Verse" -> edition.verseLevel && !edition.translation
                        "Surah" -> !edition.verseLevel
                        "Translation" -> edition.translation
                        else -> true
                    }) &&
                    normalizeArabic(
                            "${edition.name} ${edition.arabicName} ${edition.variant} ${edition.quality}"
                        )
                        .lowercase(java.util.Locale.ROOT)
                        .contains(q)
            }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        item(key = "catalog-controls") {
            PageTitle("RECITERS", "${editions.size} provider recordings")
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Name, Arabic name, riwaya or quality") },
            )
            ChoiceRow("Provider", listOf("All", "EveryAyah", "MP3Quran"), provider) {
                provider = it
            }
            ChoiceRow(
                "Recording type",
                listOf("All audio", "Verse", "Surah", "Translation"),
                kind,
            ) {
                kind = it
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ReciterCatalog.status,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                NosTextButton(
                    onClick = { scope.launch { ReciterCatalog.refresh(context, true) } },
                    enabled = !ReciterCatalog.refreshing,
                ) {
                    Text("Refresh")
                }
            }
            if (ReciterCatalog.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionTitle("Recordings", "${matches.size} shown")
        }
        items(matches, key = { it.id }, contentType = { "recitation-edition" }) { edition ->
            val selected = ReciterCatalog.selected(state.settings.reciter, edition.id)
            val choose = { state.update(state.settings.copy(reciter = edition.id)) }
            Column {
                SettingRow(
                    edition.name,
                    "${edition.variant}\n${edition.description}\n${if(edition.verseLevel)"Verse files · provider availability varies"else "${edition.surahs.size} Surahs available"}",
                    R.drawable.ic_headphones,
                    choose,
                    trailing = {
                        RadioButton(
                            selected,
                            choose,
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        "Select ${edition.name}, ${edition.variant}"
                                },
                        )
                    },
                )
                if (edition.arabicName.isNotBlank())
                    Text(
                        edition.arabicName,
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
            }
        }
        if (matches.isEmpty())
            item {
                EmptyState(
                    if (editions.isEmpty()) "Loading catalog" else "No matching recordings",
                    "Search another name or change the filters.",
                    R.drawable.ic_headphones,
                )
            }
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "Full-Surah recordings do not expose verse timings. Their player starts at the Surah beginning and does not offer verse ranges. The displayed Quran uses Hafs/Kufan numbering; choose recitation variants accordingly.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
