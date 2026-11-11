package com.clipforge.mobile

import java.io.File

data class EditingPreset(val id: String, val name: String, val template: EditorProject) {
    fun apply(clip: TimelineClip): TimelineClip {
        val style = template.timelineClips.single()
        return clip.copy(effect = style.effect, color = style.color, visuals = style.visuals, transition = style.transition,
            sounds = style.sounds.filter { it.sourceMs < clip.sourceSpanMs }.map { cue ->
                cue.copy(id = java.util.UUID.randomUUID().toString(), sourceMs = clip.startMs + cue.sourceMs)
            })
    }
}

/** Appearance/settings templates contain no source-video URI, transcript, posting caption or approval. */
object EditingPresets {
    const val LIMIT = 20
    private fun file(root: File, id: String): File {
        require(id.matches(Regex("[a-f0-9]{32}")))
        return File(root, "editing-presets/$id.properties")
    }
    @Synchronized fun save(root: File, name: String, project: EditorProject, clip: TimelineClip): EditingPreset {
        require(list(root).size < LIMIT) { "You can save up to $LIMIT presets" }
        val clean = name.trim().filterNot(Char::isISOControl).take(60)
        require(clean.isNotEmpty()) { "Name this preset" }
        val id = ProjectCatalog.newId()
        val span = clip.sourceSpanMs
        val style = clip.copy(id = "template", startMs = 0, endMs = span, hook = "", caption = "", captionStartMs = 0,
            captionEndMs = span, subtitles = emptyList(), source = "file:///preset-template", sourceDurationMs = span, sourceName = "",
            speed = 1f, volume = 1f, fadeInMs = 0, fadeOutMs = 0, voiceGain = 1f, audioProfile = ClipAudioProfile(),
            sounds = clip.sounds.filter { it.sourceMs in clip.startMs until clip.endMs }.map { it.copy(sourceMs = it.sourceMs - clip.startMs) })
        val template = EditorProject("file:///preset-template", span, 0, span, mode = "Manual Editor", vertical = project.vertical,
            fillFrame = project.fillFrame, subtitleSize = project.subtitleSize, subtitleYellow = project.subtitleYellow,
            captionPreset = project.captionPreset, captionAppearance = project.captionAppearance,
            timelineMode = true, timelineClips = listOf(style), selectedTimelineId = style.id, projectId = id, projectName = clean,
            hookTone = project.hookTone)
        ProjectStore.write(file(root, id), template)
        return EditingPreset(id, clean, template)
    }
    @Synchronized fun list(root: File): List<EditingPreset> = File(root, "editing-presets").listFiles().orEmpty()
        .filter { it.name.matches(Regex("[a-f0-9]{32}\\.properties")) }.sortedByDescending(File::lastModified).take(LIMIT)
        .mapNotNull { f -> runCatching { ProjectStore.read(f)?.let { p ->
            require(p.timelineClips.size == 1); EditingPreset(f.nameWithoutExtension, p.projectName, p)
        } }.getOrNull() }
    @Synchronized fun remove(root: File, id: String) {
        val f = file(root, id); f.delete(); ProjectStore.previousFile(f).delete()
    }
}
