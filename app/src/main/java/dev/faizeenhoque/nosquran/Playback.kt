package dev.faizeenhoque.nosquran

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

fun audioUrl(reciter: String, v: Verse): String =
    requireNotNull(ReciterCatalog.resolve(reciter)) { "Choose an available recitation" }
        .url(v.surah, v.number)

fun audioFile(context: Context, reciter: String, v: Verse): File {
    val edition =
        requireNotNull(ReciterCatalog.resolve(reciter)) { "Choose an available recitation" }
    val name = if (edition.verseLevel) "${v.surah}_${v.number}.mp3" else "${v.surah}.mp3"
    return File(context.getExternalFilesDir("recitations"), "${edition.storagePath}/$name")
}

class QuranPlaybackService : MediaSessionService() {
    companion object {
        @Volatile
        var running = false
            private set
    }

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        if (BuildConfig.BENCHMARK)
            player.volume = 0f // Device benchmarks never emit audible recitation.
        session = MediaSession.Builder(this, player).build()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) =
        if (controllerInfo.isTrusted || controllerInfo.packageName == packageName) session else null

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        running = false
        super.onDestroy()
    }
}

@Stable
class Playback(private val context: Context, private val scope: CoroutineScope) {
    var controller: MediaController? by mutableStateOf(null)
    var key by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var repeat by mutableStateOf(false)
    var verseLevel by mutableStateOf(true)
    var activeReciter by mutableStateOf("")
    var notice by mutableStateOf<String?>(null)

    var connectRequested by mutableStateOf(QuranPlaybackService.running)
    private var prepareJob: Job? = null

    fun play(repo: QuranRepository, v: Verse, s: ReaderSettings, end: Int? = null) {
        connectRequested = true
        activeReciter = ReciterCatalog.resolve(s.reciter)?.id.orEmpty()
        key = v.key
        prepareJob?.cancel()
        buffering = true
        prepareJob = scope.launch {
            try {
                val items =
                    withContext(Dispatchers.IO) {
                        ReciterCatalog.load(context)
                        refreshDownloads(context)
                        val edition =
                            requireNotNull(ReciterCatalog.resolve(s.reciter)) {
                                "Select an available recitation"
                            }
                        require(v.surah in edition.surahs) {
                            "This recording does not include Surah ${v.surah}"
                        }
                        val queue =
                            if (!edition.verseLevel) listOf(repo.chapter(v.surah).first())
                            else
                                repo.chapter(v.surah).filter {
                                    it.surah == v.surah &&
                                        it.number >= v.number &&
                                        it.number <=
                                            (end
                                                ?: if (s.autoAdvance) repo.surahs[v.surah - 1].count
                                                else v.number)
                                }
                        queue.map { verse ->
                            val file = audioFile(context, s.reciter, verse)
                            MediaItem.Builder()
                                .setMediaId(verse.key)
                                .setUri(
                                    if (file.exists() && file.length() > 0) Uri.fromFile(file)
                                    else Uri.parse(audioUrl(s.reciter, verse))
                                )
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(
                                            "${repo.surahs[verse.surah-1].name} · ${verse.number}"
                                        )
                                        .setArtist(edition.name)
                                        .setExtras(
                                            android.os.Bundle().apply {
                                                putString("reciterId", edition.id)
                                                putBoolean("verseLevel", edition.verseLevel)
                                            }
                                        )
                                        .setAlbumTitle(AppMetadata.Name)
                                        .build()
                                )
                                .build()
                        }
                    }
                ensureActive()
                val c =
                    withTimeoutOrNull(10000) { snapshotFlow { controller }.first { it != null } }
                        ?: run {
                            buffering = false
                            error = "Audio service could not connect. Please retry."
                            return@launch
                        }
                val edition = requireNotNull(ReciterCatalog.resolve(s.reciter))
                verseLevel = edition.verseLevel
                activeReciter = edition.id
                notice =
                    if (!edition.verseLevel)
                        "Full-Surah recording · verse seeking and repeat ranges are unavailable. Playback begins at the start of the Surah."
                    else null
                error = null
                c.setMediaItems(items)
                c.setPlaybackSpeed(s.speed)
                c.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                c.prepare()
                c.play()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                buffering = false
                error =
                    "Recitation could not be prepared. Check storage and connectivity, then retry."
            }
        }
    }

    fun toggle() {
        controller?.let {
            if (it.isPlaying) it.pause()
            else {
                if (it.playerError != null) it.prepare()
                it.play()
            }
        }
    }

    fun stop() {
        prepareJob?.cancel()
        buffering = false
        controller?.stop()
        controller?.clearMediaItems()
        key = null
    }

    fun updateRepeat(value: Boolean) {
        repeat = value
        controller?.repeatMode = if (value) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }
}

