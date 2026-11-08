package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
class StudioCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longHooksKeepClipRangesAndReviewStatusVisible() {
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StudioHeader("layout fixture")
                        StudioIntro(StudioTab.STUDIO)
                        Text("SAMPLE SHORTLIST · LAYOUT TEST", style = MaterialTheme.typography.bodySmall)
                        ClipReviewCard(0, HighlightDraft(30_000, 50_000, hook = "A long opening hook should wrap cleanly while the review status stays visible"), false, 65, true, onClick = {})
                        ClipReviewCard(1, HighlightDraft(3_600_000, 3_620_000), true, 70, true, onClick = {})
                        ClipReviewCard(2, HighlightDraft(7_180_000, 7_200_000, approved = true), false, 60, true, onClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Review").assertIsDisplayed()
        compose.onNodeWithText("Editing").assertIsDisplayed()
        compose.onNodeWithText("Approved").assertIsDisplayed()
        val file = File("build/outputs/studio-previews/clip-cards.png").apply { parentFile.mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
        assertTrue(file.length() > 1_000)
    }
}
