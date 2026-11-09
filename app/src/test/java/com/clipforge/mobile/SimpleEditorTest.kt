package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
class SimpleEditorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun toolbarShowsOnlySelectedClipToolsAndExportRequestsReview() {
        var tool by mutableStateOf(EditorTool.EDIT)
        var history by mutableStateOf(TimelineHistory(listOf(TimelineClip("a", 0, 20_000))))
        var reviewRequested = false
        compose.setContent {
            ClipForgeTheme {
                Scaffold(topBar = { EditorTopBar(true, history.past.isNotEmpty(), history.future.isNotEmpty(), {},
                    { history = history.undo() }, { history = history.redo() }, { reviewRequested = true }) },
                    bottomBar = { EditorToolBar(tool, true) { tool = it } }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = androidx.compose.ui.unit.Dp(12f))) {
                        Surface(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(200f))) { Box { Text("Video preview · layout fixture") } }
                        TimelineEditor(LocalContext.current, null, 60_000, history, "a", 10_000, true,
                            { history = it }, {}, {}, {}, { throw AssertionError(it) }, compact = true, tool = tool)
                    }
                }
            }
        }
        compose.onNodeWithText("Clip hook").assertDoesNotExist()
        compose.onNodeWithText("Volume · 100%").assertDoesNotExist()
        compose.onNodeWithText("Split").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, history.clips.size) }
        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(1, history.clips.size) }
        val file = File("build/outputs/studio-previews/simple-editor-fixture.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("Split").assertDoesNotExist()
        compose.onNodeWithText("Volume · 100%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Text").performClick()
        compose.onNodeWithText("Clip hook").performScrollTo().performTextInput("Opening hook")
        compose.runOnIdle { assertEquals("Opening hook", history.clips.first().hook) }
        compose.onNodeWithText("Export").performClick()
        compose.runOnIdle { assertTrue(reviewRequested) }
    }
}
