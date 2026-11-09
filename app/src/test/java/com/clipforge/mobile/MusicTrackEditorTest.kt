package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicTrackEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editsMusicTimingAndRetainsOriginalAudioControls() {
        var track by mutableStateOf<MusicTrack?>(MusicTrack("content://audio/fixture", "Local music · layout fixture", 60_000))
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MusicTrackEditor(track, 30_000, true, {}, { track = it }, { throw AssertionError(it) })
                    }
                }
            }
        }
        compose.onNodeWithText("Music in").performTextReplacement("00:05.000")
        compose.onNodeWithText("Start on timeline").performTextReplacement("00:02.000")
        compose.onNodeWithText("Apply music timing").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5000L, track!!.startMs); assertEquals(2000L, track!!.timelineStartMs) }
        compose.onNodeWithText("Music fade in").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0L, track!!.fadeInMs) }
        compose.onNodeWithText("Replace or relink music").performScrollTo()
        val file = File("build/outputs/studio-previews/music-layout-fixture.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
        compose.onNodeWithText("Remove music").performScrollTo().performClick()
        compose.runOnIdle { assertNull(track) }
    }
}
