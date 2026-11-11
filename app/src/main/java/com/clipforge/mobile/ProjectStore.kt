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
        put("autoAudio.intensity",safe.audioIntensity.name);put("autoAudio.enabled",safe.autoAudioEnabled);put("autoAudio.handled",safe.autoAudioHandled.joinToString(","))
        put("hookTone", safe.hookTone.name)
        put("brief.enabled", safe.campaignBrief.enabled); put("brief.tags", safe.campaignBrief.tags)
        put("brief.caption", safe.campaignBrief.postingCaption); put("brief.minimum", safe.campaignBrief.minimum)
        put("brief.maximum", safe.campaignBrief.maximum); put("brief.source", safe.campaignBrief.sourceSeconds)
        put("project.id", safe.projectId); put("project.name", safe.projectName)
        put("fillFrame", safe.fillFrame)
        put("speechJob", safe.lastSpeechJobId); put("speechUnits", safe.lastSpeechUnits)
        put("source", safe.source); put("duration", safe.durationMs)
        put("start", safe.startMs); put("end", safe.endMs)
        put("mode", safe.mode); put("vertical", safe.vertical); put("mute", safe.mute)
        put("selected", safe.selectedDraft); put("hook", safe.hook); put("caption", safe.caption)
        put("effect", safe.effect); put("subtitleSize", safe.subtitleSize)
        put("captionPreset", safe.captionPreset)
        put("caption.font", safe.captionAppearance.font.name); put("caption.y", safe.captionAppearance.positionY)
        put("caption.color", safe.captionAppearance.color.name); put("caption.highlight", safe.captionAppearance.highlight.name)
        put("caption.align", safe.captionAppearance.alignment.name)
        put("caption.layout", safe.captionAppearance.layout.name)
        put("caption.wordHighlight", safe.captionAppearance.wordHighlight)
        put("subtitleYellow", safe.subtitleYellow); put("subtitleDraft", safe.subtitleDraftIndex)
        put("drafts.count", safe.drafts.size)
        safe.drafts.forEachIndexed { i, draft ->
            put("drafts.$i.start", draft.startMs); put("drafts.$i.end", draft.endMs)
            put("drafts.$i.visual", draft.visualScore ?: -1)
            put("drafts.$i.hook", draft.hook); put("drafts.$i.caption", draft.caption)
            put("drafts.$i.effect", draft.effect)
            put("drafts.$i.rank", draft.rankScore ?: -1); put("drafts.$i.reasons", draft.reasons.joinToString("|"))
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
            put("music.loop",music.loop);put("music.duck",music.ducking);put("music.duckFloor",music.duckFloor)
            put("music.bpm",music.bpm);put("music.origin",music.origin);put("music.mood",music.mood);put("music.beats",music.beats.joinToString(","));put("music.libraryId",music.libraryId)
            put("music.volume", music.volume); put("music.fadeIn", music.fadeInMs); put("music.fadeOut", music.fadeOutMs)
        }
        safe.timelineClips.forEachIndexed { i, clip ->
            val v = clip.visuals
            put("timeline.$i.speed", clip.speed)
            put("timeline.$i.cleanup",clip.cleanupStrength);put("timeline.$i.voiceGain",clip.voiceGain);put("timeline.$i.audio.analyzed",clip.audioProfile.analyzed);put("timeline.$i.audio.rms",clip.audioProfile.rms);put("timeline.$i.audio.peak",clip.audioProfile.peak)
            put("timeline.$i.audio.sampled",clip.audioProfile.sampled);put("timeline.$i.audio.visual",clip.audioProfile.visualActivity)
            put("timeline.$i.audio.activity",clip.audioProfile.activity.joinToString(","){"${it.startMs}:${it.endMs}"})
            put("timeline.$i.transition.kind", clip.transition.kind.name); put("timeline.$i.transition.duration", clip.transition.durationMs)
            put("timeline.$i.color.brightness", clip.color.brightness); put("timeline.$i.color.contrast", clip.color.contrast)
            put("timeline.$i.color.saturation", clip.color.saturation); put("timeline.$i.color.warmth", clip.color.warmth)
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
            put("timeline.$i.sounds.count", clip.sounds.size)
            clip.sounds.forEachIndexed { j, sound ->
                put("timeline.$i.sounds.$j.id", sound.id); put("timeline.$i.sounds.$j.at", sound.sourceMs)
                put("timeline.$i.sounds.$j.kind", sound.kind.name); put("timeline.$i.sounds.$j.volume", sound.volume)
                sound.asset?.let { asset ->
                    put("timeline.$i.sounds.$j.asset.id", asset.id); put("timeline.$i.sounds.$j.asset.name", asset.name)
                    put("timeline.$i.sounds.$j.asset.source", asset.source); put("timeline.$i.sounds.$j.asset.duration", asset.durationMs)
                    put("timeline.$i.sounds.$j.asset.origin", asset.origin); put("timeline.$i.sounds.$j.asset.page", asset.page)
                }
            }
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
            // Keep a validated prior generation; a corrupt primary must never replace it.
            if (file.isFile && runCatching { readStrict(file) }.getOrNull() != null) {
                val previous = previousFile(file)
                val previousTemp = File(file.parentFile, file.name + ".previous.partial")
                try {
                    file.inputStream().use { input -> previousTemp.outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
                    check(previousTemp.renameTo(previous)) { "Could not preserve previous autosave" }
                } finally { previousTemp.delete() }
            }
            check(temporary.renameTo(file)) { "Could not save local project" }
        } finally { temporary.delete() }
    }

    fun previousFile(file: File): File = File(file.parentFile, file.name + ".previous")
    @Synchronized fun previous(file: File): EditorProject? = readStrict(previousFile(file))
    @Synchronized fun read(file: File): EditorProject? = readWithStatus(file).first
    @Synchronized fun readWithStatus(file: File): Pair<EditorProject?, Boolean> {
        val primary = runCatching { readStrict(file) }
        primary.getOrNull()?.let { return it to false }
        runCatching { readStrict(previousFile(file)) }.getOrNull()?.let { return it to true }
        return primary.getOrThrow() to false
    }
    private fun readStrict(file: File): EditorProject? {
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
                captionStartMs = number("drafts.$i.captionStart"), captionEndMs = number("drafts.$i.captionEnd"),
                rankScore = data.getProperty("drafts.$i.rank", "-1").toInt().takeIf { it in 0..100 },
                reasons = data.getProperty("drafts.$i.reasons", "").split("|").filter(String::isNotBlank).take(8))
        }
        val draftSegments = (0 until count).associateWith { segments("drafts.$it.segments") }.filterValues { it.isNotEmpty() }
        val timelineCount = data.getProperty("timeline.count", "0").toInt()
        require(timelineCount in 0..60) { "Invalid timeline count" }
        val timeline = (0 until timelineCount).map { i ->
            val soundCount = data.getProperty("timeline.$i.sounds.count", "0").toInt()
            require(soundCount in 0..12) { "Invalid sound effects count" }
            val sounds = (0 until soundCount).map { j -> ClipSound(text("timeline.$i.sounds.$j.id"), number("timeline.$i.sounds.$j.at"),
                SoundKind.valueOf(text("timeline.$i.sounds.$j.kind")), text("timeline.$i.sounds.$j.volume").toFloat(),
                    if (data.containsKey("timeline.$i.sounds.$j.asset.id")) SoundAsset(
                        text("timeline.$i.sounds.$j.asset.id"), text("timeline.$i.sounds.$j.asset.name"),
                        text("timeline.$i.sounds.$j.asset.source"), number("timeline.$i.sounds.$j.asset.duration"),
                        text("timeline.$i.sounds.$j.asset.origin"), text("timeline.$i.sounds.$j.asset.page")) else null) }
            TimelineClip(text("timeline.$i.id"), number("timeline.$i.start"), number("timeline.$i.end"),
                text("timeline.$i.hook"), text("timeline.$i.caption"), number("timeline.$i.captionStart"), number("timeline.$i.captionEnd"),
                text("timeline.$i.effect"), text("timeline.$i.volume").toFloat(), number("timeline.$i.fadeIn"), number("timeline.$i.fadeOut"), segments("timeline.$i.cues"),
                source = data.getProperty("timeline.$i.source", ""), sourceDurationMs = data.getProperty("timeline.$i.duration", "0").toLong(), sourceName = data.getProperty("timeline.$i.name", ""), visuals = ClipVisuals(
                    MotionPoint(data.getProperty("timeline.$i.visual.startScale", "1").toFloat(), data.getProperty("timeline.$i.visual.startX", "0").toFloat(), data.getProperty("timeline.$i.visual.startY", "0").toFloat()),
                    MotionPoint(data.getProperty("timeline.$i.visual.endScale", "1").toFloat(), data.getProperty("timeline.$i.visual.endX", "0").toFloat(), data.getProperty("timeline.$i.visual.endY", "0").toFloat()),
                    data.getProperty("timeline.$i.visual.ease", "true").toBooleanStrict(), data.getProperty("timeline.$i.visual.fadeIn", "0").toLong(), data.getProperty("timeline.$i.visual.fadeOut", "0").toLong()),
                speed = data.getProperty("timeline.$i.speed", "1").toFloat(),
                color = ColorAdjustments(data.getProperty("timeline.$i.color.brightness", "0").toFloat(),
                    data.getProperty("timeline.$i.color.contrast", "1").toFloat(),
                    data.getProperty("timeline.$i.color.saturation", "1").toFloat(),
                    data.getProperty("timeline.$i.color.warmth", "0").toFloat()),
                sounds = sounds, cleanupStrength=data.getProperty("timeline.$i.cleanup","0").toFloat(), voiceGain=data.getProperty("timeline.$i.voiceGain","1").toFloat(),
                audioProfile=ClipAudioProfile(data.getProperty("timeline.$i.audio.analyzed","false").toBooleanStrict(),data.getProperty("timeline.$i.audio.rms","0").toFloat(),data.getProperty("timeline.$i.audio.peak","0").toFloat(),data.getProperty("timeline.$i.audio.activity","").split(',').filter(String::isNotBlank).map{entry->val pair=entry.split(':');require(pair.size==2);AudioRegion(pair[0].toLong(),pair[1].toLong())},data.getProperty("timeline.$i.audio.sampled","false").toBooleanStrict(),data.getProperty("timeline.$i.audio.visual","-1").toInt()),
                transition = CutTransition(CutTransitionKind.valueOf(data.getProperty("timeline.$i.transition.kind", "CUT")),
                    data.getProperty("timeline.$i.transition.duration", "400").toLong()))
        }
        val music = if (data.getProperty("music.present", "false").toBooleanStrict()) MusicTrack(
            text("music.source"), text("music.name"), number("music.duration"), number("music.start"), number("music.end"),
            number("music.offset"), text("music.volume").toFloat(), number("music.fadeIn"), number("music.fadeOut"),loop=data.getProperty("music.loop","false").toBooleanStrict(),ducking=data.getProperty("music.duck","false").toBooleanStrict(),duckFloor=data.getProperty("music.duckFloor",".25").toFloat(),bpm=data.getProperty("music.bpm","0").toInt(),origin=data.getProperty("music.origin","Imported local music · verify reuse permission"),mood=data.getProperty("music.mood",""),beats=data.getProperty("music.beats","").split(',').filter(String::isNotBlank).map(String::toLong),libraryId=data.getProperty("music.libraryId","")) else null
        return EditorProject(text("source"), number("duration"), number("start"), number("end"),
            text("mode"), bool("vertical"), bool("mute"), drafts, integer("selected"), text("hook"), text("caption"),
            text("effect"), text("subtitleSize").toFloat(), bool("subtitleYellow"), integer("subtitleDraft"),
            segments("current"), segments("sourceSpeech"), draftSegments,
            fillFrame = data.getProperty("fillFrame", "false").toBooleanStrict(),
            lastSpeechJobId = data.getProperty("speechJob", ""), lastSpeechUnits = data.getProperty("speechUnits", "0").toInt(),
            timelineClips = timeline, timelineMode = data.getProperty("timeline.mode", "false").toBooleanStrict(), selectedTimelineId = data.getProperty("timeline.selected", ""),
            currentTimelineId = data.getProperty("timeline.current", ""), music = music, captionPreset = data.getProperty("captionPreset", "BOLD"),
            projectId = data.getProperty("project.id", ""), projectName = data.getProperty("project.name", ""),
            hookTone = HookTone.valueOf(data.getProperty("hookTone", "FUNNY")),
            audioIntensity=AudioIntensity.valueOf(data.getProperty("autoAudio.intensity","BALANCED")), autoAudioEnabled=data.getProperty("autoAudio.enabled","false").toBooleanStrict(),autoAudioHandled=data.getProperty("autoAudio.handled","").split(',').filter(String::isNotBlank),
            campaignBrief = CampaignBrief(data.getProperty("brief.enabled", "false").toBooleanStrict(),
                data.getProperty("brief.tags", ""), data.getProperty("brief.caption", ""), data.getProperty("brief.minimum", "15"),
                data.getProperty("brief.maximum", "30"), data.getProperty("brief.source", "0")),
            captionAppearance = CaptionAppearance(
                font = CaptionFont.valueOf(data.getProperty("caption.font", "SANS")),
                positionY = data.getProperty("caption.y", (970f / 1280f).toString()).toFloat(),
                color = CaptionColor.valueOf(data.getProperty("caption.color", "INHERIT")),
                highlight = CaptionColor.valueOf(data.getProperty("caption.highlight", "YELLOW")),
                alignment = CaptionAlignment.valueOf(data.getProperty("caption.align", "CENTER")),
                layout = ReferenceLayout.valueOf(data.getProperty("caption.layout", "STANDARD")),
                wordHighlight = data.getProperty("caption.wordHighlight", "false").toBooleanStrict())).also { it.validate() }
    }
}



