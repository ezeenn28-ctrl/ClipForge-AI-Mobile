package com.clipforge.mobile

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.Properties

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class CreatorToolsTest {
    private fun clip()=TimelineClip("first",0,10000,hook="Hook",caption="grabe haris",source="file:///source.mp4",sourceDurationMs=10000)
    @Test fun openingUsesExactOutputInterval(){val o=OpeningStyle(startMs=1000,endMs=3000);assertEquals(0f,o.alpha(999));assertEquals(1f,o.alpha(1000));assertEquals(1f,o.alpha(2999));assertEquals(0f,o.alpha(3000))}
    @Test fun openingAnimationNeverAffectsCaptionText(){val o=OpeningStyle(animation=OpeningAnimation.FADE,endMs=3000);assertEquals(.5f,o.alpha(100),.001f);assertEquals(.5f,o.alpha(2900),.001f);assertEquals(1f,o.alpha(1500),.001f)}
    @Test fun openingStyleAndTrackingPersist(){val f=SubjectFraming(listOf(FramingPoint(0,.2f,.5f),FramingPoint(1000,.8f,.5f)),.1f,0f,1.2f);val state=ClipEditorState(opening=OpeningStyle(CaptionFont.SERIF,CaptionColor.PINK,true,OpeningAnimation.POP,500,2500),framing=f);val p=Properties();EditorStateStore.write(p,"cut",state);assertEquals(state,EditorStateStore.read(p,"cut"))}
    @Test fun legacyStateUsesUnchangedOpeningDefaults(){assertEquals(ClipEditorState(),EditorStateStore.read(Properties(),"old"))}
    @Test fun framingWindowStaysInsideSource(){for(x in listOf(0f,.5f,1f)){val f=SubjectFraming(listOf(FramingPoint(0,x,.5f)));val w=FramingWindow.at(f,0,16f/9);assertTrue(w.left>=0&&w.left+w.width<=1.00001);assertEquals(9f/16,w.width/w.height*16f/9,.0001f)}}
    @Test fun framingInterpolatesAndOffsets(){val f=SubjectFraming(listOf(FramingPoint(0,.2f,.4f),FramingPoint(1000,.8f,.6f)),offsetX=.1f);assertEquals(.6f,f.center(500).first,.001f)}
    @Test fun framingCannotChangeLockedVideo(){val a=clip().copy(editor=ClipEditorState(videoLocked=true));val b=a.copy(editor=a.editor.copy(framing=SubjectFraming()));assertTrue(TimelineHistory(listOf(a)).edit(listOf(b)).rejection.isNotEmpty())}
    @Test fun openingCannotChangeLockedCaptions(){val a=clip().copy(editor=ClipEditorState(captionsLocked=true));val b=a.copy(editor=a.editor.copy(opening=OpeningStyle(background=true)));assertTrue(TimelineHistory(listOf(a)).edit(listOf(b)).rejection.isNotEmpty())}
    @Test fun dictionaryUsesLiteralBoundedSinglePass(){val d=mapOf("haris" to "Harris","Harris" to "changed","c++" to "C plus plus");assertEquals("Harris, harisx C plus plus",CaptionDictionary.correct("haris, harisx c++",d))}
    @Test fun dictionaryPreservesCueTimes(){val c=clip().copy(subtitles=listOf(WhisperNative.Segment(100,600,"haris")));val out=CaptionDictionary.apply(c,mapOf("haris" to "Harris"));assertEquals(100L,out.subtitles.single().startMs);assertEquals("Harris",out.subtitles.single().text);assertEquals(c.source,out.source)}
    @Test fun allTemplatesValidateWithoutChangingSourceSpeech(){CreatorTemplate.entries.forEach{val c=clip();val n=it.apply(c);n.validate(10000);assertEquals(c.source,n.source);assertEquals(c.caption,n.caption);assertEquals(c.startMs,n.startMs);assertEquals(c.endMs,n.endMs)}}
    @Test fun beatDraftKeepsSourceAndDurationAndIsUndoable(){val clips=listOf(clip());val bed=MusicTrack("file:///music.wav","Beat",10000,bpm=120);val result=BeatDraft.create(clips,bed);TimelineModel.validate(result,10000);assertTrue(result.size>1);assertEquals(TimelineModel.duration(clips),TimelineModel.duration(result));assertEquals(clips,TimelineHistory(clips).edit(result).undo().clips);assertTrue(result.all{it.source==clips.first().source})}
    @Test fun qualityAndCreatorStateRoundTrip(){val root=Files.createTempDirectory("creator").toFile();try{val p=EditorProject("file:///source.mp4",10000,0,10000,timelineMode=true,timelineClips=listOf(clip()),exportQuality=ExportQuality(720,24,4000000));val f=java.io.File(root,"p.properties");ProjectStore.write(f,p);assertEquals(p,ProjectStore.read(f))}finally{root.deleteRecursively()}}
    @Test fun versionPreservesOriginal(){val root=Files.createTempDirectory("versions").toFile();try{val p=EditorProject("file:///source.mp4",10000,0,10000,projectId=ProjectCatalog.newId(),projectName="Original");val v=ClipVersions.create(root,p,"Hook B");assertNotEquals(p.projectId,v.projectId);assertEquals(p,ProjectCatalog.read(root,p.projectId));assertEquals(2,ProjectCatalog.list(root).entries.size)}finally{root.deleteRecursively()}}
    @Test fun qualityEstimateIncreasesWithBitrate(){assertTrue(ExportQuality(bitrate=12000000).estimate(30000)>ExportQuality(bitrate=2000000).estimate(30000))}
    @Test fun dictionaryIncludedInValidatedBackup(){val root=Files.createTempDirectory("dict").toFile();try{CaptionDictionary.write(root,mapOf("haris" to "Harris"));val bytes=java.io.ByteArrayOutputStream();MetadataBackup.create(root,bytes);val staged=MetadataBackup.stage(root,bytes.toByteArray().inputStream());assertEquals(mapOf("haris" to "Harris"),CaptionDictionary.read(staged.directory));staged.discard()}finally{root.deleteRecursively()}}
}
