package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
class RecentProjectsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun projectCardsOpenTheSelectedDraftAndDisableOpeningWhileBusy() {
        var enabled by mutableStateOf(true)
        var opened = ""
        val entries = listOf(
            ProjectCatalog.Entry("a".repeat(32), "Interview highlights", "content://fixture/1", 0, 25_000, 1_794_240_000_000),
            ProjectCatalog.Entry("b".repeat(32), "Campaign draft", "content://fixture/2", 0, 30_000, 1_794_239_000_000))
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StudioHeader("layout fixture")
                        RecentProjects(ProjectCatalog.Listing(entries), entries.first().id, enabled) { opened = it }
                        CaptionPresetPicker(CaptionPreset.HIGHLIGHT, true) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Interview highlights").assertIsDisplayed()
        compose.onNodeWithText("Campaign draft").performClick()
        compose.runOnIdle { assertEquals(entries[1].id, opened); enabled = false; opened = "" }
        compose.onNodeWithText("Interview highlights").performClick()
        compose.runOnIdle { assertEquals("", opened) }
        val file = File("build/outputs/studio-previews/projects-and-caption-styles.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
        assertTrue(file.length() > 1000)
    }
}
