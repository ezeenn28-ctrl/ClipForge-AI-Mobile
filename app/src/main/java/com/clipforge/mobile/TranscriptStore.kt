package com.clipforge.mobile

import java.io.File

/** One atomic snapshot per completed chunk. An interrupted write cannot advance resume. */
object TranscriptStore {
    data class Snapshot(val throughMs: Long, val segments: List<WhisperNative.Segment>)

    fun read(file: File): Snapshot {
        if (!file.isFile) return Snapshot(0, emptyList())
        val lines = file.readLines()
        val through = lines.firstOrNull()?.removePrefix("clipforge-transcript-v1\t")?.toLongOrNull()
            ?: return Snapshot(0, emptyList())
        val segments = lines.drop(1).mapNotNull { line ->
            val fields = line.split('\t', limit = 3)
            if (fields.size != 3) return@mapNotNull null
            val start = fields[0].toLongOrNull() ?: return@mapNotNull null
            val end = fields[1].toLongOrNull() ?: return@mapNotNull null
            if (start < 0 || end <= start || end > through) return@mapNotNull null
            WhisperNative.Segment(start, end, fields[2])
        }
        return Snapshot(through.coerceAtLeast(0), segments)
    }

    fun write(file: File, snapshot: Snapshot) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".partial")
        temporary.outputStream().use { stream ->
            val text = buildString {
                append("clipforge-transcript-v1\t${snapshot.throughMs}\n")
                snapshot.segments.forEach { segment ->
                    append("${segment.startMs}\t${segment.endMs}\t")
                    append(segment.text.replace(Regex("[\t\r\n]+"), " "))
                    append('\n')
                }
            }
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Unable to save transcript checkpoint" }
    }
}
