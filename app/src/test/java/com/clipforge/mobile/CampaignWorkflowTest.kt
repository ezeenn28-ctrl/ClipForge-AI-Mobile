package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CampaignWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fiftyFourRecordsCanBeReviewedEditedAndRestoredWithoutInventingPaidAmounts() {
        val root = Files.createTempDirectory("campaign-ui").toFile()
        val restored = Files.createTempDirectory("campaign-restore").toFile()
        try {
            val file = File(root, "campaigns/submissions.properties")
            val entries = (0 until 54).map { i -> CampaignEntry("record-$i", "Synthetic campaign", "TikTok", "",
                if (i < 4) SubmissionStatus.APPROVED else if (i < 46) SubmissionStatus.PENDING else SubmissionStatus.REJECTED,
                100, BigDecimal("1"), BigDecimal("3"), BigDecimal("150"), rejectionReason = if (i >= 46) "Below payout threshold" else "") }
            CampaignEntryStore.write(file, entries)
            compose.setContent { ClipForgeTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) { CampaignTracker(root, true) } } } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Page 1/6").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("42").assertExists()
            compose.onNodeWithText("4").assertExists()
            compose.onNodeWithText("8").assertExists()
            assertEquals(BigDecimal("0.00"), entries.fold(BigDecimal("0.00")) { sum, entry -> sum + entry.qualifiedEstimate() })
            compose.onAllNodesWithText("Edit submission").onFirst().performScrollTo().performClick()
            compose.onNodeWithText("Platform-reported views").performScrollTo().performTextReplacement("5000")
            compose.onAllNodes(hasText("Approved") and hasClickAction()).onFirst().performScrollTo().performClick()
            compose.onNodeWithText("Facebook").performScrollTo().performClick()
            compose.onNodeWithText("Update submission").performScrollTo().performClick()
            compose.waitUntil(5000) {
                CampaignEntryStore.read(file).first { it.id == "record-53" }.let { it.status == SubmissionStatus.APPROVED && it.views == 5000L }
            }
            val changed = CampaignEntryStore.read(file)
            val updated = changed.first { it.id == "record-53" }
            assertEquals("Facebook", updated.platform)
            assertEquals(BigDecimal("5.00"), updated.qualifiedEstimate())
            assertEquals(BigDecimal.ZERO, updated.paid)
            assertEquals(54, changed.size)
            val bytes = java.io.ByteArrayOutputStream().also { MetadataBackup.create(root, it) }.toByteArray()
            MetadataBackup.restore(restored, MetadataBackup.stage(restored, bytes.inputStream()))
            assertEquals(changed, CampaignEntryStore.read(File(restored, "campaigns/submissions.properties")))
        } finally { root.deleteRecursively(); restored.deleteRecursively() }
    }
    @Test fun unreadableTrackerDoesNotExposeAnOverwriteAction() {
        val root = Files.createTempDirectory("campaign-corrupt").toFile()
        try {
            val file = File(root, "campaigns/submissions.properties").apply { parentFile.mkdirs(); writeText("corrupt tracker") }
            compose.setContent { ClipForgeTheme { Column { CampaignTracker(root, true) } } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Could not read tracker.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Add submission").assertDoesNotExist()
            assertEquals("corrupt tracker", file.readText())
        } finally { root.deleteRecursively() }
    }
}
