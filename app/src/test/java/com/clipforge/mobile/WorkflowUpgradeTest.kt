package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WorkflowUpgradeTest {
    private fun project(name: String = "Edit") = EditorProject("content://fixture/video", 60_000, 0, 20_000,
        mode = "Manual Editor", projectId = "a".repeat(32), projectName = name, timelineMode = true,
        timelineClips = listOf(TimelineClip("cut", 0, 20_000, source = "content://fixture/video", sourceDurationMs = 60_000)), selectedTimelineId = "cut")

    @Test fun corruptPrimaryRestoresValidatedPreviousGenerationAndNeverRestoresApproval() {
        val root = Files.createTempDirectory("recovery").toFile()
        try {
            val file = File(root, "project.properties")
            ProjectStore.write(file, project("Before")); ProjectStore.write(file, project("After"))
            assertEquals("Before", ProjectStore.previous(file)!!.projectName)
            file.writeText("truncated")
            assertEquals("Before", ProjectStore.read(file)!!.projectName)
            assertTrue(ProjectStore.readWithStatus(file).second)
            ProjectStore.write(file, project("Recovered"))
            assertEquals("Before", ProjectStore.previous(file)!!.projectName)
            assertEquals("Recovered", ProjectStore.read(file)!!.projectName)
        } finally { root.deleteRecursively() }
    }
    @Test fun interruptedPartialIsIgnoredAndMissingPrimaryCanRecover() {
        val root = Files.createTempDirectory("recovery-partial").toFile()
        try {
            val file = File(root, "project.properties")
            ProjectStore.write(file, project("One")); ProjectStore.write(file, project("Two"))
            File(root, "project.properties.partial").writeText("incomplete")
            assertEquals("Two", ProjectStore.read(file)!!.projectName)
            file.delete(); assertEquals("One", ProjectStore.read(file)!!.projectName)
        } finally { root.deleteRecursively() }
    }
    @Test fun wordEstimatesAreContiguousBoundedAndMatchActiveSourceClock() {
        val cue = WhisperNative.Segment(5000, 7100, "Bakit hello world?")
        val words = CaptionTiming.words(cue)
        assertEquals(listOf("Bakit", "hello", "world?"), words.map { it.text })
        assertEquals(cue.startMs, words.first().startMs); assertEquals(cue.endMs, words.last().endMs)
        words.zipWithNext().forEach { (a,b) -> assertEquals(a.endMs,b.startMs) }
        words.forEachIndexed { i, w -> assertEquals(i, CaptionTiming.activeWord(cue,w.startMs)); assertTrue(w.endMs > w.startMs) }
        assertEquals(-1, CaptionTiming.activeWord(cue,7100))
    }
    @Test fun impossibleWordSpanDoesNotInventNegativeOrZeroLengthCues() {
        assertTrue(CaptionTiming.words(WhisperNative.Segment(0,1,"a b c")).isEmpty())
    }
    @Test fun manualTimingRejectsOverlapsAndInvalidSourceBounds() {
        val cues = listOf(WhisperNative.Segment(0,1000,"A"), WhisperNative.Segment(1500,2000,"B"))
        assertThrows(IllegalArgumentException::class.java) { CaptionTiming.replace(cues,1,cues[1].copy(startMs=900),3000) }
        assertThrows(IllegalArgumentException::class.java) { CaptionTiming.replace(cues,0,cues[0].copy(endMs=5000),3000) }
        assertEquals(1400L,CaptionTiming.replace(cues,0,cues[0].copy(endMs=1400),3000)[0].endMs)
    }
    @Test fun correctionsAreLiteralWholeWordsAndPreserveUnrelatedTaglish() {
        val cues = listOf(WhisperNative.Segment(0,1000,"JAM naman si jammy; jam!"))
        assertEquals("Harris naman si jammy; Harris!",CaptionTiming.correct(cues,"jam","Harris")[0].text)
        assertEquals("$1 naman si jammy; $1!",CaptionTiming.correct(cues,"jam","$1")[0].text)
    }
    @Test fun presetDoesNotCopySourceTextAndRemapsSoundAnchorsWithoutChangingSpeed() {
        val root=Files.createTempDirectory("presets").toFile()
        try {
            val original=project().timelineClips.single().copy(startMs=5000,endMs=15000,hook="Private hook",subtitles=listOf(WhisperNative.Segment(5000,6000,"Private words")),
                effect="Warm film", sounds=listOf(ClipSound("fx",7000,SoundKind.POP,.4f)))
            val preset=EditingPresets.save(root,"My look",project().copy(hookTone=HookTone.STORY,captionAppearance=CaptionAppearance(wordHighlight=true)),original)
            val restored=EditingPresets.list(root).single()
            assertEquals(preset.id,restored.id);assertTrue(restored.template.timelineClips.single().hook.isEmpty())
            assertTrue(restored.template.timelineClips.single().subtitles.isEmpty())
            assertEquals(HookTone.STORY,restored.template.hookTone); assertTrue(restored.template.captionAppearance.wordHighlight)
            val target=original.copy(startMs=20000,endMs=26000,hook="Keep this",speed=2f)
            val applied=restored.apply(target)
            assertEquals(22000L,applied.sounds.single().sourceMs);assertEquals(.4f,applied.sounds.single().volume,0f)
            assertEquals("Keep this",applied.hook);assertEquals(2f,applied.speed,0f);assertEquals("Warm film",applied.effect)
            EditingPresets.remove(root,restored.id);assertTrue(EditingPresets.list(root).isEmpty())
        }finally{root.deleteRecursively()}
    }
    @Test fun presetOmitsCuesOutsideShortTargetCut() {
        val template=project().copy(timelineClips=listOf(project().timelineClips.single().copy(sounds=listOf(ClipSound("late",19000,SoundKind.POP)))))
        assertTrue(EditingPreset("a".repeat(32),"Look",template).apply(TimelineClip("short",5000,6000)).sounds.isEmpty())
    }
    @Test fun queueRequiresApprovalFreezesEditsAndClearsAuthorizationOnRestart() {
        val root=Files.createTempDirectory("queue").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java){ExportQueue.add(root,project(),false)}
            val item=ExportQueue.add(root,project("Approved"),true)
            assertEquals(QueueStatus.READY,item.status)
            val review=ExportQueue.forReview(item)
            assertNotEquals(item.project.projectId,review.projectId)
            assertEquals(item.project.timelineClips,review.timelineClips)
            val changed=project("Later edits");assertNotEquals(changed.projectName,item.project.projectName)
            assertEquals("Approved",ExportQueue.list(root).single().project.projectName)
            assertEquals(QueueStatus.REVIEW,ExportQueue.list(root).single().status)
            ExportQueue.record(root,item.copy(status=QueueStatus.RENDERING))
            assertEquals(QueueStatus.REVIEW,ExportQueue.list(root).single().status)
            ExportQueue.record(root,item.copy(status=QueueStatus.COMPLETE,uri="content://gallery/1"))
            assertEquals(QueueStatus.COMPLETE,ExportQueue.list(root).single().status)
            ExportQueue.remove(root,item.id);assertTrue(ExportQueue.list(root).isEmpty())
        }finally{root.deleteRecursively()}
    }
    @Test fun queueLimitPreventsUnboundedSnapshots() {
        val root=Files.createTempDirectory("queue-limit").toFile()
        try {
            repeat(ExportQueue.LIMIT){ExportQueue.add(root,project(),true)}
            assertThrows(IllegalArgumentException::class.java){ExportQueue.add(root,project(),true)}
        }finally{root.deleteRecursively()}
    }
    @Test fun campaignBriefAndWordModeStayProjectLocalThroughSaveAndBackup() {
        val root=Files.createTempDirectory("brief").toFile()
        try {
            val p=project().copy(campaignBrief=CampaignBrief(true,"@show #episode","@show #episode","15","30","1"),captionAppearance=CaptionAppearance(wordHighlight=true))
            ProjectCatalog.save(root,p)
            val saved=ProjectCatalog.read(root,p.projectId)
            assertEquals(p.campaignBrief,saved.campaignBrief);assertTrue(saved.captionAppearance.wordHighlight)
            assertTrue(CampaignRules(saved.campaignBrief.tags).issues(saved.campaignBrief.postingCaption,20000,false,false).isNotEmpty())
            val other=project("Other").copy(projectId="b".repeat(32));ProjectCatalog.save(root,other)
            assertFalse(ProjectCatalog.read(root,other.projectId).campaignBrief.enabled)
            assertTrue(ProjectCatalog.read(root,p.projectId).campaignBrief.enabled)
        }finally{root.deleteRecursively()}
    }
    @Test fun languageHintSurvivesInterruptedSpeechCheckpoint() {
        val root=Files.createTempDirectory("language").toFile()
        try {
            val p=SpeechJob("language-test","content://video/1",30000,SpeechJobMode.FIRST_30,listOf(SpeechRange(0,0,30000)),language="tl")
            val f=File(root,"speech.properties");SpeechJobStore.write(f,p)
            assertEquals("tl",SpeechJobStore.read(f)!!.recovered().language)
            assertEquals(SpeechJobStatus.PAUSED,SpeechJobStore.read(f)!!.recovered().status)
            assertThrows(IllegalArgumentException::class.java){p.copy(language="unsupported").validate()}
        }finally{root.deleteRecursively()}
    }
    @Test fun rankingExplainsSentenceBoundaryAndPenalizesDeadAir() {
        val clean=listOf(WhisperNative.Segment(0,8000,"Why does this happen?"),WhisperNative.Segment(8000,16000,"Here is the complete answer."))
        val result=TranscriptHighlights.detect(clean,30000).first()
        assertTrue(result.reasons.any{it.contains("sentence")});assertTrue(result.rankScore!! in 0..100);assertFalse(result.approved)
        val sparse=TranscriptHighlights.detect(listOf(WhisperNative.Segment(0,1000,"Why?")),30000).single()
        assertTrue(sparse.reasons.any{it.contains("Pause")});assertTrue(result.rankScore!! > sparse.rankScore!!)
    }
}
