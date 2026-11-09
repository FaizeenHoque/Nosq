package dev.faizeenhoque.nosquran

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @Test
    fun appControllerConnectsAndCanManageQueueWithoutPlayingAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var future: ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            future =
                MediaController.Builder(
                        context,
                        SessionToken(
                            context,
                            ComponentName(context, QuranPlaybackService::class.java),
                        ),
                    )
                    .buildAsync()
        }
        val controller = future.get(10, TimeUnit.SECONDS)
        val queueUpdated = CountDownLatch(1)
        val listener =
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItem?.mediaId == "session-test") queueUpdated.countDown()
                }
            }
        try {
            instrumentation.runOnMainSync {
                assertTrue(controller.isConnected)
                controller.addListener(listener)
                // A URI is required by MediaSession's default item resolver.
                // The item is never prepared or played; no resource is opened.
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("session-test")
                        .setUri("file:///android_asset/session-test-not-played.mp3")
                        .build()
                )
            }
            assertTrue(
                "Session should accept the playback queue",
                queueUpdated.await(5, TimeUnit.SECONDS),
            )
            instrumentation.runOnMainSync {
                assertEquals("session-test", controller.currentMediaItem?.mediaId)
                assertFalse(controller.isPlaying)
            }
        } finally {
            instrumentation.runOnMainSync {
                controller.removeListener(listener)
                controller.clearMediaItems()
                controller.release()
            }
        }
    }
}
