package dev.faizeenhoque.nosquran

import android.content.Context
import androidx.compose.runtime.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.json.JSONObject

@Immutable
data class RecitationEdition(
    val id: String,
    val name: String,
    val arabicName: String,
    val provider: String,
    val variant: String,
    val quality: String,
    val folder: String = "",
    val server: String = "",
    val surahs: Set<Int> = (1..114).toSet(),
    val translation: Boolean = false,
) {
    val verseLevel
        get() = provider == "EveryAyah"

    val description
        get() =
            "$provider · ${if(verseLevel) "Verse by verse" else "Full Surah"} · MP3${if(quality.isBlank()) "" else " · $quality"}"

    val storagePath
        get() = if (verseLevel) folder else "mp3quran/${id.substringAfter(':')}"

    fun url(surah: Int, verse: Int): String =
        if (verseLevel)
            "https://everyayah.com/data/$folder/${surah.toString().padStart(3,'0')}${verse.toString().padStart(3,'0')}.mp3"
        else "${server.trimEnd('/')}/${surah.toString().padStart(3,'0')}.mp3"
}

/**
 * Provider identifiers, recording editions and bitrates remain distinct; names are never dedup
 * keys.
 */
object ReciterCatalog {
    const val EveryAyahUrl = "https://everyayah.com/data/recitations.js"
    const val Mp3EnglishUrl = "https://mp3quran.net/api/v3/reciters?language=eng"
    const val Mp3ArabicUrl = "https://mp3quran.net/api/v3/reciters?language=ar"
    var editions by mutableStateOf<List<RecitationEdition>>(emptyList())
        private set

    var refreshing by mutableStateOf(false)
        private set

    var status by mutableStateOf("Bundled provider catalog")
        private set

    @Volatile private var lookup: Map<String, RecitationEdition> = emptyMap()
    private val aliases =
        mapOf(
            "Alafasy" to "Alafasy_128kbps",
            "Abdul Basit" to "Abdul_Basit_Murattal_192kbps",
            "Husary" to "Husary_128kbps",
        )

    fun resolve(selection: String): RecitationEdition? =
        lookup[selection] ?: aliases[selection]?.let { lookup["ea:$it"] }

    fun label(selection: String) = resolve(selection)?.name ?: selection

    fun selected(selection: String, id: String) = resolve(selection)?.id == id

