package com.clipforge.mobile

import java.io.File
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.util.Properties

object CampaignEntryStore {
    @Synchronized fun write(file: File, entries: List<CampaignEntry>) {
        require(entries.size <= 1000) { "Tracker limit is 1,000 submissions" }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate submission IDs" }
        entries.forEach { it.validate() }
        val data = Properties()
        data.setProperty("format", "clipforge-campaigns-v1")
        data.setProperty("count", entries.size.toString())
        entries.forEachIndexed { i, entry ->
            fun put(key: String, value: Any) { data.setProperty("$i.$key", value.toString()) }
            put("id", entry.id); put("campaign", entry.campaign); put("platform", entry.platform)
            put("url", entry.postUrl); put("status", entry.status.name); put("views", entry.views)
            put("cpm", entry.ratePerThousand.toPlainString()); put("min", entry.minimumPayout.toPlainString())
            put("max", entry.maximumPayout.toPlainString()); put("paid", entry.paid.toPlainString())
            put("tierMin", entry.minimumTierOnePercent); put("tierActual", entry.actualTierOnePercent ?: "")
            put("reason", entry.rejectionReason)
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".partial")
        try {
            temporary.outputStream().use { stream ->
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                data.store(writer, "ClipForge manual campaign tracker")
                writer.flush()
                require(temporary.length() <= 5L * 1024 * 1024) { "Tracker exceeds 5 MB" }
                stream.fd.sync()
            }
            check(temporary.renameTo(file)) { "Could not save submission tracker" }
        } finally { temporary.delete() }
    }

    @Synchronized fun read(file: File): List<CampaignEntry> {
        if (!file.exists()) return emptyList()
        require(file.length() <= 5L * 1024 * 1024) { "Tracker exceeds 5 MB" }
        val data = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
        require(data.getProperty("format") == "clipforge-campaigns-v1") { "Unsupported tracker format" }
        val count = data.getProperty("count")?.toIntOrNull() ?: error("Invalid tracker count")
        require(count in 0..1000) { "Invalid tracker count" }
        val entries = (0 until count).map { i ->
            fun text(key: String): String = data.getProperty("$i.$key") ?: error("Missing submission field: $key")
            CampaignEntry(text("id"), text("campaign"), text("platform"), text("url"),
                SubmissionStatus.valueOf(text("status")), text("views").toLong(), BigDecimal(text("cpm")),
                BigDecimal(text("min")), BigDecimal(text("max")), BigDecimal(text("paid")),
                text("tierMin").toInt(), text("tierActual").takeIf { it.isNotBlank() }?.toInt(), text("reason"))
                .also { it.validate() }
        }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate submission IDs" }
        return entries
    }
}
