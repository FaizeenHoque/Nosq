package dev.faizeenhoque.nosquran

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*

@Composable
fun DownloadsScreen(
    repo: QuranRepository,
    state: QuranState,
    notify: (String) -> Unit,
    navigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(DownloadStatus(0, 0, 0)) }
    var downloaded by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val reciter = state.settings.reciter
    val edition = ReciterCatalog.resolve(reciter)
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(reciter, owner, edition?.id) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                withContext(Dispatchers.IO) {
                    val snapshot = refreshDownloads(context)
                    val directory =
                        java.io.File(
                            context.getExternalFilesDir("recitations"),
                            edition?.storagePath.orEmpty(),
                        )
                    val counts =
                        directory
                            .listFiles()
                            .orEmpty()
                            .asSequence()
                            .filter { it.extension == "mp3" }
                            .mapNotNull {
                                it.nameWithoutExtension.substringBefore('_').toIntOrNull()
                            }
                            .groupingBy { it }
                            .eachCount()
                    withContext(Dispatchers.Main) {
                        status = snapshot
                        downloaded = counts
                    }
                }
                delay(if (status.queued > 0) 2000 else 10000)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp)) {
        item {
            PageTitle("OFFLINE AUDIO", "Your recitations. Anywhere.")
            NosPanel(Modifier.fillMaxWidth()) {
                Text(
                    String.format(java.util.Locale.ENGLISH, "%.1f MB", status.bytes / 1_048_576.0),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Eyebrow("${status.queued} QUEUED / ${status.failed} FAILED")
            }
            SettingRow(
                "Reciter",
                ReciterCatalog.label(reciter),
                R.drawable.ic_headphones,
                { navigate("reciters") },
            )
            Text(
                "Downloads wait for an unmetered connection. Keep the app installed to retain audio. Failed downloads can be cancelled and retried.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                NosTextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { cancelDownloads(context, false) }
                            notify("Pending and failed downloads cancelled")
                        }
                    },
                    enabled = status.queued + status.failed > 0,
                ) {
                    Text("Cancel pending")
                }
                NosTextButton(onClick = { confirm = true }) { Text("Delete all audio") }
            }
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Find a Surah") },
            )
            SectionTitle("Download a Surah", ReciterCatalog.label(reciter))
        }
        items(
            repo.surahs.filter {
                query.isBlank() || it.name.contains(query, true) || it.id.toString() == query
            },
            key = { it.id },
        ) { s ->
            val count = downloaded[s.id] ?: 0
            val total = if (edition?.verseLevel != false) s.count else 1
            val available = edition?.surahs?.contains(s.id) == true
            SettingRow(
                s.name,
                if (!available) "Not available in this recording"
                else if (edition?.verseLevel == false) "$count / 1 Surah recording downloaded"
                else "$count / ${s.count} ayahs downloaded",
                R.drawable.ic_headphones,
                trailing = {
                    NosTextButton(
                        enabled = available && count < total && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        downloadSurah(context, repo, s.id, reciter)
                                    }
                                }
                                    .onSuccess {
                                        notify(
                                            if (it > 0)
                                                "$it ${if(edition?.verseLevel==false)"recording"else "ayahs"} queued"
                                            else "Already downloaded or queued"
                                        )
                                    }
                                    .onFailure {
                                        notify(
                                            "Download could not be queued: ${it.localizedMessage}"
                                        )
                                    }
                                busy = false
                            }
                        },
                    ) {
                        Text(if (count >= total) "Saved" else "Download")
                    }
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (confirm)
        NosAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all recitations?") },
            text = {
                Text(
                    "Downloaded audio and pending downloads for every reciter will be removed. You can download them again."
                )
            },
            confirmButton = {
                NosTextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { cancelDownloads(context, true) }
                            status = DownloadStatus(0, 0, 0)
                            downloaded = emptyMap()
                            notify("Downloaded audio deleted")
                        }
                        confirm = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { NosTextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
}