    private fun publish(entries: List<RecitationEdition>) {
        val ordered =
            entries
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.variant }, { it.quality }))
        lookup = ordered.associateBy { it.id }
        editions = ordered
    }

    suspend fun load(context: Context) {
        if (editions.isNotEmpty()) return
        val entries =
            withContext(Dispatchers.IO) {
                fun source(name: String): String {
                    val cached = File(context.filesDir, "reciters/$name")
                    return runCatching {
                        if (cached.exists()) cached.readText() else error("No cache")
                    }
                        .getOrElse {
                            context.assets.open("audio/$name").bufferedReader().use {
                                it.readText()
                            }
                        }
                }
                parseEvery(source("everyayah.json")) +
                    parseMp3(source("mp3quran-en.json"), source("mp3quran-ar.json"))
            }
        publish(entries)
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        load(context)
        if (refreshing) return
        val folder = File(context.filesDir, "reciters")
        val stamp = File(folder, "updated")
        if (
            !force &&
                withContext(Dispatchers.IO) {
                    stamp.exists() &&
                        System.currentTimeMillis() - stamp.lastModified() < 24 * 60 * 60 * 1000
                }
        )
            return
        refreshing = true
        try {
            val results =
                withContext(Dispatchers.IO) {
                    supervisorScope {
                        listOf(EveryAyahUrl, Mp3EnglishUrl, Mp3ArabicUrl)
                            .map { url -> async { runCatching { fetch(url) } } }
                            .awaitAll()
                    }
                }
            val every = results[0].getOrNull()?.let { runCatching { parseEvery(it) }.getOrNull() }
            val mp3 =
                if (results[1].isSuccess && results[2].isSuccess)
                    runCatching { parseMp3(results[1].getOrThrow(), results[2].getOrThrow()) }
                        .getOrNull()
                else null
            val replacement =
                (every ?: editions.filter { it.provider == "EveryAyah" }) +
                    (mp3 ?: editions.filter { it.provider == "MP3Quran" })
            require(replacement.isNotEmpty())
            withContext(Dispatchers.IO) {
                folder.mkdirs()
                for ((index, name) in
                    listOf("everyayah.json", "mp3quran-en.json", "mp3quran-ar.json").withIndex()) {
                    if (index == 0 && every != null || index > 0 && mp3 != null) {
                        val destination = File(folder, name)
                        val temporary = File(folder, "$name.tmp")
                        temporary.writeText(results[index].getOrThrow())
                        check(temporary.renameTo(destination))
                    }
                }
                if (every != null && mp3 != null)
                    stamp.writeText(System.currentTimeMillis().toString())
            }
            publish(replacement)
            status =
                if (every != null && mp3 != null) "Provider catalog up to date"
                else "Some providers unavailable · saved catalog retained"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = "Offline · using saved provider catalog"
        } finally {
            refreshing = false
        }
    }

    private fun fetch(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        return try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { reader ->
                reader.readText().also { require(it.length < 2_000_000) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEvery(raw: String): List<RecitationEdition> {
        val root = JSONObject(raw)
        return root
            .keys()
            .asSequence()
            .mapNotNull { root.optJSONObject(it) }
            .filter { it.has("subfolder") }
            .map { entry ->
                val path = entry.getString("subfolder")
                require(
                    !path.startsWith('/') &&
                        !path.contains("..") &&
                        path.matches(Regex("[A-Za-z0-9_./-]+"))
                )
                val translation = path.startsWith("translations/")
                val variant =
                    when {
                        translation -> "Spoken translation"
                        path.contains("warsh", true) -> "Warsh"
                        path.contains("muallim", true) -> "Muallim"
                        path.contains("mujawwad", true) -> "Mujawwad"
                        path.contains("murattal", true) -> "Murattal"
                        else -> "Recitation"
                    }
                RecitationEdition(
                    "ea:$path",
                    entry.getString("name").replace('_', ' '),
                    "",
                    "EveryAyah",
                    variant,
                    entry.optString("bitrate"),
                    folder = path,
                    translation = translation,
                )
            }
            .toList()
            .also { require(it.isNotEmpty()) }
    }

    private fun parseMp3(english: String, arabic: String): List<RecitationEdition> {
        val ar = JSONObject(arabic).getJSONArray("reciters")
        val arabicById =
            (0 until ar.length()).associate {
                ar.getJSONObject(it).let { r -> r.getInt("id") to r }
            }
        val en = JSONObject(english).getJSONArray("reciters")
        return buildList {
            for (index in 0 until en.length()) {
                val reciter = en.getJSONObject(index)
                val matches = arabicById[reciter.getInt("id")]
                val recordings = reciter.getJSONArray("moshaf")
                for (i in 0 until recordings.length()) {
                    val recording = recordings.getJSONObject(i)
                    val server = recording.getString("server")
                    require(server.startsWith("https://"))
                    val surahs =
                        recording
                            .getString("surah_list")
                            .split(',')
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 1..114 }
                            .toSet()
                    if (surahs.isNotEmpty())
                        add(
                            RecitationEdition(
                                "mp3:${recording.getInt("id")}",
                                reciter.getString("name"),
                                matches?.optString("name").orEmpty(),
                                "MP3Quran",
                                recording.getString("name"),
                                "",
                                server = server,
                                surahs = surahs,
                            )
                        )
                }
            }
        }
            .also { require(it.isNotEmpty()) }
    }
}
