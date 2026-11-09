package dev.faizeenhoque.nosquran

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(
    repo: QuranRepository,
    state: QuranState,
    open: (String) -> Unit,
    navigate: (String) -> Unit,
    actions: (Verse) -> Unit,
) {
    val last = repo.verse(state.last) ?: repo.verses.first()
    val surah = repo.surahs[last.surah - 1]
    val daily = repo.verses[(LocalDate.now().toEpochDay() % repo.verses.size).toInt()]
    val continuePress = rememberPressFeedback(.985f)
    val motion = LocalNosMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(last.key, motion.reduced) {
        val target = last.number.toFloat() / surah.count
        if (motion.reduced) progress.snapTo(target)
        else
            progress.animateTo(
                target,
                tween(motion.duration(MotionTokens.Screen), easing = PrecisionEase),
            )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        item(key = "identity") {
            StaggeredEntrance(0) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("A moment of stillness")
                            Text(
                                AppMetadata.Name,
                                fontFamily = NDot57,
                                fontSize = 42.sp,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Box(
                            Modifier.size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            HijrahDate.now()
                                .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Eyebrow("HIJRI · ESTIMATED")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        item(key = "continue") {
            StaggeredEntrance(1) {
                Surface(
                    onClick = {
                        continuePress.confirm()
                        open(last.key)
                    },
                    modifier = Modifier.fillMaxWidth().then(continuePress.modifier),
                    interactionSource = continuePress.source,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "PICK UP WHERE YOU LEFT OFF",
                                fontSize = 10.sp,
                                letterSpacing = 1.6.sp,
                            )
                            Icon(
                                painterResource(R.drawable.ic_arrow_up_down),
                                null,
                                Modifier.size(15.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    surah.name,
                                    modifier = Modifier,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Ayah ${last.number} of ${surah.count}",
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Box(
                                Modifier.size(50.dp)
                                    .background(MaterialTheme.colorScheme.background, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_chevron_right),
                                    null,
                                    Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress.value },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = NOS.Red,
                            trackColor = Color.Gray.copy(alpha = .35f),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("CONTINUE READING", fontSize = 10.sp, letterSpacing = 1.6.sp)
                            Text(
                                "${(100f*last.number/surah.count).toInt()}%",
                                fontFamily = NDot57,
                                fontSize = 18.sp,
                            )
                        }
                    }
                }
            }
        }
        item {
            StaggeredEntrance(2) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(state.read.size.toString(), "AYAHS READ", Modifier.weight(1f)) {
                        navigate("stats")
                    }
                    StatTile(
                        state.sessions.toString().padStart(2, '0'),
                        "SESSIONS",
                        Modifier.weight(1f),
                    ) {
                        navigate("history")
                    }
                }
            }
            SectionTitle("Your daily ayah", daily.key)
            Surface(shape = NOS.Corner, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ArabicText(daily.arabic, 28f, 1.9f)
                    Text(
                        daily.translation(state.settings.language),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NosTextButton(
                            onClick = { open(daily.key) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${repo.surahs[daily.surah-1].name}  →")
                        }
                        Glyph(R.drawable.ic_ellipsis_vertical, "Daily verse actions") {
                            actions(daily)
                        }
                    }
                }
            }
            SectionTitle("Within reach", "QUICK ACCESS")
            StaggeredEntrance(3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("01", "Al-Fatihah", Modifier.weight(1f)) { open("1:1") }
                    QuickTile("18", "Al-Kahf", Modifier.weight(1f)) { open("18:1") }
                    QuickTile("2:255", "Al-Kursi", Modifier.weight(1f)) { open("2:255") }
                }
            }
            SectionTitle("Recently opened")
        }
        if (state.history.isEmpty())
            item {
                Text(
                    "Your reading journey begins with a single ayah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                NosTextButton(onClick = { open("1:1") }) { Text("Open Al-Fatihah →") }
            }
        items(state.history.take(3), key = { it }) { key ->
            repo.verses
                .find { it.key == key }
                ?.let { v ->
                    SettingRow(
                        repo.surahs[v.surah - 1].name,
                        "Ayah ${v.number}",
                        R.drawable.ic_history,
                        { open(key) },
                    )
                }
        }
        if (state.saved.isNotEmpty()) {
            item { SectionTitle("Recently saved") }
            items(
                state.saved.sortedByDescending { it.created }.take(2),
                key = { "saved-${it.key}" },
            ) { saved ->
                repo.verses
                    .find { it.key == saved.key }
                    ?.let { v ->
                        SettingRow(
                            repo.surahs[v.surah - 1].name,
                            "Ayah ${v.number} · ${saved.collection}",
                            R.drawable.ic_bookmark,
                            { open(v.key) },
                        )
                    }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Eyebrow("THE QURAN. WITHOUT DISTRACTION.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, click: () -> Unit) {
    val press = rememberPressFeedback()
    Surface(
        onClick = {
            press.confirm()
            click()
        },
        modifier = modifier.then(press.modifier),
        interactionSource = press.source,
        shape = NOS.Corner,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedStatNumber(value.toIntOrNull() ?: 0, digits = value.length)
            Eyebrow(label)
        }
    }
}

@Composable
private fun QuickTile(number: String, title: String, modifier: Modifier, click: () -> Unit) {
    val press = rememberPressFeedback()
    Surface(
        onClick = {
            press.confirm()
            click()
        },
        modifier = modifier.then(press.modifier),
        interactionSource = press.source,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(number, fontFamily = NDot57, fontSize = 23.sp)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: QuranRepository, state: QuranState, open: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Surah") }
    var query by rememberSaveable { mutableStateOf("") }
    var origin by rememberSaveable { mutableStateOf("All") }
    val pager = rememberPagerState(initialPage = if (mode == "Juz") 1 else 0, pageCount = { 2 })
    val surahList = rememberLazyListState()
    val juzList = rememberLazyListState()
    val motion = LocalNosMotion.current
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(snapAnimationSpec = null)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }
            .distinctUntilChanged()
            .collect { mode = if (it == 0) "Surah" else "Juz" }
    }
    val matches =
        remember(repo, query, origin) {
            repo.surahs.filter {
                (query.isBlank() ||
                    it.name.contains(query, true) ||
                    it.meaning.contains(query, true) ||
                    normalizeArabic(it.arabic).contains(normalizeArabic(query)) ||
                    it.id.toString() == query) &&
                    (origin == "All" ||
                        origin == "Meccan" && it.origin.startsWith("Makk", true) ||
                        origin == "Medinan" && it.origin.startsWith("Mad", true))
            }
        }
    Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeTopAppBar(
            title = {
                Column {
                    Eyebrow("114 SURAHS / 30 JUZ")
                    Text("THE QURAN", fontFamily = NDot57, fontSize = 30.sp)
                }
            },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
        )
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            singleLine = true,
            placeholder = {
                Text(
                    if (pager.currentPage == 0) "Find a Surah or enter 2:255"
                    else "Find a Juz or its opening Surah"
                )
            },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp))
            },
            shape = RoundedCornerShape(16.dp),
        )
        repo.verse(query.trim())?.let { v ->
            NosTextButton(
                onClick = { open(v.key) },
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text("Open ${v.key} →")
            }
        }
        NosPagerTabs(
            pager,
            listOf("Surah", "Juz"),
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { if (it == 0) "surahs" else "juz" },
        ) { page ->
            if (page == 0)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = surahList,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    item(key = "filters") {
                        ChoiceRow("Revelation", listOf("All", "Meccan", "Medinan"), origin) {
                            origin = it
                        }
                        Eyebrow("${matches.size} SURAHS")
                    }
                    if (matches.isEmpty())
                        item {
                            EmptyState("No Surahs found", "Try another name, number or filter.")
                        }
                    items(matches, key = { "surah-${it.id}" }, contentType = { "surah" }) { surah ->
                        SurahRow(
                            surah,
                            state.readCount(surah.id).toFloat() / surah.count,
                            state.settings.compact,
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = tween(motion.duration(MotionTokens.Fast)),
                                    placementSpec = null,
                                    fadeOutSpec = tween(motion.duration(MotionTokens.Fast)),
                                ),
                        ) {
                            open("${surah.id}:1")
                        }
                    }
                }
            else
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = juzList,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    items(
                        (1..30).filter { juz ->
                            val first = repo.byJuz.getValue(juz).first()
                            query.isBlank() ||
                                juz.toString() == query ||
                                repo.surahs[first.surah - 1].name.contains(query, true)
                        },
                        key = { "juz-$it" },
                        contentType = { "juz" },
                    ) { juz ->
                        val verses = repo.byJuz.getValue(juz)
                        val v = verses.first()
                        SettingRow(
                            "Juz ${juz.toString().padStart(2,'0')}",
                            "${repo.surahs[v.surah-1].name} ${v.key} · ${verses.size} ayahs",
                            R.drawable.ic_book_open,
                            { open(v.key) },
                        )
                    }
                }
        }
    }
}

