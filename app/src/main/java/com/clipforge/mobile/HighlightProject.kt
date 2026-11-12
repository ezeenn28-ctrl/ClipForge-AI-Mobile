package com.clipforge.mobile

/** Begin an independent highlight workflow without carrying another cut's subtitle clock. */
fun EditorProject.forHighlights(nextProjectId: String, nextName: String): EditorProject {
    require(nextProjectId.isNotEmpty()) { "Highlight project needs an identity" }
    return copy(projectId = nextProjectId, projectName = nextName.filterNot(Char::isISOControl).take(80),
        startMs = 0, endMs = durationMs, mode = "AI Draft",
        highlightParentId="",highlightEditing=false,drafts = emptyList(), selectedDraft = -1, hook = "", caption = "", effect = "None",
        currentSegments = sourceSegments, subtitleDraftIndex = -1, draftSegments = emptyMap(),
        lastSpeechJobId = "", lastSpeechUnits = 0, timelineClips = emptyList(), timelineMode = false,
        selectedTimelineId = "", currentTimelineId = "", music = null).also { it.validate() }
}

