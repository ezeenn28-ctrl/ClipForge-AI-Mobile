package com.clipforge.mobile

import java.io.File
import java.math.BigDecimal

/** Produces synthetic old-format-compatible app data for the real signed-APK update test. */
object ReleaseUpgradeSeed {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0], "files").apply { mkdirs() }
        val source = "file:///data/user/0/com.clipforge.mobile/files/media/upgrade.mp4"
        val first = EditorProject(source, 8000, 0, 8000,
            projectId = "a".repeat(32), projectName = "Release upgrade check", timelineMode = true,
            selectedTimelineId = "cut-a", captionPreset = "HIGHLIGHT",
            captionAppearance = CaptionAppearance(CaptionFont.MONO, .76f, CaptionColor.WHITE, CaptionColor.CYAN),
            timelineClips = listOf(
                TimelineClip("cut-a", 1000, 3000, hook = "Upgrade fixture", caption = "UPGRADE #CHECK",
                    captionStartMs = 1000, captionEndMs = 3000, speed = .5f, color = ColorAdjustments(brightness = .08f)),
                TimelineClip("cut-b", 4000, 5000, caption = "SECOND CLIP", captionStartMs = 4000, captionEndMs = 5000)))
        ProjectCatalog.save(root, first.copy(projectId = "b".repeat(32), projectName = "Upgrade draft B"))
        ProjectCatalog.save(root, first)
        CampaignEntryStore.write(File(root, "campaigns/submissions.properties"), listOf(
            CampaignEntry("upgrade-record", "Upgrade campaign fixture", "TikTok", "", SubmissionStatus.PENDING,
                100, BigDecimal("1"), BigDecimal("3"), BigDecimal("150"))))
        check(ProjectCatalog.list(root).entries.size == 2)
        check(TimelineModel.duration(ProjectCatalog.read(root, first.projectId).timelineClips) == 5000L)
        println("Generated two synthetic upgrade projects and one unpaid campaign record")
    }
}
