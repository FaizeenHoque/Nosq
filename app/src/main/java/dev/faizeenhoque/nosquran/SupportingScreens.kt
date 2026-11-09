package dev.faizeenhoque.nosquran

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable
fun HistoryScreen(repo: QuranRepository, state: QuranState, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp)) {
        item { PageTitle("HISTORY", "Return to a familiar place") }
        if (state.history.isEmpty())
            item {
                EmptyState(
                    "A fresh beginning",
                    "Opened Surahs will appear here.",
                    R.drawable.ic_history,
                )
            }
        items(state.history, key = { it }) { key ->
            repo.verses
                .find { it.key == key }
                ?.let { v ->
                    SettingRow(
                        repo.surahs[v.surah - 1].name,
                        "Ayah ${v.number} · Juz ${v.juz}",
                        R.drawable.ic_history,
                        { open(key) },
                    )
                }
        }
    }
}

@Composable
fun StatisticsScreen(repo: QuranRepository, state: QuranState) {
    val completed = repo.surahs.count { s -> state.readCount(s.id) == s.count }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        PageTitle("YOUR JOURNEY", "One ayah at a time")
        NosPanel(Modifier.fillMaxWidth(), dark = true) {
            Text("${state.read.size}", fontFamily = NDot57, fontSize = 76.sp)
            Text("of 6,236 ayahs opened")
            LinearProgressIndicator(
                progress = { state.read.size / 6236f },
                Modifier.fillMaxWidth(),
                color = NOS.Red,
            )
        }
        SectionTitle("Reading record")
        SettingRow("Surahs completed", "$completed of 114")
        SettingRow("Reading sessions", state.sessions.toString())
        SettingRow("Saved ayahs", state.saved.size.toString())
        SettingRow("Personal notes", state.saved.count { it.note.isNotBlank() }.toString())
        Spacer(Modifier.height(24.dp))
        Text(
            "Statistics count ayahs brought into view. They are a quiet record of your visits, rather than a measure of comprehension or devotion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.settings.track)
            Text(
                "Progress tracking is paused in Settings.",
                modifier = Modifier.padding(top = 16.dp),
            )
    }
}

@Composable
fun DetailsScreen(
    repo: QuranRepository,
    state: QuranState,
    key: String,
    open: (String) -> Unit,
    actions: (Verse) -> Unit,
    navigate: (String) -> Unit,
) {
    val v = repo.verse(key) ?: return
    val context = LocalContext.current
    var size by remember { mutableFloatStateOf(state.settings.translationSize) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageTitle("AYAH ${v.number}", "${repo.surahs[v.surah-1].name} / ${v.key}")
            ArabicText(v.arabic, state.settings.arabicSize, state.settings.lineSpacing)
        }
        item {
            Eyebrow(
                if (state.settings.language == "Bengali") "TRANSLATION / MUHIUDDIN KHAN"
                else "TRANSLATION / SAHEEH INTERNATIONAL"
            )
            Spacer(Modifier.height(12.dp))
            Text(
                v.translation(state.settings.language),
                fontSize = size.sp,
                lineHeight = (size * 1.7f).sp,
            )
        }
        item {
            FontControl("Text size", size, 12f..26f) {
                size = it
                state.update(state.settings.copy(translationSize = it))
            }
            Row {
                NosTextButton(onClick = { actions(v) }) { Text("Save / Share / Note") }
                NosTextButton(onClick = { open(v.key) }) { Text("Read in context →") }
            }
        }
        item {
            HorizontalDivider()
            ExpandableSettings("Tafsir / Human commentary") {
                Text("Commentary is not bundled", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "A verified, licensed offline Tafsir edition has not yet been integrated. Read published commentary on Quran.com using the link below. An internet connection and external browser are required.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                NosOutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://quran.com/${v.surah}:${v.number}/tafsirs/en-tafisr-ibn-kathir"
                                ),
                            )
                        )
                    }
                ) {
                    Text("Ibn Kathir on Quran.com ↗")
                }
            }
        }
        item {
            SectionTitle("Verse details")
            SettingRow("Reference", v.key)
            SettingRow("Juz", v.juz.toString())
            SettingRow("Revelation", repo.surahs[v.surah - 1].origin)
            SettingRow("Numbering", "Hafs / Kufan · 6,236 ayahs")
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NosTextButton(
                    enabled = v.index > 0,
                    onClick = { navigate("details/${repo.verses[v.index-1].key}") },
                ) {
                    Text("← Previous ayah")
                }
                NosTextButton(
                    enabled = v.index < repo.verses.lastIndex,
                    onClick = { navigate("details/${repo.verses[v.index+1].key}") },
                ) {
                    Text("Next ayah →")
                }
            }
        }
    }
}

