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
    @Test fun trimDragCommitsOnceAndCanBeUndone() {
        val original = TimelineClip("a", 30_000, 50_000)
        var history by mutableStateOf(TimelineHistory(listOf(original)))
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TimelineEditor(LocalContext.current, null, 120_000, history, "a", 0, true,
                            { history = it }, {}, {}, {}, { error -> throw AssertionError(error) })
                    }
                }
            }
        }
        compose.onNodeWithTag("trim-end-handle").performScrollTo().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-25f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-25f, 0f))
            up()
        }
        compose.runOnIdle {
            org.junit.Assert.assertTrue(history.clips.single().endMs < original.endMs)
            assertEquals(1, history.past.size)
            assertEquals(original.startMs, history.clips.single().startMs)
        }
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(original), history.clips) }
    }


    @Test fun emptyCompactTimelineOffersAnAddSourceAction() {
        var additions = 0
        compose.setContent {
            ClipForgeTheme {
                TimelineEditor(androidx.compose.ui.platform.LocalContext.current, null, 30_000,
                    TimelineHistory(), "", 0, true, {}, {}, {}, { additions++ }, {}, compact = true)
            }
        }
        compose.onNodeWithText("Add source clip").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, additions) }
    }

    @Test fun compactSpeedPresetChangesDurationAndUndoRestoresIt() {
        var history by mutableStateOf(TimelineHistory(listOf(TimelineClip("a", 1000, 5000))))
        compose.setContent {
            ClipForgeTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
                EditorTopBar(true, history.past.isNotEmpty(), history.future.isNotEmpty(), {}, { history = history.undo() }, { history = history.redo() }, {})
                TimelineEditor(LocalContext.current, null, 8000, history, "a", 0, true,
                    { history = it }, {}, {}, {}, { error -> throw AssertionError(error) }, compact = true, tool = EditorTool.SPEED)
            } } }
        }
        compose.onNodeWithText("2.0x").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2000L, history.clips.single().durationMs); assertEquals(1, history.past.size) }
        compose.onNodeWithContentDescription("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(4000L, history.clips.single().durationMs) }
    }

    @Test fun colorDragCommitsOnceAndResetRestoresNeutral() {
        var color by mutableStateOf(ColorAdjustments())
        var changes = 0
        compose.setContent {
            ClipForgeTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
                ColorControls(color, true) { color = it; changes++ }
            } } }
        }
        compose.onNodeWithTag("color-Brightness").performScrollTo().performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(25f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(25f, 0f)); up()
        }
        compose.runOnIdle { org.junit.Assert.assertTrue(color.brightness > 0); assertEquals(1, changes) }
        compose.onNodeWithText("Reset color").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ColorAdjustments(), color) }
        compose.onRoot().captureRoboImage(File("build/outputs/studio-previews/color-controls.png").apply { parentFile.mkdirs() }.absolutePath)
    }
}
