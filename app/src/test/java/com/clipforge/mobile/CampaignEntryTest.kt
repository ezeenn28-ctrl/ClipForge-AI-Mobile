package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

class CampaignEntryTest {
    private fun entry() = CampaignEntry("id1", "Campaign", "TikTok", "https://example.com/post", SubmissionStatus.APPROVED,
        3000, BigDecimal("1.00"), BigDecimal("3.00"), BigDecimal("150.00"))

    @Test fun exactThresholdQualifiesAndEstimateIsCapped() {
        assertEquals(BigDecimal("3.00"), entry().qualifiedEstimate())
        assertEquals(BigDecimal("0.00"), entry().copy(views = 2999).qualifiedEstimate())
        assertEquals(BigDecimal("150.00"), entry().copy(views = 200_000).qualifiedEstimate())
    }

    @Test fun pendingAndRejectedAreNeverCountedAsApprovedEstimates() {
        assertEquals(BigDecimal("0.00"), entry().copy(status = SubmissionStatus.PENDING).qualifiedEstimate())
        assertEquals(BigDecimal("0.00"), entry().copy(status = SubmissionStatus.REJECTED).qualifiedEstimate())
        assertEquals(BigDecimal.ZERO, entry().paid)
    }

    @Test fun requiredGeographyMustBeKnownAndMeetThreshold() {
        val rules = entry().copy(minimumTierOnePercent = 40)
        assertTrue(rules.estimateIssues().contains("Audience geography has not been entered"))
        assertEquals(BigDecimal("0.00"), rules.copy(actualTierOnePercent = 39).qualifiedEstimate())
        assertEquals(BigDecimal("3.00"), rules.copy(actualTierOnePercent = 40).qualifiedEstimate())
    }

    @Test fun centsAreNotRoundedUpAcrossThreshold() {
        val value = entry().copy(views = 1999, ratePerThousand = BigDecimal("1.5"), minimumPayout = BigDecimal("3"))
        assertEquals(BigDecimal("2.99"), value.estimatedAmount())
        assertEquals(BigDecimal("0.00"), value.qualifiedEstimate())
    }

    @Test fun trackerRestoresRejectionDetailsAndActualPaidSeparately() {
        val directory = Files.createTempDirectory("clipforge-tracker").toFile()
        try {
            val file = File(directory, "tracker.properties")
            val rejected = entry().copy(status = SubmissionStatus.REJECTED, rejectionReason = "Minimum payout\nKulang pa", paid = BigDecimal("1.25"))
            CampaignEntryStore.write(file, listOf(rejected))
            assertEquals(listOf(rejected), CampaignEntryStore.read(file))
            try { CampaignEntryStore.write(file, listOf(rejected, rejected)); fail("Duplicate IDs accepted") }
            catch (_: IllegalArgumentException) {}
            assertEquals(listOf(rejected), CampaignEntryStore.read(file))
        } finally { directory.deleteRecursively() }
    }
}