@Composable
fun InformationScreen(kind: String) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    LaunchedEffect(kind) {
        notice =
            withContext(Dispatchers.IO) {
                context.assets.open("quran/NOTICE.txt").bufferedReader().use { it.readText() }
            }
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp)
    ) {
        PageTitle(
            when (kind) {
                "licenses" -> "SOURCES"
                "privacy" -> "PRIVACY"
                else -> AppMetadata.Name
            },
            "Version ${AppMetadata.version} / Android 8.0+",
        )
        when (kind) {
            "privacy" -> {
                Text("Your reading is personal.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Reading positions, bookmarks, notes, preferences and statistics are saved on this device. nosq has no account, analytics or advertising SDK.\n\nAndroid backup may include app preferences according to your system backup settings. Exported bookmarks include your personal notes; choose a destination you trust.\n\nStreaming and downloading recitations contacts the selected provider (EveryAyah or MP3Quran). Refreshing the reciter catalog contacts both providers. External source and commentary links open your browser and follow the destination’s privacy policy. Arabic and translations can be read without a network connection.",
                    lineHeight = 26.sp,
                )
                SettingRow(
                    "Privacy inquiries",
                    AppMetadata.SupportEmail,
                    R.drawable.ic_external_link,
                    {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:${AppMetadata.SupportEmail}"),
                            )
                        )
                    },
                )
            }
            "licenses" -> {
                Text(notice, style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp)
                Spacer(Modifier.height(16.dp))
                SettingRow(
                    "Tanzil Project",
                    "Text license and updates",
                    R.drawable.ic_external_link,
                    {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://tanzil.net/docs/Text_License"),
                            )
                        )
                    },
                )
                SettingRow(
                    "Al Quran Cloud",
                    "Dataset API and editions",
                    R.drawable.ic_external_link,
                    {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://alquran.cloud/api"))
                        )
                    },
                )
                Text(
                    "Libraries: AndroidX, Jetpack Compose, Navigation, Lifecycle and Media3 — Apache License 2.0. Geist and Amiri — SIL Open Font License. NDot is an existing project asset; redistribution permission must be confirmed before release.",
                    modifier = Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {
                Text(
                    "The Quran.\nWithout distraction.",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "A quiet reading instrument, inspired by the clarity and precision of Nothing OS.\n\nBuilt with Kotlin, Jetpack Compose and the original Geist, NDot and Amiri Quran fonts.\n\nDeveloper · ${AppMetadata.Developer}\nPackage · dev.faizeenhoque.nosquran\n\nAn independent application. Not affiliated with Nothing Technology.",
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(24.dp))
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                SettingRow(
                    "App website",
                    "nosq.faizeenhoque.dev",
                    R.drawable.ic_external_link,
                    { uriHandler.openUri(AppMetadata.Website) },
                )
                SettingRow(
                    AppMetadata.Developer,
                    "faizeenhoque.dev",
                    R.drawable.ic_external_link,
                    { uriHandler.openUri(AppMetadata.DeveloperWebsite) },
                )
                SettingRow(
                    "Support",
                    AppMetadata.SupportEmail,
                    R.drawable.ic_external_link,
                    { uriHandler.openUri("mailto:${AppMetadata.SupportEmail}") },
                )
                Spacer(Modifier.height(32.dp))
                Eyebrow("READ. REFLECT. RETURN.")
            }
        }
    }
}

@Composable
fun OnboardingScreen(state: QuranState) {
    var step by remember { mutableIntStateOf(0) }
    val motion = LocalNosMotion.current
    AnimatedContent(
        step,
        transitionSpec = {
            fadeIn(tween(motion.duration())) togetherWith
                fadeOut(tween(motion.duration(MotionTokens.Fast)))
        },
        label = "onboarding step",
    ) { currentStep ->
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Eyebrow("nosq / ${currentStep+1} OF 3")
            Spacer(Modifier.height(24.dp))
            Text(
                if (currentStep == 0) "A little less\nnoise.\nA little more\nmeaning."
                else if (currentStep == 1) "Read with\nunderstanding."
                else "Make this\ntime yours.",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(12.dp))
            when (currentStep) {
                0 -> {
                    Text(
                        "A considered space for the Quran. All 114 Surahs, available wherever you are.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Eyebrow("ARABIC / ENGLISH / বাংলা")
                    Text(
                        "The interface is available in English. Choose your Quran translation on the next step.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                1 -> {
                    ChoiceRow(
                        "Translation language",
                        listOf("English", "Bengali", "None"),
                        state.settings.language,
                    ) {
                        state.update(state.settings.copy(language = it))
                    }
                    Text(
                        "English · Saheeh International\nBengali · Muhiuddin Khan\n\nTranslations explain the meaning and do not replace the Quranic Arabic.",
                        lineHeight = 25.sp,
                    )
                }
                2 -> {
                    ChoiceRow(
                        "Appearance",
                        listOf("System", "Light", "Dark"),
                        state.settings.theme,
                    ) {
                        state.update(state.settings.copy(theme = it))
                    }
                    ToggleRow(
                        "Remember my place",
                        "Resume the last ayah you opened",
                        state.settings.resume,
                    ) {
                        state.update(state.settings.copy(resume = it))
                    }
                    Text(
                        "No account. No ads. Your notes stay on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            NosButton(
                onClick = { if (step < 2) step++ else state.finishOnboarding() },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
            ) {
                Text(if (step < 2) "Continue →" else "Begin reading →")
            }
            if (step > 0) NosTextButton(onClick = { step-- }) { Text("Back") }
        }
    }
}
