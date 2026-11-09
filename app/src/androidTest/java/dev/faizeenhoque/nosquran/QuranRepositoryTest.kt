package dev.faizeenhoque.nosquran

import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranRepositoryTest {
    @Test
    fun offlineAssetsPassIntegrityAndConstructEveryVerse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = QuranRepository(context)
        assertEquals(114, repository.surahs.size)
        assertEquals(6236, repository.verses.size)
        assertEquals("1:1", repository.verses.first().key)
        assertEquals("114:6", repository.verses.last().key)
        assertEquals(3, repository.verses.first { it.key == "2:255" }.juz)
        assertTrue(
            repository.verses.all {
                it.arabic.isNotBlank() && it.english.isNotBlank() && it.bengali.isNotBlank()
            }
        )
        assertFalse(
            repository.verses
                .first { it.key == "2:1" }
                .arabic
                .startsWith(repository.verses.first().arabic)
        )
        assertFalse(
            repository.verses
                .first { it.key == "9:1" }
                .arabic
                .startsWith(repository.verses.first().arabic)
        )
        listOf(R.font.geist_variable, R.font.ndot_57_aligned, R.font.amiriquran_regular).forEach {
            assertNotNull(ResourcesCompat.getFont(context, it))
        }
    }
}
