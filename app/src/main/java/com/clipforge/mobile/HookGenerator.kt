package com.clipforge.mobile

import java.util.Locale

enum class HookTone(val label: String) { FUNNY("Funny"), CURIOSITY("Curiosity"), STORY("Story") }

/** Conservative local ideas from supplied words, not visual recognition or a cloud LLM. */
object HookGenerator {
    fun suggestions(source: String, tone: HookTone = HookTone.FUNNY): List<String> {
        val clean = source.take(6000).replace(Regex("[\\p{Cntrl}#]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (clean.count { it.isLetterOrDigit() } < 6) return emptyList()
        val lower = clean.lowercase(Locale.ROOT)
        val filipino = Regex("\\b(ang|yung|hindi|bakit|grabe|naman|talaga|ako|siya|sila)\\b")
            .findAll(lower).count() >= 2
        val phrases = clean.split(Regex("(?<=[.!?])\\s+|\\n")).filter { it.isNotBlank() }
        val quote = (phrases.firstOrNull { it.length in 8..42 &&
            Regex("\\b(what|why|really|wait|traitor|sword|birthday|grabe|bakit|hala)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: phrases.firstOrNull() ?: clean).trim().trimEnd('.', '!', '?')
        val short = shorten(quote, 38)
        val specific = when {
            "sword" in lower && ("court" in lower || "judge" in lower) -> "A sword… in COURT?! 😭"
            "birthday" in lower && "anniversary" in lower -> "The birthday mix-up 😭"
            "traitor" in lower -> "That “traitor” comment 😭"
            else -> null
        }
        val ideas = if (filipino) when (tone) {
            HookTone.FUNNY -> listOf("“" + short + "”… teka lang 😭", "Ang “" + short + "” moment 😂", "Panoorin ang kasunod nito 👀")
            HookTone.CURIOSITY -> listOf("“" + short + "”… bakit kaya? 👀", "Ano ang ibig niyang sabihin dito?", "Pakinggan muna ang buong usapan 👀")
            HookTone.STORY -> listOf("Dito nagsimula ang usapan 👀", "“" + short + "” — ito ang context", "Ganito napunta ang usapan dito")
        } else when (tone) {
            HookTone.FUNNY -> listOfNotNull(specific, "“" + short + "”… wait 😭", "The “" + short + "” moment 😂", "Listen to this exchange 👀")
            HookTone.CURIOSITY -> listOf("“" + short + "”… what does that mean? 👀", "Listen to how this conversation unfolds 👀", "Here's the context behind “" + short + "”")
            HookTone.STORY -> listOf("This is where the conversation started 👀", "“" + short + "” — here's the context", "How this conversation got here")
        }
        return ideas.map { shorten(it, 65) }.distinct().take(3)
    }

    private fun shorten(value: String, limit: Int): String {
        if (value.length <= limit) return value
        var result = value.take(limit - 1).trimEnd()
        if (result.lastOrNull()?.isHighSurrogate() == true) result = result.dropLast(1)
        val space = result.lastIndexOf(' ')
        if (space > limit / 2) result = result.take(space)
        return result.trimEnd('.', ',', '…') + "…"
    }
}
