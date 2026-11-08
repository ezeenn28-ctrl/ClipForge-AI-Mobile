package com.clipforge.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
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
        waitForProjectLoad()
        compose.onNodeWithText("Choose a video").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Studio").assertIsDisplayed()
        compose.onNodeWithText("Captions").assertIsDisplayed()
        compose.onNodeWithText("Campaign").assertIsDisplayed()
        compose.onNodeWithText("Export").assertIsDisplayed()
        screenshot("studio")
    }

    @Test fun workspacesStayReachableAndExportRequiresApproval() {
        waitForProjectLoad()
        compose.onNodeWithText("Captions").performClick()
        compose.onNodeWithText("Every word, in place.").assertIsDisplayed()
        screenshot("captions")
        compose.onNodeWithText("Campaign").performClick()
        compose.onNodeWithText("Create with a clear brief.").assertIsDisplayed()
        screenshot("campaign")
        compose.onNodeWithText("Export").performClick()
        screenshot("export")
        compose.onNodeWithText("Export approved MP4").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Studio").performClick()
        compose.onNodeWithText("Choose a video").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun compactPhoneKeepsPrimaryActionAndTabsVisible() {
        waitForProjectLoad()
        compose.onNodeWithText("Choose a video").assertIsDisplayed()
        compose.onNodeWithText("Export").assertIsDisplayed()
        screenshot("studio-compact")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-mdpi")
    fun landscapeKeepsSourceToolsAndNavigationReachable() {
        waitForProjectLoad()
        compose.onNodeWithText("Your workflow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose a video").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export").assertIsDisplayed()
        screenshot("studio-landscape")
    }

    @Test fun switchingWorkspacesPreservesUnsavedCampaignText() {
        waitForProjectLoad()
        compose.onNodeWithText("Campaign").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithText("Add submission").assertIsEnabled(); true }.getOrDefault(false)
        }
        compose.onNodeWithText("Add submission").performScrollTo().performClick()
        compose.onNodeWithText("Campaign name").performScrollTo().performTextInput("My campaign")
        compose.onNodeWithText("Export").performClick()
        compose.onNodeWithText("Campaign").performClick()
        compose.onNodeWithText("Campaign name").performScrollTo().assertTextContains("My campaign")
    }

    private fun waitForProjectLoad() {
        compose.waitUntil(timeoutMillis = 15_000) {
            runCatching { compose.onNodeWithText("Choose a video").assertIsEnabled(); true }.getOrDefault(false)
        }
    }

    private fun screenshot(name: String) {
        val directory = File("build/outputs/studio-previews").apply { mkdirs() }
        val output = File(directory, "$name.png")
        compose.onRoot().captureRoboImage(output.absolutePath)
        assertTrue("Rendered preview was not written", output.isFile && output.length() > 1_000)
    }
}
