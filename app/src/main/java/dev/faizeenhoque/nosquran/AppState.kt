package dev.faizeenhoque.nosquran

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject

// The entire preference snapshot is saved atomically; Quran text is never written here.
@Immutable
data class ReaderSettings(
    val theme: String = "System",
    val oled: Boolean = false,
    val redAccent: Boolean = true,
    val compact: Boolean = false,
    val reduceMotion: Boolean = false,
    val motionProfile: String = "Balanced",
    val language: String = "English",
    val arabicSize: Float = 32f,
    val translationSize: Float = 16f,
    val lineSpacing: Float = 1.9f,
    val transliteration: Boolean = false,
    val separators: Boolean = true,
    val verseStyle: String = "Western",
    val layout: String = "Scroll",
    val keepAwake: Boolean = false,
    val track: Boolean = true,
    val resume: Boolean = true,
    val opening: String = "Home",
    val reciter: String = "Alafasy",
    val speed: Float = 1f,
    val autoAdvance: Boolean = true,
    val followRecitation: Boolean = true,
    val background: Boolean = true,
)

@Immutable
data class SavedVerse(
    val key: String,
    val collection: String = "Saved verses",
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
)

@Stable
class QuranState(application: Application) : AndroidViewModel(application) {
    private val prefs by lazy {
        application.getSharedPreferences("nos_state_v2", Context.MODE_PRIVATE)
    }
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<SharedPreferences.Editor.() -> Unit>(Channel.UNLIMITED)
    private var settingsJob: Job? = null
    private var progressJob: Job? = null
    var ready by mutableStateOf(false)
        private set

    var settings by mutableStateOf(ReaderSettings())
        private set

    var saved by mutableStateOf<List<SavedVerse>>(emptyList())
        private set

    var savedKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    var collections by mutableStateOf(setOf("Saved verses"))
        private set

    var last by mutableStateOf("1:1")
        private set

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    private val readMap = mutableStateMapOf<String, Boolean>()
    private val chapterCounts = mutableStateMapOf<Int, Int>()
    val read: Set<String>
        get() = readMap.keys

    fun readCount(surah: Int) = chapterCounts[surah] ?: 0

    var searches by mutableStateOf<List<String>>(emptyList())
        private set

    var onboarded by mutableStateOf(false)
        private set

    var sessions by mutableIntStateOf(0)
        private set

    init {
        persistenceScope.launch {
            try {
                for (write in writes) prefs.edit().apply(write).commit()
            } finally {
                persistenceScope.cancel()
            }
        }
        viewModelScope.launch {
            val snapshot =
                withContext(Dispatchers.IO) {
                    Triple(readSettings(), readSaved(), prefs.all)
                }
            settings = snapshot.first
            saved = snapshot.second
            savedKeys = saved.mapTo(hashSetOf()) { it.key }
            val values = snapshot.third
            @Suppress("UNCHECKED_CAST")
            collections = (values["collections"] as? Set<String>)?.toSet() ?: setOf("Saved verses")
            last = values["last"] as? String ?: "1:1"
            history = (values["history"] as? String).orEmpty().split('|').filter { it.isNotBlank() }
            @Suppress("UNCHECKED_CAST") val keys = (values["read"] as? Set<String>).orEmpty()
            keys.forEach { key ->
                readMap[key] = true
                key.substringBefore(':').toIntOrNull()?.let {
                    chapterCounts[it] = (chapterCounts[it] ?: 0) + 1
                }
            }
            searches =
                (values["searches"] as? String).orEmpty().split('|').filter { it.isNotBlank() }
            onboarded = values["onboarded"] as? Boolean ?: false
            sessions = values["sessions"] as? Int ?: 0
            ready = true
        }
    }

    override fun onCleared() {
        flushProgress()
        writes.close()
        super.onCleared()
    }

    private fun persist(block: SharedPreferences.Editor.() -> Unit) {
        writes.trySend(block)
    }

    private fun persistCollections() {
        val snapshot = collections
        persist { putStringSet("collections", snapshot) }
    }

    private fun readSettings(): ReaderSettings {
        val j = runCatching {
            JSONObject(prefs.getString("settings", "{}")!!)
        }
            .getOrDefault(JSONObject())
        return ReaderSettings(
            theme = j.optString("theme", "System"),
            oled = j.optBoolean("oled"),
            redAccent = j.optBoolean("redAccent", true),
            compact = j.optBoolean("compact"),
            reduceMotion = j.optBoolean("reduceMotion"),
            motionProfile =
                j.optString("motionProfile", "Balanced").takeIf {
                    it in listOf("Calm", "Balanced", "Expressive")
                } ?: "Balanced",
            language = j.optString("language", "English"),
            arabicSize = j.optDouble("arabicSize", 32.0).toFloat().coerceIn(22f, 52f),
            translationSize = j.optDouble("translationSize", 16.0).toFloat().coerceIn(12f, 26f),
            lineSpacing = j.optDouble("lineSpacing", 1.9).toFloat().coerceIn(1.5f, 2.5f),
            transliteration = j.optBoolean("transliteration"),
            separators = j.optBoolean("separators", true),
            verseStyle = j.optString("verseStyle", "Western"),
            layout = j.optString("layout", "Scroll"),
            keepAwake = j.optBoolean("keepAwake"),
            track = j.optBoolean("track", true),
            resume = j.optBoolean("resume", true),
            opening = j.optString("opening", "Home"),
            reciter = j.optString("reciter", "Alafasy"),
            speed = j.optDouble("speed", 1.0).toFloat().coerceIn(.5f, 2f),
            autoAdvance = j.optBoolean("autoAdvance", true),
            followRecitation = j.optBoolean("followRecitation", true),
            background = j.optBoolean("background", true),
        )
    }

