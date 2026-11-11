package com.clipforge.mobile

/** Local editing state. Export approval and cloud permission are deliberately absent. */
data class EditorProject(
    val source: String,
    val durationMs: Long,
    val startMs: Long,
    val endMs: Long,
    val mode: String = "Manual Editor",
    val vertical: Boolean = true,
    val mute: Boolean = false,
    val drafts: List<HighlightDraft> = emptyList(),
    val selectedDraft: Int = -1,
    val hook: String = "",
    val caption: String = "",
    val effect: String = "None",
    val subtitleSize: Float = 39f,
    val subtitleYellow: Boolean = false,
    val subtitleDraftIndex: Int = -1,
    val currentSegments: List<WhisperNative.Segment> = emptyList(),
    val sourceSegments: List<WhisperNative.Segment> = emptyList(),
    val draftSegments: Map<Int, List<WhisperNative.Segment>> = emptyMap(),
    val fillFrame: Boolean = false,
    val lastSpeechJobId: String = "",
    val lastSpeechUnits: Int = 0,
    val timelineClips: List<TimelineClip> = emptyList(),
    val timelineMode: Boolean = false,
    val selectedTimelineId: String = "",
    val currentTimelineId: String = "",
    val music: MusicTrack? = null,
    val captionPreset: String = "BOLD",
    val projectId: String = "",
    val projectName: String = "",
    val captionAppearance: CaptionAppearance = CaptionAppearance(),
    val hookTone: HookTone = HookTone.FUNNY,
    val campaignBrief: CampaignBrief = CampaignBrief(),
    val autoAudioEnabled: Boolean = false, val autoAudioHandled: List<String> = emptyList()
) {
    fun reviewedAgain(): EditorProject = copy(drafts = drafts.map { it.copy(approved = false) })

    fun validate() {
        require(autoAudioHandled.size<=500 && autoAudioHandled.all { it.matches(Regex("[A-Za-z0-9-]{1,80}")) })
        captionAppearance.validate()
        campaignBrief.validate()
        require(projectId.isEmpty() || projectId.matches(Regex("[a-f0-9]{32}"))) { "Invalid project identity" }
        require(projectName.length <= 80 && projectName.none { it.isISOControl() }) { "Invalid project name" }
        require(source.startsWith("content://") || source.startsWith("file://")) { "Project source must be a local video" }
        require(durationMs > 0 && startMs >= 0 && endMs > startMs && endMs <= durationMs) { "Invalid project trim" }
        require(mode in listOf("Manual Editor", "AI Draft")) { "Unknown editing mode" }
        require(effect in VideoLooks.names) { "Unknown visual effect" }
        require(subtitleSize.isFinite() && subtitleSize in 28f..55f) { "Invalid subtitle size" }
        TimelineModel.validate(timelineClips, durationMs)
        music?.validate()
        require(captionPreset in CaptionPreset.entries.map { it.name }) { "Invalid caption preset" }
        require(currentTimelineId.isEmpty() || timelineClips.any { it.id == currentTimelineId }) { "Invalid subtitle source association" }
        require(selectedTimelineId.isEmpty() || timelineClips.any { it.id == selectedTimelineId }) { "Invalid selected timeline clip" }
        require(lastSpeechUnits in 0..2880) { "Invalid applied speech checkpoint" }
        require(drafts.size <= 15) { "Too many drafts" }
        require(selectedDraft == -1 || selectedDraft in drafts.indices) { "Invalid selected draft" }
        require(subtitleDraftIndex == -1 || subtitleDraftIndex in drafts.indices) { "Invalid subtitle association" }
        drafts.forEach {
            require(it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= durationMs) { "Invalid draft trim" }
            require(it.effect in VideoLooks.names) { "Invalid draft effect" }
        }
        require(draftSegments.size <= 15 && draftSegments.keys.all { it in drafts.indices }) { "Invalid draft subtitles" }
        val currentDuration = timelineClips.firstOrNull { it.id == currentTimelineId }?.resolvedDuration(durationMs) ?: durationMs
        (listOf(currentSegments to currentDuration, sourceSegments to durationMs) + draftSegments.values.map { it to durationMs }).forEach { (segments, segmentDuration) ->
            require(segments.size <= 20_000) { "Transcript exceeds project limit" }
            segments.forEach {
                require(it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= segmentDuration) { "Invalid subtitle timing" }
            }
        }
    }
}


