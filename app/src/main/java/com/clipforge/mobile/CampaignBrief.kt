package com.clipforge.mobile

/** Project-local entered rules. Human confirmations and export authorization are never stored. */
data class CampaignBrief(val enabled: Boolean = false, val tags: String = "", val postingCaption: String = "",
                         val minimum: String = "15", val maximum: String = "30", val sourceSeconds: String = "0") {
    fun validate() {
        require(tags.length <= 2000 && postingCaption.length <= 10_000) { "Campaign text is too long" }
        require(listOf(minimum, maximum, sourceSeconds).all { it.length <= 4 && it.all(Char::isDigit) }) { "Invalid campaign duration entry" }
    }
}
