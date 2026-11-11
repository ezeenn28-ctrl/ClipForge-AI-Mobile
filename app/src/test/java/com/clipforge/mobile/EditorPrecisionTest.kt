package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EditorPrecisionTest {
    private fun clip()=TimelineClip("cut",1000,11000,source="content://private/video",sourceDurationMs=20000,hook="Keep my hook",subtitles=listOf(WhisperNative.Segment(1500,2500,"Keep my words")),sounds=listOf(ClipSound("fx",2000,SoundKind.POP)))
    @Test fun lockedTracksRejectEditsWithoutCrashingAndUnlockCanBeUndone(){
        val c=clip().copy(editor=ClipEditorState(videoLocked=true,captionsLocked=true,voiceLocked=true,sfxLocked=true))
        val history=TimelineHistory(listOf(c))
        listOf(emptyList(),listOf(c.copy(startMs=2000)),listOf(c.copy(hook="changed")),listOf(c.copy(volume=.2f)),listOf(c.copy(sounds=emptyList()))).forEach{next->val rejected=history.edit(next);assertEquals(history.clips,rejected.clips);assertTrue(rejected.rejection.isNotEmpty());assertTrue(rejected.past.isEmpty())}
        val unlocked=history.edit(listOf(c.copy(editor=c.editor.copy(videoLocked=false,captionsLocked=false,voiceLocked=false,sfxLocked=false))))
        assertEquals("Track controls",unlocked.undoLabel)
        assertEquals(c,unlocked.undo().clips.single())
        assertEquals(unlocked.clips,unlocked.undo().redo().clips)
    }
    @Test fun videoLockPreventsReorderingAndCaptionLockProtectsPlacement(){
        val a=clip().copy(editor=ClipEditorState(videoLocked=true));val b=clip().copy(id="other")
        assertTrue(TimelineHistory(listOf(a,b)).edit(listOf(b,a)).rejection.isNotEmpty())
        val c=clip().copy(editor=ClipEditorState(captionsLocked=true))
        assertTrue(TimelineHistory(listOf(c)).edit(listOf(c.copy(editor=c.editor.copy(placement=TextPlacement(x=.1f))))).rejection.isNotEmpty())
    }
    @Test fun selectivePastePreservesMediaSpeechTrimAndUnselectedSettings(){
        val source=clip().copy(effect="Warm film",color=ColorAdjustments(brightness=.2f),volume=.4f,fadeInMs=3000,editor=ClipEditorState(placement=TextPlacement(x=.1f,scale=1.4f)))
        val target=clip().copy(id="target",startMs=4000,endMs=6000,source="content://other",hook="Target hook",subtitles=listOf(WhisperNative.Segment(4100,4500,"Target words")))
        val copy=SettingsClipboard(source,CaptionLook(size=44f,preset="HIGHLIGHT"))
        val result=copy.apply(target,setOf(SettingsPart.CAPTIONS,SettingsPart.AUDIO))
        assertEquals(target.source,result.source);assertEquals(target.startMs,result.startMs);assertEquals(target.endMs,result.endMs)
        assertEquals(target.subtitles,result.subtitles);assertEquals(target.hook,result.hook);assertEquals(target.sounds,result.sounds);assertEquals(target.color,result.color);assertEquals(target.effect,result.effect)
        assertEquals(.4f,result.volume,0f);assertEquals(2000L,result.fadeInMs);assertEquals(44f,result.editor.look!!.size,0f);assertEquals(source.editor.placement,result.editor.placement)
    }
    @Test fun muteKeepsSavedContentButRemovesItFromOutputClocks(){
        val c=clip().copy(editor=ClipEditorState(captionsMuted=true,voiceMuted=true,sfxMuted=true))
        assertEquals(0f,TimelineModel.audioGain(c,500),0f)
        assertTrue(TimelineModel.subtitles(listOf(c)).isEmpty());assertTrue(SoundTimeline.events(listOf(c)).isEmpty())
        assertTrue(c.subtitles.isNotEmpty());assertTrue(c.sounds.isNotEmpty())
        val music=MusicTrack("file:///music.wav","Music",2000,fadeInMs=0,muted=true)
        assertEquals(0f,music.gain(500,2000),0f);assertEquals(0f,music.envelope(2000)!!.volume,0f)
    }
    @Test fun editLabelsAndHistoryAreBoundedAndNewEditDropsRedo(){
        var history=TimelineHistory(listOf(clip()))
        repeat(40){n->history=history.edit(listOf(history.clips.single().copy(volume=(n+1)/50f)))}
        assertEquals(30,history.past.size);assertEquals(30,history.pastLabels.size);assertEquals("Audio",history.undoLabel)
        val before=history.undo();assertEquals("Audio",before.redoLabel)
        val trimmed=before.edit(listOf(before.clips.single().copy(startMs=1500)))
        assertEquals("Trim",trimmed.undoLabel);assertTrue(trimmed.future.isEmpty());assertTrue(trimmed.futureLabels.isEmpty())
    }
    @Test fun snapTargetsUseTrimSpeedCutOffsetCaptionsAndSoundCues(){
        val first=clip().copy(id="first",startMs=0,endMs=5000,subtitles=emptyList(),sounds=emptyList())
        val second=clip().copy(speed=2f)
        val targets=EditorPrecision.snapTargets(listOf(first,second),null)
        assertTrue(5250L in targets);assertTrue(5750L in targets);assertTrue(5500L in targets);assertTrue(5000L in targets);assertTrue(10000L in targets)
        assertEquals(5500L,TimelineTouch.snap(5490,targets,20))
    }
    @Test fun newEditorSettingsRoundTripWithoutRestoringApproval(){
        val root=Files.createTempDirectory("precision-store").toFile()
        try{
            val c=clip().copy(editor=ClipEditorState(videoLocked=true,captionsLocked=true,voiceLocked=true,sfxLocked=true,captionsMuted=true,voiceMuted=true,sfxMuted=true,placement=TextPlacement(.1f,-.1f,1.3f,.05f,.1f,.8f),look=CaptionLook(size=42f,preset="NEON")))
            val project=EditorProject(c.source,20000,1000,11000,timelineClips=listOf(c),music=MusicTrack("file:///music.wav","Bed",2000,muted=true,locked=true))
            val file=File(root,"project.properties");ProjectStore.write(file,project)
            assertEquals(project,ProjectStore.read(file))
            val old=java.util.Properties();val legacy=EditorStateStore.read(old,"missing")
            assertEquals(ClipEditorState(),legacy)
        }finally{root.deleteRecursively()}
    }
    @Test fun resetIsScopedAndPeakMeterResetsWithoutInventingAudio(){
        val c=clip().copy(volume=.2f,cleanupStrength=.7f,editor=ClipEditorState(placement=TextPlacement(x=.2f)))
        val reset=EditorPrecision.reset(c,SettingsPart.CAPTIONS)
        assertEquals(TextPlacement(),reset.editor.placement);assertEquals(c.subtitles,reset.subtitles);assertEquals(c.volume,reset.volume,0f)
        val meter=PcmPeak();meter.offer(.2f);meter.offer(-.8f);meter.offer(Float.NaN)
        assertEquals(.4f,meter.take(.5f,true),.0001f);assertEquals(0f,meter.take(1f,true),0f)
    }
}