    fun update(value: ReaderSettings) {
        settings = value
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            delay(160)
            persist { putString("settings", encodeSettings(value)) }
        }
    }

    private fun encodeSettings(value: ReaderSettings): String {
        val j =
            JSONObject().apply {
                put("theme", value.theme)
                put("oled", value.oled)
                put("redAccent", value.redAccent)
                put("compact", value.compact)
                put("reduceMotion", value.reduceMotion)
                put("motionProfile", value.motionProfile)
                put("language", value.language)
                put("arabicSize", value.arabicSize)
                put("translationSize", value.translationSize)
                put("lineSpacing", value.lineSpacing)
                put("transliteration", value.transliteration)
                put("separators", value.separators)
                put("verseStyle", value.verseStyle)
                put("layout", value.layout)
                put("keepAwake", value.keepAwake)
                put("track", value.track)
                put("resume", value.resume)
                put("opening", value.opening)
                put("reciter", value.reciter)
                put("speed", value.speed)
                put("autoAdvance", value.autoAdvance)
                put("followRecitation", value.followRecitation)
                put("background", value.background)
            }
        return j.toString()
    }

    private fun readSaved(): List<SavedVerse> = runCatching {
        val a = JSONArray(prefs.getString("saved", "[]"))
        (0 until a.length()).map {
            a.getJSONObject(it).let { j ->
                SavedVerse(
                    j.getString("key"),
                    j.optString("collection", "Saved verses"),
                    j.optString("note"),
                    j.optLong("created"),
                )
            }
        }
    }
        .getOrDefault(emptyList())

    private fun persistSaved() {
        savedKeys = saved.mapTo(hashSetOf()) { it.key }
        val snapshot = saved
        persist { putString("saved", encodeSaved(snapshot)) }
    }

    fun toggle(key: String) {
        saved =
            if (saved.any { it.key == key }) saved.filterNot { it.key == key }
            else saved + SavedVerse(key)
        persistSaved()
    }

    fun annotate(key: String, note: String, collection: String) {
        val old = saved.find { it.key == key } ?: SavedVerse(key)
        saved = saved.filterNot { it.key == key } + old.copy(note = note, collection = collection)
        persistSaved()
    }

    fun addCollection(name: String) {
        if (name.isNotBlank()) {
            collections = collections + name.trim()
            persistCollections()
        }
    }

    fun renameCollection(old: String, new: String) {
        if (new.isBlank() || new in collections) return
        collections = collections - old + new.trim()
        saved = saved.map { if (it.collection == old) it.copy(collection = new.trim()) else it }
        persistCollections()
        persistSaved()
    }

    fun deleteCollection(name: String) {
        collections = collections - name + "Saved verses"
        saved = saved.map {
            if (it.collection == name) it.copy(collection = "Saved verses") else it
        }
        persistCollections()
        persistSaved()
    }

    fun position(v: Verse) {
        if (!settings.resume && !settings.track) return
        if (settings.resume) last = v.key
        if (settings.track && readMap.put(v.key, true) == null)
            chapterCounts[v.surah] = readCount(v.surah) + 1
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(500)
            flushProgress()
        }
    }

    fun flushProgress() {
        if (!ready) return
        progressJob?.cancel()
        val keys = readMap.keys.toSet()
        val position = last
        persist {
            putString("last", position)
            putStringSet("read", keys)
        }
        if (settingsJob?.isActive == true) {
            settingsJob?.cancel()
            val current = settings
            persist { putString("settings", encodeSettings(current)) }
        }
    }

    fun opened(key: String) {
        history = (listOf(key) + history.filterNot { it == key }).take(30)
        if (settings.track) sessions++
        val recent = history.joinToString("|")
        val count = sessions
        persist {
            putString("history", recent)
            putInt("sessions", count)
        }
    }

    fun search(q: String) {
        if (q.isBlank()) return
        searches = (listOf(q.replace('|', ' ')) + searches.filterNot { it == q }).take(8)
        val snapshot = searches.joinToString("|")
        persist { putString("searches", snapshot) }
    }

    fun finishOnboarding() {
        onboarded = true
        persist { putBoolean("onboarded", true) }
    }

    fun resetProgress() {
        progressJob?.cancel()
        readMap.clear()
        chapterCounts.clear()
        sessions = 0
        last = "1:1"
        history = emptyList()
        persist {
            remove("read")
            remove("sessions")
            remove("last")
            remove("history")
        }
    }

    fun export(): String = encodeSaved(saved)

    private fun encodeSaved(snapshot: List<SavedVerse>): String =
        JSONArray()
            .apply {
                snapshot.forEach {
                    put(
                        JSONObject()
                            .put("key", it.key)
                            .put("collection", it.collection)
                            .put("note", it.note)
                            .put("created", it.created)
                    )
                }
            }
            .toString(2)

    suspend fun import(json: String, repo: QuranRepository) {
        val incoming =
            withContext(Dispatchers.Default) {
                require(json.length < 2_000_000) { "File is too large" }
                val a = JSONArray(json)
                require(a.length() <= 6236) { "Too many bookmarks" }
                (0 until a.length()).map {
                    a.getJSONObject(it).let { j ->
                        val key = j.getString("key")
                        require(repo.verse(key) != null) { "Invalid verse $key" }
                        SavedVerse(
                            key,
                            j.optString("collection", "Saved verses").take(100),
                            j.optString("note").take(20000),
                            j.optLong("created", System.currentTimeMillis()),
                        )
                    }
                }
            }
        saved = (saved + incoming).distinctBy { it.key }
        collections = collections + incoming.map { it.collection }
        persistCollections()
        persistSaved()
    }
}

fun Verse.translation(language: String) = if (language == "Bengali") bengali else english
