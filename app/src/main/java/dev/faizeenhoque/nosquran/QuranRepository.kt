package dev.faizeenhoque.nosquran

import android.content.Context
import kotlinx.coroutines.ensureActive

@androidx.compose.runtime.Immutable
data class Surah(
    val id: Int,
    val arabic: String,
    val name: String,
    val meaning: String,
    val origin: String,
    val count: Int,
)

@androidx.compose.runtime.Immutable
data class Verse(
    val index: Int,
    val surah: Int,
    val number: Int,
    val juz: Int,
    val arabic: String,
    val english: String,
    val bengali: String,
    val transliteration: String,
) {
    val key = "$surah:$number"
}

@androidx.compose.runtime.Stable
class QuranRepository(private val context: Context) {
    private fun lines(file: String) =
        context.assets.open("quran/$file").bufferedReader().use { it.readLines() }

    init {
        val manifest =
            org.json.JSONObject(
                context.assets.open("quran/integrity.json").bufferedReader().use { it.readText() }
            )
        manifest.keys().forEach { file ->
            val bytes = context.assets.open("quran/$file").use { it.readBytes() }
            val digest =
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
            require(digest == manifest.getString(file)) {
                "Quran asset integrity check failed: $file"
            }
        }
    }

    val surahs: List<Surah> =
        lines("surahs.tsv").map {
            it.split('\t').let { p -> Surah(p[0].toInt(), p[1], p[2], p[3], p[4], p[5].toInt()) }
        }
    val verses: List<Verse>

    init {
        val ar = lines("ar.txt")
        val en = lines("en.sahih.txt")
        val bn = lines("bn.bengali.txt")
        val tr = lines("en.transliteration.txt")
        val meta = lines("meta.tsv")
        require(listOf(ar, en, bn, tr, meta).all { it.size == 6236 }) { "Incomplete Quran data" }
        verses = meta.mapIndexed { i, row ->
            row.split('\t').let { p ->
                Verse(
                    i,
                    p[0].toInt(),
                    p[1].toInt(),
                    p[2].toInt(),
                    ar[i],
                    en[i],
                    bn[i],
                    tr[i].let {
                        if ('<' in it || '&' in it) android.text.Html.fromHtml(it, 0).toString()
                        else it
                    },
                )
            }
        }
        require(surahs.size == 114 && surahs.sumOf { it.count } == 6236)
        val grouped = verses.groupBy { it.surah }
        surahs.forEachIndexed { i, s ->
            require(s.id == i + 1)
            require(grouped.getValue(s.id).map { it.number } == (1..s.count).toList()) {
                "Invalid verse ordering in ${s.id}"
            }
        }
        require(verses.all { it.juz in 1..30 && it.arabic.isNotBlank() && it.english.isNotBlank() })
    }

    // Immutable indexes built once off the UI thread with the repository.
    val byKey = verses.associateBy { it.key }
    val bySurah = verses.groupBy { it.surah }
    val byJuz = verses.groupBy { it.juz }

    fun verse(key: String?): Verse? = byKey[key]

    fun chapter(id: Int): List<Verse> = bySurah[id].orEmpty()

    private data class SearchEntry(val verse: Verse, val arabic: String, val english: String)

    private val chapterNames = surahs.map {
        normalizeArabic("${it.name} ${it.meaning} ${it.arabic}").lowercase(java.util.Locale.ROOT)
    }
    private val searchIndex by lazy {
        android.os.Trace.beginSection("NOS.searchIndex")
        try {
            verses.map {
                SearchEntry(
                    it,
                    normalizeArabic(it.arabic),
                    it.english.lowercase(java.util.Locale.ROOT),
                )
            }
        } finally {
            android.os.Trace.endSection()
        }
    }

    suspend fun prepareSearch() =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            searchIndex.size
        }

    suspend fun search(query: String, surah: Int = 0): List<Verse> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            android.os.Trace.beginSection("NOS.search")
            try {
                val q = query.trim().lowercase(java.util.Locale.ROOT)
                if (q.isEmpty()) return@withContext emptyList()
                byKey[q]?.let {
                    return@withContext if (surah == 0 || it.surah == surah) listOf(it)
                    else emptyList()
                }
                val arabic = normalizeArabic(q)
                val matchingChapters = chapterNames.map { it.contains(arabic) }
                buildList {
                    searchIndex.forEachIndexed { index, entry ->
                        if (index % 128 == 0)
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val v = entry.verse
                        if (
                            (surah == 0 || v.surah == surah) &&
                                (matchingChapters[v.surah - 1] ||
                                    entry.english.contains(q) ||
                                    v.bengali.contains(q) ||
                                    (arabic.isNotBlank() && entry.arabic.contains(arabic)))
                        )
                            add(v)
                    }
                }
            } finally {
                android.os.Trace.endSection()
            }
        }
}

private val ArabicMarks = Regex("[\\p{M}ـ]")

fun normalizeArabic(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(ArabicMarks, "")
        .replace('ٱ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
