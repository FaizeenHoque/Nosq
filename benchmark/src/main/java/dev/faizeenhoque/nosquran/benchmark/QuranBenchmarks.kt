package dev.faizeenhoque.nosquran.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.runner.RunWith

const val TARGET = "dev.faizeenhoque.nosquran.benchmark"

fun launchIntent(route: String) =
    Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName(TARGET, "dev.faizeenhoque.nosquran.MainActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra("benchmarkRoute", route)

fun androidx.benchmark.macro.MacrobenchmarkScope.openRoute(route: String) {
    val result =
        device.executeShellCommand(
            "am start -W -a android.intent.action.MAIN -n $TARGET/dev.faizeenhoque.nosquran.MainActivity -f 0x10008000 --es benchmarkRoute $route"
        )
    check(!result.contains("Error:")) { result }
    device.waitForIdle()
    device.awaitText(
        when (route) {
            "Home" -> "PICK UP WHERE YOU LEFT OFF"
            "Settings" -> "SETTINGS"
            "Search" -> "SEARCH"
            "player" -> "RECITATION"
            "reciters" -> "RECITERS"
            else -> "Al-Baqara"
        }
    )
}

fun UiDevice.clickTab(name: String) {
    val index = listOf("Today", "Quran", "Search", "Saved", "Settings").indexOf(name)
    require(index >= 0)
    // Bottom tabs have equal widths; coordinates avoid stale accessibility nodes mid-transition.
    click(displayWidth * (2 * index + 1) / 10, displayHeight * 935 / 1000)
    waitForIdle()
}

fun UiDevice.setQuery(query: String) {
    repeat(8) {
        try {
            wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = query
            return
        } catch (stale: StaleObjectException) {
            waitForIdle()
        }
    }
    error("Search field did not settle")
}

fun UiDevice.awaitText(text: String) {
    check(wait(Until.hasObject(By.text(text)), 10000)) { "Missing UI: $text" }
}

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class QuranBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() =
        rule.measureRepeated(
            TARGET,
            listOf(StartupTimingMetric()),
            CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                killProcess()
            },
        ) {
            openRoute("Home")
            device.awaitText("NOS QURAN")
        }

    @Test
    fun profiledColdStartup() =
        rule.measureRepeated(
            TARGET,
            listOf(StartupTimingMetric()),
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                killProcess()
            },
        ) {
            openRoute("Home")
        }

    @Test
    fun readerScroll() =
        rule.measureRepeated(
            TARGET,
            listOf(FrameTimingMetric()),
            CompilationMode.None(),
            iterations = 3,
            setupBlock = {
                openRoute("reader/2:1")
                device.awaitText("Al-Baqara")
            },
        ) {
            repeat(8) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    12,
                )
                device.waitForIdle()
            }
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    12,
                )
                device.waitForIdle()
            }
        }

    @Test
    fun navigation() =
        rule.measureRepeated(
            TARGET,
            listOf(FrameTimingMetric()),
            CompilationMode.None(),
            iterations = 3,
            setupBlock = {
                openRoute("Home")
                device.awaitText("PICK UP WHERE YOU LEFT OFF")
            },
        ) {
            repeat(3) {
                for (tab in listOf("Quran", "Search", "Saved", "Settings", "Today")) {
                    device.clickTab(tab)
                    device.waitForIdle()
                }
            }
        }

    @Test
    fun searchResults() =
        rule.measureRepeated(
            TARGET,
            listOf(
                FrameTimingMetric(),
                TraceSectionMetric("NOS.search", TraceSectionMetric.Mode.Sum),
            ),
            CompilationMode.None(),
            iterations = 3,
            setupBlock = { openRoute("Search") },
        ) {
            for (query in listOf("mercy", "رحمة", "2:255")) {
                device.setQuery(query)
                device.waitForIdle()
                check(device.wait(Until.hasObject(By.res("search-ready")), 5000))
            }
            device.pressBack()
        }

    @Test
    fun themeChanges() =
        rule.measureRepeated(
            TARGET,
            listOf(FrameTimingMetric()),
            CompilationMode.None(),
            iterations = 3,
            setupBlock = { openRoute("Settings") },
        ) {
            repeat(3) {
                for (theme in listOf("Light", "Dark")) {
                    device.wait(Until.findObject(By.text(theme)), 5000)!!.click()
                    device.waitForIdle()
                }
            }
        }

    @Test
    fun playerTransitions() =
        rule.measureRepeated(
            TARGET,
            listOf(FrameTimingMetric()),
            CompilationMode.None(),
            iterations = 3,
            setupBlock = {
                openRoute("player")
                check(device.wait(Until.hasObject(By.desc("Read current ayah")), 10000))
            },
        ) {
            repeat(3) {
                device.wait(Until.findObject(By.desc("Back")), 5000)!!.click()
                device.waitForIdle()
                device.wait(Until.findObject(By.res("mini-player")), 5000)!!.click()
                device.waitForIdle()
            }
            openRoute("reciters")
            for (reciter in listOf("Abdul Basit", "Husary", "Alafasy")) {
                device.wait(Until.findObject(By.text(reciter)), 5000)!!.click()
                device.waitForIdle()
            }
            device.pressHome()
        }
}

@RunWith(AndroidJUnit4::class)
class GenerateBaselineProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            TARGET,
            includeInStartupProfile = true,
            maxIterations = 3,
            stableIterations = 2,
        ) {
            openRoute("Home")
            device.awaitText("PICK UP WHERE YOU LEFT OFF")
            device.clickTab("Quran")
            device.awaitText("THE QURAN")
            device.findObject(By.text("Al-Baqara")).click()
            device.awaitText("Al-Baqara")
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 4 / 5,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    12,
                )
                device.waitForIdle()
            }
            device.pressBack()
            device.waitForIdle()
            device.clickTab("Search")
            device.setQuery("mercy")
            device.waitForIdle()
        }
}
