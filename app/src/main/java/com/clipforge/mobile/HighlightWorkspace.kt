package com.clipforge.mobile

import java.security.MessageDigest

/** Shortlist entries keep stable edited cuts; revisiting one never appends a duplicate. */
object HighlightWorkspace {
    fun id(source:String,index:Int,draft:HighlightDraft):String {
        val key="$source|$index|${draft.startMs}|${draft.endMs}"
        return "highlight-"+MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(12).joinToString(""){"%02x".format(it)}
    }
    fun find(source:String,index:Int,draft:HighlightDraft,clips:List<TimelineClip>):TimelineClip? =
        clips.firstOrNull{it.id==id(source,index,draft)}?:clips.firstOrNull{it.resolvedSource(source)==source&&it.startMs==draft.startMs&&it.endMs==draft.endMs}
    fun cut(p:EditorProject,index:Int):TimelineClip {
        val d=p.drafts.getOrNull(index)?:error("Highlight is no longer in this shortlist")
        val existing=find(p.source,index,d,p.timelineClips)
        val look=CaptionLook(p.captionAppearance,p.subtitleSize,p.captionPreset,p.subtitleYellow)
        return (existing?:TimelineClip(id(p.source,index,d),d.startMs,d.endMs,hook=d.hook,caption=d.caption,
            captionStartMs=d.captionStartMs,captionEndMs=d.captionEndMs,effect=d.effect,
            subtitles=p.draftSegments[index]?:p.sourceSegments.filter{it.endMs>d.startMs&&it.startMs<d.endMs},
            source=p.source,sourceDurationMs=p.durationMs)).let{it.copy(id=id(p.source,index,d),editor=it.editor.copy(look=it.editor.look?:look))}
    }
    /** Caller serializes catalog changes with MetadataGate. Each highlight owns its full editor project. */
    fun resolve(root:java.io.File,current:EditorProject,index:Int):EditorProject {
        val parentId=current.highlightParentId.ifBlank{current.projectId}
        require(parentId.matches(Regex("[a-f0-9]{32}")))
        val parent=if(current.highlightParentId.isEmpty())current else ProjectCatalog.read(root,parentId)
        val d=parent.drafts.getOrNull(index)?:error("Highlight is no longer in this shortlist")
        val key="$parentId|${id(parent.source,index,d)}"
        val childId=MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(16).joinToString(""){"%02x".format(it)}
        if(current.projectId==childId)return current.reviewedAgain()
        val stored=java.io.File(root,"projects/catalog/$childId.properties")
        if(stored.isFile||ProjectStore.previousFile(stored).isFile)return ProjectCatalog.read(root,childId)
        val c=cut(parent,index)
        val legacy=find(parent.source,index,d,parent.timelineClips)
        return parent.reviewedAgain().copy(projectId=childId,projectName=(parent.projectName.take(55)+" · Highlight ${index+1}").take(80),highlightParentId=parentId,
            highlightEditing=true,selectedDraft=index,timelineMode=true,timelineClips=listOf(c),selectedTimelineId=c.id,currentTimelineId=c.id,
            startMs=c.startMs,endMs=c.endMs,hook=c.hook,caption=c.caption,effect=c.effect,currentSegments=c.subtitles,sourceSegments=emptyList(),
            draftSegments=if(c.subtitles.isEmpty())emptyMap() else mapOf(index to c.subtitles),subtitleDraftIndex=if(c.subtitles.isEmpty())-1 else index,
            music=parent.music.takeIf{legacy!=null&&parent.timelineClips.size==1},lastSpeechJobId="",lastSpeechUnits=0,
            autoAudioHandled=if(legacy==null)emptyList() else listOf(c.id)).also{it.validate()}
    }
    fun snapshot(p:EditorProject,index:Int):EditorProject {
        val c=cut(p,index)
        return p.reviewedAgain().copy(source=c.resolvedSource(p.source),durationMs=c.resolvedDuration(p.durationMs),startMs=c.startMs,endMs=c.endMs,
            projectId=p.projectId,projectName=if(p.highlightParentId.isNotEmpty())p.projectName else (p.projectName.take(55)+" · Highlight ${index+1}").take(80),
            mode="Manual Editor",drafts=emptyList(),selectedDraft= -1,draftSegments=emptyMap(),sourceSegments=emptyList(),currentSegments=c.subtitles,
            subtitleDraftIndex= -1,timelineClips=listOf(c),timelineMode=true,selectedTimelineId=c.id,currentTimelineId=c.id,
            highlightParentId="",highlightEditing=false,music=p.music.takeIf{p.timelineClips.size==1&&p.timelineClips.single().id==c.id},hook=c.hook,caption=c.caption,effect=c.effect,lastSpeechJobId="",lastSpeechUnits=0,autoAudioEnabled=false,autoAudioHandled=listOf(c.id)).also{it.validate()}
    }
}
