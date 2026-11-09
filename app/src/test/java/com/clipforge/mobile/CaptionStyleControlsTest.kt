package com.clipforge.mobile

import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
class CaptionStyleControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun controlsChangeThePreviewAndCanResetAppearance() {
        var appearance by mutableStateOf(CaptionAppearance())
        var preset by mutableStateOf(CaptionPreset.BOLD)
        var size by mutableFloatStateOf(39f)
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Caption controls · layout fixture")
                        val bitmap = remember(appearance, preset, size) {
                            makeCaptionOverlay("", "HELLO #WORLD", size, Color.WHITE, preset, appearance)
                        }
                        Image(bitmap.asImageBitmap(), "Caption appearance preview",
                            Modifier.height(280.dp).aspectRatio(9f / 16f))
                        CaptionStyleControls(preset, size, appearance, Color.WHITE, true,
                            { preset = it }, { size = it }, { appearance = it })
                    }
                }
            }
        }
        compose.onNodeWithText("Font").performClick()
        compose.onNodeWithText("Mono").performClick()
        compose.runOnIdle { assertEquals(CaptionFont.MONO, appearance.font) }
        compose.onNodeWithText("Color").performClick()
        compose.onAllNodesWithText("Pink")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Cyan")[1].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CaptionColor.PINK, appearance.color); assertEquals(CaptionColor.CYAN, appearance.highlight) }
        compose.onNodeWithText("Position").performScrollTo().performClick()
        compose.onNodeWithText("Middle").performClick()
        compose.onNodeWithText("Left").performClick()
        compose.runOnIdle { assertEquals(.55f, appearance.positionY, .001f); assertEquals(CaptionAlignment.LEFT, appearance.alignment) }
        val file = File("build/outputs/studio-previews/caption-appearance-controls.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
        compose.onNodeWithText("Reset font, color & position").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(CaptionAppearance(), appearance) }
    }
}
