package com.clipforge.mobile

import java.io.File
import java.io.OutputStreamWriter
import java.util.Properties

object SpeechJobStore {
    @Synchronized fun write(file: File, job: SpeechJob) {
        job.validate()
        val data = Properties()
        fun put(key: String, value: Any) { data.setProperty(key, value.toString()) }
        put("format", "clipforge-speech-v1"); put("id", job.id); put("source", job.source)
        put("duration", job.durationMs); put("mode", job.mode.name); put("target", job.targetId)
        put("status", job.status.name); put("elapsed", job.elapsedMs); put("message", job.message)
        put("range.count", job.ranges.size)
        job.ranges.forEachIndexed { i, range -> put("range.$i.index", range.index); put("range.$i.start", range.startMs); put("range.$i.end", range.endMs) }
        put("result.count", job.results.size)
        job.results.entries.forEachIndexed { i, (key, segments) ->
            put("result.$i.key", key); put("result.$i.count", segments.size)
            segments.forEachIndexed { j, segment ->
                put("result.$i.$j.start", segment.startMs); put("result.$i.$j.end", segment.endMs); put("result.$i.$j.text", segment.text)
            }
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".partial")
        try {
            temp.outputStream().use { stream ->
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                data.store(writer, "Local speech checkpoint"); writer.flush()
                require(temp.length() <= 20L * 1024 * 1024) { "Speech checkpoint exceeds 20 MB" }
                stream.fd.sync()
            }
            check(temp.renameTo(file)) { "Could not save speech progress" }
        } finally { temp.delete() }
    }

    @Synchronized fun read(file: File): SpeechJob? {
        if (!file.exists()) return null
        require(file.length() <= 20L * 1024 * 1024) { "Speech checkpoint exceeds 20 MB" }
        val data = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
        fun text(key: String) = data.getProperty(key) ?: error("Missing speech field: $key")
        fun count(key: String, max: Int): Int = text(key).toInt().also { require(it in 0..max) }
        require(text("format") == "clipforge-speech-v1") { "Unknown speech checkpoint" }
        val ranges = (0 until count("range.count", 60)).map { i -> SpeechRange(text("range.$i.index").toInt(), text("range.$i.start").toLong(), text("range.$i.end").toLong()) }
        val results = (0 until count("result.count", 2880)).associate { i ->
            text("result.$i.key") to (0 until count("result.$i.count", 20_000)).map { j ->
                WhisperNative.Segment(text("result.$i.$j.start").toLong(), text("result.$i.$j.end").toLong(), text("result.$i.$j.text"))
            }
        }
        return SpeechJob(text("id"), text("source"), text("duration").toLong(), SpeechJobMode.valueOf(text("mode")), ranges,
            text("target"), SpeechJobStatus.valueOf(text("status")), results, text("elapsed").toLong(), text("message")).also { it.validate() }
    }
}
