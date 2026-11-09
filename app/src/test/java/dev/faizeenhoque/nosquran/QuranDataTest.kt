package dev.faizeenhoque.nosquran

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class QuranDataTest {
    private val assets = File("src/main/assets/quran")

    @Test
    fun completeKufanNumberingAndAllEditionsAlign() {
        val surahs = File(assets, "surahs.tsv").readLines().map { it.split('\t') }
        val metadata = File(assets, "meta.tsv").readLines().map { it.split('\t') }
        assertEquals(114, surahs.size)
        assertEquals(6236, metadata.size)
        assertEquals((1..114).toList(), surahs.map { it[0].toInt() })
        var offset = 0
        surahs.forEach { s ->
            val id = s[0].toInt()
            val count = s[5].toInt()
            val chapter = metadata.subList(offset, offset + count)
            assertTrue(chapter.all { it[0].toInt() == id })
            assertEquals((1..count).toList(), chapter.map { it[1].toInt() })
            offset += count
        }
        assertEquals(6236, offset)
        assertEquals((1..30).toSet(), metadata.map { it[2].toInt() }.toSet())
        assertTrue(metadata.zipWithNext().all { (a, b) -> b[2].toInt() >= a[2].toInt() })
        listOf("ar.txt", "en.sahih.txt", "bn.bengali.txt", "en.transliteration.txt").forEach { name
            ->
            val lines = File(assets, name).readLines()
            assertEquals(name, 6236, lines.size)
            assertTrue(name, lines.all { it.isNotBlank() })
        }
    }

    @Test
    fun verifiedAssetFingerprintsRemainUnchanged() {
        val manifest = File(assets, "integrity.json").readText()
        val pairs = Regex("\"([^\"]+)\": \"([a-f0-9]{64})\"").findAll(manifest).toList()
        assertEquals(6, pairs.size)
        pairs.forEach { match ->
            val bytes = File(assets, match.groupValues[1]).readBytes()
            val sha =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                }
            assertEquals(match.groupValues[1], match.groupValues[2], sha)
        }
    }

    @Test
    fun arabicSearchIgnoresMarksWithoutChangingStoredText() {
        val original = "بِسْمِ ٱللَّهِ"
        assertEquals("بسم الله", normalizeArabic(original))
        assertEquals("بِسْمِ ٱللَّهِ", original)
    }
}
