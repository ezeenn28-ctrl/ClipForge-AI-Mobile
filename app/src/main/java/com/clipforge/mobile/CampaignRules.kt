package com.clipforge.mobile

/** User-entered campaign rules. No fabricated platform metrics or payout promises. */
data class CampaignRules(
    val requiredTag: String = "",
    val minimumSeconds: Long = 15,
    val maximumSeconds: Long = 30,
    val minimumSourceSeconds: Long = 0
) {
    fun issues(caption: String, clipMs: Long, sourceVerified: Boolean, rightsVerified: Boolean): List<String> = buildList {
        if (minimumSeconds < 0 || maximumSeconds < minimumSeconds || minimumSourceSeconds < 0)
            add("Correct the campaign duration rules")
        if (clipMs < minimumSeconds * 1000 || clipMs > maximumSeconds * 1000)
            add("Clip must be $minimumSeconds–$maximumSeconds seconds")
        val tokens = caption.lowercase().split(Regex("\\s+"))
        requiredTag.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach {
            if (it.lowercase() !in tokens) add("Include $it in the posting caption")
        }
        if (clipMs < minimumSourceSeconds * 1000) add("Clip is shorter than required source footage")
        if (!sourceVerified) add("Verify the permitted episode/source and required footage")
        if (!rightsVerified) add("Verify campaign rights and duplicate-submission restrictions")
    }
}
