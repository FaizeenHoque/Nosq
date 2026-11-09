package dev.faizeenhoque.nosquran

import android.os.Bundle
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchPerformanceTest {
    @Test
    fun indexedSearchLatencyAndReferenceEquivalence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repo = QuranRepository(instrumentation.targetContext)
        val report = JSONObject()
        for (query in listOf("mercy", "رحمة", "2:255", "Allah", "Al-Baqara")) {
            // The previous UI algorithm, preserved here as an independent reference.
            fun reference(): List<Verse> {
                val q = query.trim()
                val normalized = normalizeArabic(q)
                return repo.verses.filter { v ->
                    v.key == q ||
                        v.english.contains(q, true) ||
                        v.bengali.contains(q) ||
                        normalizeArabic(v.arabic).contains(normalized) ||
                        repo.surahs[v.surah - 1].name.contains(q, true) ||
                        repo.surahs[v.surah - 1].meaning.contains(q, true) ||
                        normalizeArabic(repo.surahs[v.surah - 1].arabic).contains(normalized)
                }
            }
            val expected = reference().map { it.key }
            val initial = System.nanoTime()
            assertEquals(expected, repo.search(query).map { it.key })
            val firstMs = (System.nanoTime() - initial) / 1e6
            val before = Debug.getRuntimeStat("art.gc.bytes-allocated")!!.toLong()
            val indexed =
                List(15) {
                        val t = System.nanoTime()
                        repo.search(query)
                        (System.nanoTime() - t) / 1e6
                    }
                    .sorted()
            val bytes = Debug.getRuntimeStat("art.gc.bytes-allocated")!!.toLong() - before
            val referenceTimes =
                List(5) {
                        val t = System.nanoTime()
                        reference()
                        (System.nanoTime() - t) / 1e6
                    }
                    .sorted()
            report.put(
                query,
                JSONObject()
                    .put("firstMs", firstMs)
                    .put("indexedMedianMs", indexed[7])
                    .put("indexedP95Ms", indexed[14])
                    .put("referenceMedianMs", referenceTimes[2])
                    .put("processAllocationDeltaBytes", bytes),
            )
            assertEquals(
                expected.filter { repo.verse(it)?.surah == 2 },
                repo.search(query, 2).map { it.key },
            )
        }
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("searchPerformance", report.toString()) },
        )
    }
}
