package com.clipforge.mobile

import java.io.File
import java.io.OutputStreamWriter
import java.util.Properties

/** Atomic, UTF-8 local snapshot. Does not load models, start jobs, upload or export. */
object ProjectStore {
    @Synchronized fun write(file: File, project: EditorProject) {
        val safe = project.reviewedAgain()
        safe.validate()
        val data = Properties()
        fun put(key: String, value: Any) { data.setProperty(key, value.toString()) }
        fun segments(prefix: String, list: List<WhisperNative.Segment>) {
            put("$prefix.count", list.size)
            list.forEachIndexed { index, segment ->
                put("$prefix.$index.start", segment.startMs)
                put("$prefix.$index.end", segment.endMs)
                put("$prefix.$index.text", segment.text)
            }
        }
        put("format", "clipforge-project-v1")
        put("fillFrame", safe.fillFrame)
        put("source", safe.source); put("duration", safe.durationMs)
        put("start", safe.startMs); put("end", safe.endMs)
        put("mode", safe.mode); put("vertical", safe.vertical); put("mute", safe.mute)
        put("selected", safe.selectedDraft); put("hook", safe.hook); put("caption", safe.caption)
        put("effect", safe.effect); put("subtitleSize", safe.subtitleSize)
        put("subtitleYellow", safe.subtitleYellow); put("subtitleDraft", safe.subtitleDraftIndex)
        put("drafts.count", safe.drafts.size)
        safe.drafts.forEachIndexed { i, draft ->
            put("drafts.$i.start", draft.startMs); put("drafts.$i.end", draft.endMs)
            put("drafts.$i.visual", draft.visualScore ?: -1)
            put("drafts.$i.hook", draft.hook); put("drafts.$i.caption", draft.caption)
            put("drafts.$i.effect", draft.effect)
            put("drafts.$i.captionStart", draft.captionStartMs); put("drafts.$i.captionEnd", draft.captionEndMs)
            segments("drafts.$i.segments", safe.draftSegments[i].orEmpty())
        }
        segments("current", safe.currentSegments)
        segments("sourceSpeech", safe.sourceSegments)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".partial")
        try {
            temporary.outputStream().use { stream ->
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                data.store(writer, "ClipForge local project")
                writer.flush()
                require(temporary.length() <= 20L * 1024 * 1024) { "Project exceeds 20 MB limit" }
                stream.fd.sync()
            }
            check(temporary.renameTo(file)) { "Could not save local project" }
        } finally { temporary.delete() }
    }

    @Synchronized fun read(file: File): EditorProject? {
        if (!file.exists()) return null
        require(file.length() <= 20L * 1024 * 1024) { "Project exceeds 20 MB limit" }
        val data = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
        fun text(key: String): String = data.getProperty(key) ?: error("Missing project field: $key")
        fun number(key: String): Long = text(key).toLongOrNull() ?: error("Invalid project number: $key")
        fun integer(key: String): Int = text(key).toIntOrNull() ?: error("Invalid project index: $key")
        fun bool(key: String): Boolean = text(key).toBooleanStrictOrNull() ?: error("Invalid project flag: $key")
        fun segments(prefix: String): List<WhisperNative.Segment> {
            val count = integer("$prefix.count")
            require(count in 0..20_000) { "Invalid subtitle count" }
            return (0 until count).map { i -> WhisperNative.Segment(number("$prefix.$i.start"), number("$prefix.$i.end"), text("$prefix.$i.text")) }
        }
        require(text("format") == "clipforge-project-v1") { "Unsupported project version" }
        val count = integer("drafts.count")
        require(count in 0..15) { "Invalid draft count" }
        val drafts = (0 until count).map { i ->
            HighlightDraft(number("drafts.$i.start"), number("drafts.$i.end"), approved = false,
                visualScore = integer("drafts.$i.visual").takeIf { it >= 0 }, hook = text("drafts.$i.hook"),
                caption = text("drafts.$i.caption"), effect = text("drafts.$i.effect"),
                captionStartMs = number("drafts.$i.captionStart"), captionEndMs = number("drafts.$i.captionEnd"))
        }
        val draftSegments = (0 until count).associateWith { segments("drafts.$it.segments") }.filterValues { it.isNotEmpty() }
        return EditorProject(text("source"), number("duration"), number("start"), number("end"),
            text("mode"), bool("vertical"), bool("mute"), drafts, integer("selected"), text("hook"), text("caption"),
            text("effect"), text("subtitleSize").toFloat(), bool("subtitleYellow"), integer("subtitleDraft"),
            segments("current"), segments("sourceSpeech"), draftSegments,
            fillFrame = data.getProperty("fillFrame", "false").toBooleanStrict()).also { it.validate() }
    }
}
