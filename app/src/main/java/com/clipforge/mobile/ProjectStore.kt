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
        put("project.id", safe.projectId); put("project.name", safe.projectName)
        put("fillFrame", safe.fillFrame)
        put("speechJob", safe.lastSpeechJobId); put("speechUnits", safe.lastSpeechUnits)
        put("source", safe.source); put("duration", safe.durationMs)
        put("start", safe.startMs); put("end", safe.endMs)
        put("mode", safe.mode); put("vertical", safe.vertical); put("mute", safe.mute)
        put("selected", safe.selectedDraft); put("hook", safe.hook); put("caption", safe.caption)
        put("effect", safe.effect); put("subtitleSize", safe.subtitleSize)
        put("captionPreset", safe.captionPreset)
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
        put("timeline.mode", safe.timelineMode); put("timeline.selected", safe.selectedTimelineId)
        put("timeline.count", safe.timelineClips.size)
        put("timeline.current", safe.currentTimelineId)
        put("music.present", safe.music != null)
        safe.music?.let { music ->
            put("music.source", music.source); put("music.name", music.name); put("music.duration", music.durationMs)
            put("music.start", music.startMs); put("music.end", music.endMs); put("music.offset", music.timelineStartMs)
            put("music.volume", music.volume); put("music.fadeIn", music.fadeInMs); put("music.fadeOut", music.fadeOutMs)
        }
        safe.timelineClips.forEachIndexed { i, clip ->
            val v = clip.visuals
            put("timeline.$i.visual.startScale", v.start.scale); put("timeline.$i.visual.startX", v.start.x); put("timeline.$i.visual.startY", v.start.y)
            put("timeline.$i.visual.endScale", v.end.scale); put("timeline.$i.visual.endX", v.end.x); put("timeline.$i.visual.endY", v.end.y)
            put("timeline.$i.visual.ease", v.ease); put("timeline.$i.visual.fadeIn", v.fadeInMs); put("timeline.$i.visual.fadeOut", v.fadeOutMs)
            put("timeline.$i.source", clip.source); put("timeline.$i.duration", clip.sourceDurationMs); put("timeline.$i.name", clip.sourceName)
            put("timeline.$i.id", clip.id); put("timeline.$i.start", clip.startMs); put("timeline.$i.end", clip.endMs)
            put("timeline.$i.hook", clip.hook); put("timeline.$i.caption", clip.caption)
            put("timeline.$i.captionStart", clip.captionStartMs); put("timeline.$i.captionEnd", clip.captionEndMs)
            put("timeline.$i.effect", clip.effect); put("timeline.$i.volume", clip.volume)
            put("timeline.$i.fadeIn", clip.fadeInMs); put("timeline.$i.fadeOut", clip.fadeOutMs)
            segments("timeline.$i.cues", clip.subtitles)
        }
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
        val timelineCount = data.getProperty("timeline.count", "0").toInt()
        require(timelineCount in 0..60) { "Invalid timeline count" }
        val timeline = (0 until timelineCount).map { i ->
            TimelineClip(text("timeline.$i.id"), number("timeline.$i.start"), number("timeline.$i.end"),
                text("timeline.$i.hook"), text("timeline.$i.caption"), number("timeline.$i.captionStart"), number("timeline.$i.captionEnd"),
                text("timeline.$i.effect"), text("timeline.$i.volume").toFloat(), number("timeline.$i.fadeIn"), number("timeline.$i.fadeOut"), segments("timeline.$i.cues"),
                source = data.getProperty("timeline.$i.source", ""), sourceDurationMs = data.getProperty("timeline.$i.duration", "0").toLong(), sourceName = data.getProperty("timeline.$i.name", ""), visuals = ClipVisuals(
                    MotionPoint(data.getProperty("timeline.$i.visual.startScale", "1").toFloat(), data.getProperty("timeline.$i.visual.startX", "0").toFloat(), data.getProperty("timeline.$i.visual.startY", "0").toFloat()),
                    MotionPoint(data.getProperty("timeline.$i.visual.endScale", "1").toFloat(), data.getProperty("timeline.$i.visual.endX", "0").toFloat(), data.getProperty("timeline.$i.visual.endY", "0").toFloat()),
                    data.getProperty("timeline.$i.visual.ease", "true").toBooleanStrict(), data.getProperty("timeline.$i.visual.fadeIn", "0").toLong(), data.getProperty("timeline.$i.visual.fadeOut", "0").toLong()))
        }
        val music = if (data.getProperty("music.present", "false").toBooleanStrict()) MusicTrack(
            text("music.source"), text("music.name"), number("music.duration"), number("music.start"), number("music.end"),
            number("music.offset"), text("music.volume").toFloat(), number("music.fadeIn"), number("music.fadeOut")) else null
        return EditorProject(text("source"), number("duration"), number("start"), number("end"),
            text("mode"), bool("vertical"), bool("mute"), drafts, integer("selected"), text("hook"), text("caption"),
            text("effect"), text("subtitleSize").toFloat(), bool("subtitleYellow"), integer("subtitleDraft"),
            segments("current"), segments("sourceSpeech"), draftSegments,
            fillFrame = data.getProperty("fillFrame", "false").toBooleanStrict(),
            lastSpeechJobId = data.getProperty("speechJob", ""), lastSpeechUnits = data.getProperty("speechUnits", "0").toInt(),
            timelineClips = timeline, timelineMode = data.getProperty("timeline.mode", "false").toBooleanStrict(), selectedTimelineId = data.getProperty("timeline.selected", ""),
            currentTimelineId = data.getProperty("timeline.current", ""), music = music, captionPreset = data.getProperty("captionPreset", "BOLD"),
            projectId = data.getProperty("project.id", ""), projectName = data.getProperty("project.name", "")).also { it.validate() }
    }
}

