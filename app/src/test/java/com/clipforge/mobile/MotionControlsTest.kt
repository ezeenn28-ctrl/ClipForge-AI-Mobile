package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
class MotionControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun endpointSelectionFadesAndCaptionPresetsAreEditable() {
        var clip by mutableStateOf(TimelineClip("a", 0, 10_000))
        var endSelected = false
        var preset by mutableStateOf(CaptionPreset.BOLD)
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.testTag("motion-scroll").verticalScroll(rememberScrollState())) {
                        CaptionPresetPicker(preset, true) { preset = it }
                        MotionControls(clip, true, { clip = it }, { endSelected = it })
                    }
                }
            }
        }
        compose.onNodeWithText("Minimal").performClick()
        compose.runOnIdle { assertEquals(CaptionPreset.CLEAN, preset) }
        compose.onNodeWithText("End frame").performClick()
        compose.runOnIdle { assertTrue(endSelected) }
        // The expanded preset grid makes this control fall below the viewport.
        // Scroll the vertical parent before targeting the nested horizontal row.
        compose.onNodeWithTag("motion-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2000f) }
        compose.waitForIdle()
        compose.onNodeWithText("Fade to black").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(400L, clip.visuals.fadeOutMs) }
        compose.onNodeWithText("Start frame").performScrollTo()
        val file = File("build/outputs/studio-previews/motion-controls-fixture.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
}