@Composable
fun SearchScreen(repo: QuranRepository, state: QuranState, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(repo) { repo.prepareSearch() }
    var results by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val motion = LocalNosMotion.current
    LaunchedEffect(query, filter) {
        loading = true
        delay(100)
        results = repo.search(query, filter)
        loading = false
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("SEARCH", "Find meaning. Find a moment.")
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().testTag("search-input"),
                singleLine = true,
                placeholder = { Text("A word, a Surah, or 2:255") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) Glyph(R.drawable.ic_x, "Clear search") { query = "" }
                },
                shape = RoundedCornerShape(16.dp),
            )
            var expanded by remember { mutableStateOf(false) }
            Box {
                NosTextButton(onClick = { expanded = true }) {
                    Text(if (filter == 0) "All Surahs ▾" else "${repo.surahs[filter-1].name} ▾")
                }
                DropdownMenu(
                    expanded,
                    { expanded = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("All Surahs") },
                        onClick = {
                            filter = 0
                            expanded = false
                        },
                    )
                    repo.surahs.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.id}. ${s.name}") },
                            onClick = {
                                filter = s.id
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (query.isNotBlank())
                Eyebrow("${results.size} matching ayahs", Modifier.testTag("search-ready"))
        }
        if (query.isBlank()) {
            item { SectionTitle("Recent searches") }
            if (state.searches.isEmpty())
                item {
                    EmptyState(
                        "Seek and discover",
                        "Search Arabic with or without diacritics, English, Bengali, or a verse reference.",
                        R.drawable.ic_search,
                    )
                }
            items(state.searches) { q -> SettingRow(q, "", R.drawable.ic_history, { query = q }) }
        } else {
            if (!loading && results.isEmpty())
                item {
                    EmptyState(
                        "No matching ayahs",
                        "Try a different word or a reference such as 2:255.",
                        R.drawable.ic_search,
                    )
                }
            items(results, key = { it.key }, contentType = { "search-result" }) { v ->
                Surface(
                    onClick = {
                        state.search(query.trim())
                        open(v.key)
                    },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = tween(motion.duration(MotionTokens.Fast)),
                            placementSpec = null,
                            fadeOutSpec = null,
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Eyebrow("${repo.surahs[v.surah-1].name} / ${v.key}")
                        if (query.any { it in '\u0600'..'\u06FF' }) ArabicText(v.arabic, 25f, 1.8f)
                        HighlightedText(v.translation(state.settings.language), query)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, query: String) {
    val color = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, query, color) {
            buildAnnotatedString {
                append(text)
                if (query.isNotBlank()) {
                    var index = text.indexOf(query, ignoreCase = true)
                    while (index >= 0) {
                        addStyle(
                            SpanStyle(color = color, fontWeight = FontWeight.Bold),
                            index,
                            index + query.length,
                        )
                        index = text.indexOf(query, index + query.length, ignoreCase = true)
                    }
                }
            }
        }
    Text(annotated, style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp)
}

@Composable
fun SavedScreen(
    repo: QuranRepository,
    state: QuranState,
    open: (String) -> Unit,
    actions: (Verse) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    var collectionDialog by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageTitle("SAVED", "Keep what stays with you") {
                Glyph(R.drawable.ic_plus, "Create collection") {
                    name = ""
                    rename = false
                    collectionDialog = true
                }
            }
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search verses and notes") },
                shape = RoundedCornerShape(16.dp),
            )
            ChoiceRow("Collections", listOf("All") + state.collections.sorted(), selected) {
                selected = it
            }
            if (selected != "All")
                Row {
                    NosTextButton(
                        onClick = {
                            name = selected
                            rename = true
                            collectionDialog = true
                        }
                    ) {
                        Text("Rename")
                    }
                    if (selected != "Saved verses")
                        NosTextButton(
                            onClick = {
                                state.deleteCollection(selected)
                                selected = "All"
                            }
                        ) {
                            Text("Remove collection")
                        }
                }
            ChoiceRow("Sort", listOf("Recent", "Quran order"), sort) { sort = it }
        }
        val matches =
            state.saved
                .filter { s ->
                    (selected == "All" || s.collection == selected) &&
                        (query.isBlank() ||
                            s.note.contains(query, true) ||
                            s.key.contains(query) ||
                            repo.verse(s.key)?.english?.contains(query, true) == true)
                }
                .let {
                    if (sort == "Recent") it.sortedByDescending { it.created }
                    else it.sortedBy { s -> repo.verses.indexOfFirst { it.key == s.key } }
                }
        if (matches.isEmpty())
            item {
                EmptyState(
                    "A place for reflection",
                    "Save an ayah while reading, then organize it into a collection or add a personal note.",
                    R.drawable.ic_bookmark,
                )
            }
        items(matches, key = { it.key }) { saved ->
            repo.verses
                .find { it.key == saved.key }
                ?.let { v ->
                    Surface(shape = NOS.Corner, color = MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { open(v.key) }) {
                                    Eyebrow(saved.collection)
                                    Text(
                                        "${repo.surahs[v.surah-1].name} · ${v.key}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Glyph(R.drawable.ic_ellipsis_vertical, "Bookmark actions") {
                                    actions(v)
                                }
                            }
                            Text(
                                v.translation(state.settings.language),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { open(v.key) },
                            )
                            if (saved.note.isNotEmpty()) {
                                HorizontalDivider()
                                Eyebrow("PERSONAL NOTE")
                                Text(saved.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
        }
    }
    if (collectionDialog)
        NosAlertDialog(
            onDismissRequest = { collectionDialog = false },
            title = { Text(if (rename) "Rename collection" else "New collection") },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                NosTextButton(
                    enabled = name.isNotBlank() && name !in state.collections,
                    onClick = {
                        if (rename) {
                            state.renameCollection(selected, name)
                            selected = name.trim()
                        } else state.addCollection(name)
                        collectionDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                NosTextButton(onClick = { collectionDialog = false }) { Text("Cancel") }
            },
        )
}
