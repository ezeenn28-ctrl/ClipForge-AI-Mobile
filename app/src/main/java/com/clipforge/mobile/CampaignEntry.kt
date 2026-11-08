package com.clipforge.mobile

import java.math.BigDecimal
import java.math.RoundingMode

enum class SubmissionStatus { PENDING, APPROVED, REJECTED }

/** Manual campaign bookkeeping, never a platform payout guarantee. Amounts are USD. */
data class CampaignEntry(
    val id: String,
    val campaign: String,
    val platform: String,
    val postUrl: String,
    val status: SubmissionStatus,
    val views: Long,
    val ratePerThousand: BigDecimal,
    val minimumPayout: BigDecimal,
    val maximumPayout: BigDecimal,
    val paid: BigDecimal = BigDecimal.ZERO,
    val minimumTierOnePercent: Int = 0,
    val actualTierOnePercent: Int? = null,
    val rejectionReason: String = ""
) {
    fun validate() {
        require(id.isNotBlank() && campaign.isNotBlank()) { "Enter a campaign name" }
        require(platform in listOf("TikTok", "Instagram", "YouTube", "X")) { "Select a platform" }
        require(postUrl.isBlank() || postUrl.startsWith("https://")) { "Use an HTTPS post URL" }
        require(views >= 0 && views <= 1_000_000_000_000) { "Enter a valid view count" }
        listOf(ratePerThousand, minimumPayout, maximumPayout, paid).forEach {
            require(it.signum() >= 0 && it <= BigDecimal("1000000000") && it.scale() <= 6) { "Enter nonnegative USD amounts with at most six decimal places" }
        }
        require(maximumPayout >= minimumPayout) { "Maximum payout must be at least the minimum" }
        require(minimumTierOnePercent in 0..100 && (actualTierOnePercent == null || actualTierOnePercent in 0..100)) { "Audience percentages must be 0–100" }
    }

    fun estimatedAmount(): BigDecimal = BigDecimal.valueOf(views).multiply(ratePerThousand)
        .divide(BigDecimal(1000)).min(maximumPayout).setScale(2, RoundingMode.DOWN)

    fun estimateIssues(): List<String> = buildList {
        if (status != SubmissionStatus.APPROVED) add("Submission is ${status.name.lowercase()}")
        if (ratePerThousand.signum() == 0) add("CPM is zero")
        if (estimatedAmount() < minimumPayout) add("Below minimum payout")
        if (minimumTierOnePercent > 0) {
            if (actualTierOnePercent == null) add("Audience geography has not been entered")
            else if (actualTierOnePercent < minimumTierOnePercent) add("Below required Tier 1 audience percentage")
        }
    }

    fun qualifiedEstimate(): BigDecimal = if (estimateIssues().isEmpty()) estimatedAmount() else BigDecimal.ZERO.setScale(2)
}
