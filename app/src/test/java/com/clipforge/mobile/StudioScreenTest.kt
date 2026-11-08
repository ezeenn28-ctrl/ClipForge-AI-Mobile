package com.clipforge.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
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
class StudioScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyStudioKeepsImportAndNavigationVisible() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Choose a video").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose a video").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Studio").assertIsDisplayed()
        compose.onNodeWithText("Captions").assertIsDisplayed()
        compose.onNodeWithText("Campaign").assertIsDisplayed()
        compose.onNodeWithText("Export").assertIsDisplayed()
        screenshot("studio")
    }

    @Test fun workspacesStayReachableAndExportRequiresApproval() {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Choose a video").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Captions").performClick()
        compose.onNodeWithText("Every word, in place.").assertIsDisplayed()
        screenshot("captions")
        compose.onNodeWithText("Campaign").performClick()
        compose.onNodeWithText("Create with a clear brief.").assertIsDisplayed()
        screenshot("campaign")
        compose.onNodeWithText("Export").performClick()
        compose.onNodeWithText("Export approved MP4").performScrollTo().assertIsNotEnabled()
        screenshot("export")
        compose.onNodeWithText("Studio").performClick()
        compose.onNodeWithText("Choose a video").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val directory = File("build/outputs/studio-previews").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(directory, "$name.png").absolutePath)
    }
}
