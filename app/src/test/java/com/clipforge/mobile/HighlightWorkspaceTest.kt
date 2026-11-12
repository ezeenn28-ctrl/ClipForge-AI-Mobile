package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HighlightWorkspaceTest {
    private fun project()=EditorProject("file:///source.mp4",400000,0,20000,mode="AI Draft",projectId=ProjectCatalog.newId(),projectName="Campaign",
        drafts=(0 until 15).map{HighlightDraft(it*20000L,(it+1)*20000L,hook="Hook $it",caption="Caption $it")})
    @Test fun fifteenHighlightsHaveDistinctStableIdentities(){val p=project();val ids=p.drafts.indices.map{HighlightWorkspace.cut(p,it).id};assertEquals(15,ids.distinct().size);assertEquals(ids,p.drafts.indices.map{HighlightWorkspace.cut(p,it).id})}
    @Test fun revisitingHighlightKeepsTrimStyleSpeedWordsAndSounds(){val p=project();val original=HighlightWorkspace.cut(p,0);val edited=original.copy(startMs=1000,endMs=19000,hook="Edited",speed=2f,sounds=listOf(ClipSound("pop",1500,SoundKind.POP)),editor=original.editor.copy(placement=TextPlacement(x=.2f)));val saved=p.copy(timelineClips=listOf(edited));assertEquals(edited,HighlightWorkspace.cut(saved,0));assertEquals("Hook 1",HighlightWorkspace.cut(saved,1).hook)}
    @Test fun oldMatchingCutIsAdoptedWithoutDiscardingItsEdits(){val p=project();val old=TimelineClip("old-cut",0,20000,hook="Keep this",source=p.source,sourceDurationMs=p.durationMs);val c=HighlightWorkspace.cut(p.copy(timelineClips=listOf(old)),0);assertEquals("Keep this",c.hook);assertEquals(HighlightWorkspace.id(p.source,0,p.drafts[0]),c.id)}
    @Test fun separateSnapshotsDoNotConcatenateFifteenClips(){val p=project();val snapshots=p.drafts.indices.map{HighlightWorkspace.snapshot(p,it)};assertEquals(15,snapshots.size);assertTrue(snapshots.all{ExportQueue.clips(it).size==1&&it.drafts.isEmpty()&&!it.highlightEditing});assertEquals(15,snapshots.map{it.timelineClips.single().id}.distinct().size);assertEquals(20000L,TimelineModel.duration(snapshots[4].timelineClips))}
    @Test fun currentHighlightExportIgnoresOtherWorkspaceCuts(){val p=project();val cuts=(0..1).map{HighlightWorkspace.cut(p,it)};val workspace=p.copy(highlightEditing=true,selectedDraft=1,timelineMode=true,timelineClips=cuts);workspace.validate();assertEquals(listOf(cuts[1]),ExportQueue.clips(workspace))}
    @Test fun shortlistAndEditsSurviveSaveAndRestore(){val root=Files.createTempDirectory("highlights").toFile();try{val p=project();val c=HighlightWorkspace.cut(p,4).copy(hook="My edited hook");val saved=p.copy(highlightEditing=true,selectedDraft=4,timelineMode=true,timelineClips=listOf(c),selectedTimelineId=c.id);val f=java.io.File(root,"p.properties");ProjectStore.write(f,saved);val reopened=ProjectStore.read(f)!!;assertEquals(15,reopened.drafts.size);assertTrue(reopened.highlightEditing);assertEquals(c,HighlightWorkspace.cut(reopened,4))}finally{root.deleteRecursively()}}
    @Test fun selectingBatchDoesNotAuthorizeExport(){val root=Files.createTempDirectory("highlight-queue").toFile();try{val p=project();assertThrows(IllegalArgumentException::class.java){ExportQueue.addBatch(root,listOf(HighlightWorkspace.snapshot(p,0)),false)};assertTrue(ExportQueue.list(root).isEmpty())}finally{root.deleteRecursively()}}
    @Test fun fifteenApprovedSnapshotsQueueSeparatelyAndRestoreForReview(){val root=Files.createTempDirectory("highlight-queue").toFile();try{val p=project();val added=ExportQueue.addBatch(root,p.drafts.indices.map{HighlightWorkspace.snapshot(p,it)},true);assertEquals(15,added.size);assertTrue(added.all{it.status==QueueStatus.READY});val restored=ExportQueue.list(root);assertEquals(15,restored.size);assertTrue(restored.all{it.status==QueueStatus.REVIEW})}finally{root.deleteRecursively()}}
    @Test fun overCapacityBatchDoesNotPartiallyWrite(){val root=Files.createTempDirectory("highlight-queue").toFile();try{val p=project();val one=HighlightWorkspace.snapshot(p,0);ExportQueue.add(root,one,true);assertThrows(IllegalArgumentException::class.java){ExportQueue.addBatch(root,p.drafts.indices.map{HighlightWorkspace.snapshot(p,it)},true)};assertEquals(1,ExportQueue.list(root).size)}finally{root.deleteRecursively()}}
    @Test fun snapshotIsDeterministicAndDoesNotCarryTimelineMusic(){val p=project();val a=HighlightWorkspace.snapshot(p,3);assertEquals(a,HighlightWorkspace.snapshot(p,3));assertNull(a.music);assertEquals(p.projectId,a.projectId);assertEquals(p.drafts.size,15)}
    @Test fun switchingHighlightsRetainsIndependentMusicAndEdits(){
        val root=Files.createTempDirectory("highlight-switch").toFile()
        try{
            val parent=project();ProjectCatalog.save(root,parent)
            val first=HighlightWorkspace.resolve(root,parent,0)
            val track=MusicTrack("file:///music.wav","My music",30000)
            val edited=first.copy(music=track,timelineClips=listOf(first.timelineClips.single().copy(hook="My opening",startMs=1000)))
            ProjectCatalog.save(root,edited)
            val second=HighlightWorkspace.resolve(root,edited,1);ProjectCatalog.save(root,second)
            assertNotEquals(first.projectId,second.projectId);assertNull(second.music)
            val reopened=HighlightWorkspace.resolve(root,second,0)
            assertEquals(track,reopened.music);assertEquals("My opening",reopened.timelineClips.single().hook)
            assertEquals(1000L,reopened.timelineClips.single().startMs)
            assertEquals(parent.projectId,reopened.highlightParentId)
            assertEquals(15,ProjectCatalog.read(root,parent.projectId).drafts.size)
            val output=HighlightWorkspace.snapshot(reopened,0)
            assertEquals(track,output.music);assertEquals(reopened.projectName,output.projectName)
            assertTrue(output.highlightParentId.isEmpty());assertEquals(1,output.timelineClips.size)
        }finally{root.deleteRecursively()}
    }
    @Test fun fifteenChildProjectsSurviveCatalogRestore(){
        val root=Files.createTempDirectory("highlight-restore").toFile()
        try{val parent=project();ProjectCatalog.save(root,parent)
            val children=parent.drafts.indices.map{HighlightWorkspace.resolve(root,parent,it).also{child->ProjectCatalog.save(root,child)}}
            assertEquals(15,children.map{it.projectId}.distinct().size)
            children.forEachIndexed{index,child->val saved=ProjectCatalog.read(root,child.projectId);assertEquals(index,saved.selectedDraft);assertEquals(15,saved.drafts.size);assertEquals(child.timelineClips,saved.timelineClips)}
        }finally{root.deleteRecursively()}
    }

}
