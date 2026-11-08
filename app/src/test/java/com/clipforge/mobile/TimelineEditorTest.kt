package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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
class TimelineEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun splitAndUndoUseTheRealSequence() {
        var history by mutableStateOf(TimelineHistory(listOf(TimelineClip("a", 30_000, 50_000), TimelineClip("b", 70_000, 90_000))))
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TimelineEditor(LocalContext.current, null, 120_000, history, "a", 10_000, true,
                            { history = it }, {}, {}, {}, { error -> throw AssertionError(error) })
                    }
                }
            }
        }
        compose.onNodeWithText("Split", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3, history.clips.size); assertEquals(40_000L, history.clips[0].endMs) }
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, history.clips.size) }
        val file = File("build/outputs/studio-previews/timeline-layout-fixture.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
}