@Composable
fun rememberPlayback(background: Boolean): Playback {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val audio = remember { Playback(context, scope) }
    if (audio.connectRequested)
        DisposableEffect(Unit) {
            val future =
                MediaController.Builder(
                        context,
                        SessionToken(
                            context,
                            ComponentName(context, QuranPlaybackService::class.java),
                        ),
                    )
                    .buildAsync()
            var attached: MediaController? = null
            val listener =
                object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        player.currentMediaItem?.mediaMetadata?.extras?.let { extras ->
                            audio.verseLevel = extras.getBoolean("verseLevel", true)
                            audio.activeReciter = extras.getString("reciterId").orEmpty()
                        }
                        audio.position = player.currentPosition.coerceAtLeast(0)
                        audio.duration = player.duration.coerceAtLeast(0)
                        audio.key = player.currentMediaItem?.mediaId
                        audio.playing = player.isPlaying
                        audio.buffering = player.playbackState == Player.STATE_BUFFERING
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        audio.error =
                            "Recitation could not be loaded. Connect to the internet or download this Surah, then retry."
                    }
                }
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { c ->
                            attached = c
                            audio.controller = c
                            c.addListener(listener)
                            audio.key = c.currentMediaItem?.mediaId ?: audio.key
                            audio.verseLevel =
                                c.mediaMetadata.extras?.getBoolean("verseLevel", true) ?: true
                            audio.activeReciter =
                                c.mediaMetadata.extras?.getString("reciterId").orEmpty()
                            audio.playing = c.isPlaying
                        }
                },
                ContextCompat.getMainExecutor(context),
            )
            onDispose {
                attached?.removeListener(listener)
                MediaController.releaseFuture(future)
                audio.controller = null
            }
        }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, background) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !background)
                    audio.controller?.pause()
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(audio.controller, owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            snapshotFlow { audio.playing || audio.buffering }
                .collectLatest { active ->
                    if (active)
                        while (isActive) {
                            audio.controller?.let {
                                audio.position = it.currentPosition.coerceAtLeast(0)
                                audio.duration = it.duration.coerceAtLeast(0)
                            }
                            delay(250)
                        }
                }
        }
    }
    return audio
}

fun downloadSurah(context: Context, repo: QuranRepository, surah: Int, reciter: String): Int {
    refreshDownloads(context)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    val queued = prefs.all.values.map { it.toString() }.toSet()
    var count = 0
    val edition = requireNotNull(ReciterCatalog.resolve(reciter))
    require(surah in edition.surahs) { "Surah not available in this recording" }
    val tracks =
        if (edition.verseLevel) repo.chapter(surah) else listOf(repo.chapter(surah).first())
    tracks.forEach { v ->
        val file = audioFile(context, reciter, v)
        val partial = File(file.path + ".part")
        if (!file.exists() && partial.path !in queued) {
            file.parentFile?.mkdirs()
            if (partial.exists()) partial.delete()
            val request =
                DownloadManager.Request(Uri.parse(audioUrl(reciter, v)))
                    .setTitle("nosq · ${v.key}")
                    .setDescription(edition.name)
                    .setDestinationUri(Uri.fromFile(partial))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(false)
            val id = manager.enqueue(request)
            prefs.edit().putString(id.toString(), partial.path).apply()
            count++
        }
    }
    return count
}

data class DownloadStatus(val queued: Int, val failed: Int, val bytes: Long)

fun refreshDownloads(context: Context): DownloadStatus {
    val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    var queued = 0
    var failed = 0
    val pending = prefs.all
    val statuses = mutableMapOf<Long, Int>()
    if (pending.isNotEmpty())
        manager
            .query(
                DownloadManager.Query()
                    .setFilterById(*pending.keys.map { it.toLong() }.toLongArray())
            )
            .use { cursor ->
                while (cursor.moveToNext()) statuses[
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))] =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
    pending.forEach { (id, path) ->
        when (statuses[id.toLong()]) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val partial = File(path.toString())
                val destination = File(partial.path.removeSuffix(".part"))
                if (partial.exists() && partial.length() > 0 && partial.renameTo(destination))
                    prefs.edit().remove(id).apply()
            }
            DownloadManager.STATUS_FAILED -> {
                failed++
            }
            null -> {
                File(path.toString()).delete()
                prefs.edit().remove(id).apply()
            }
            else -> queued++
        }
    }
    val bytes =
        File(context.getExternalFilesDir("recitations"), "")
            .walkTopDown()
            .filter { it.isFile && it.extension == "mp3" }
            .sumOf { it.length() }
    return DownloadStatus(queued, failed, bytes)
}

fun cancelDownloads(context: Context, clear: Boolean) {
    val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    prefs.all.forEach { (id, path) ->
        manager.remove(id.toLong())
        File(path.toString()).delete()
    }
    prefs.edit().clear().apply()
    if (clear) context.getExternalFilesDir("recitations")?.deleteRecursively()
}

fun timeLabel(ms: Long) = "${ms/60000}:${(ms/1000%60).toString().padStart(2,'0')}"
