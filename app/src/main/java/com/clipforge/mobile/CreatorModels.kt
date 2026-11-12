package com.clipforge.mobile

import java.io.File
import java.util.Properties
import java.util.Locale
import kotlin.math.roundToLong

enum class OpeningAnimation { NONE, FADE, POP }
data class OpeningStyle(val font:CaptionFont=CaptionFont.SANS,val color:CaptionColor=CaptionColor.INHERIT,val background:Boolean=false,
    val animation:OpeningAnimation=OpeningAnimation.NONE,val startMs:Long=0,val endMs:Long=21_600_000) {
    fun validate(){require(startMs in 0..21_599_999 && endMs in (startMs+1)..21_600_000)}
    fun alpha(ms:Long):Float=if(ms !in startMs until endMs)0f else if(animation==OpeningAnimation.FADE)minOf(1f,(ms-startMs)/200f,(endMs-ms)/200f).coerceIn(0f,1f) else 1f
    fun scale(ms:Long):Float=if(animation==OpeningAnimation.POP) .8f+.2f*((ms-startMs)/180f).coerceIn(0f,1f) else 1f
}
data class FramingPoint(val ms:Long,val x:Float,val y:Float)
data class SubjectFraming(val points:List<FramingPoint> = emptyList(),val offsetX:Float=0f,val offsetY:Float=0f,val zoom:Float=1f) {
    fun validate(){require(points.size<=121 && points.zipWithNext().all{it.first.ms<it.second.ms});require(points.all{it.ms>=0 && it.x.isFinite()&&it.x in 0f..1f && it.y.isFinite()&&it.y in 0f..1f});require(offsetX.isFinite()&&offsetX in -.5f.. .5f&&offsetY.isFinite()&&offsetY in -.5f.. .5f&&zoom.isFinite()&&zoom in 1f..2f)}
    fun center(ms:Long):Pair<Float,Float>{
        val right=points.indexOfFirst{it.ms>=ms}
        val a=when{points.isEmpty()->FramingPoint(0,.5f,.5f);right<0->points.last();right==0->points.first();else->points[right-1]}
        val b=if(right>0)points[right] else a
        val t=if(a.ms==b.ms)0f else ((ms-a.ms).toFloat()/(b.ms-a.ms)).coerceIn(0f,1f)
        return (a.x+(b.x-a.x)*t+offsetX).coerceIn(0f,1f) to (a.y+(b.y-a.y)*t+offsetY).coerceIn(0f,1f)
    }
}
data class ExportQuality(val height:Int=1080,val fps:Int=30,val bitrate:Int=8_000_000){
    fun validate(){require(height in listOf(720,1080)&&fps in listOf(24,30,60)&&bitrate in 2_000_000..20_000_000)}
    fun estimate(ms:Long):Long=((bitrate+192_000L)*ms/8000*1.06).roundToLong()
}
enum class CreatorTemplate(val label:String,val preset:CaptionPreset,val color:CaptionColor,val cleanup:Float,val zoom:Float){
    ANIME("Anime",CaptionPreset.COMIC,CaptionColor.CYAN,0f,1.08f), ROMANCE("Romance",CaptionPreset.EDITORIAL,CaptionColor.PINK,.15f,1.03f),
    PODCAST("Podcast",CaptionPreset.CAPSULE,CaptionColor.YELLOW,.5f,1f), GAMING("Gaming",CaptionPreset.NEON,CaptionColor.GREEN,.25f,1.05f), MUSIC("Music",CaptionPreset.OUTLINE,CaptionColor.LAVENDER,0f,1.04f);
    fun apply(c:TimelineClip):TimelineClip=c.copy(editor=c.editor.copy(look=CaptionLook(CaptionAppearance(highlight=color,wordHighlight=true),39f,preset.name),opening=OpeningStyle(color=color,animation=OpeningAnimation.FADE,endMs=minOf(3000,c.durationMs))),
        visuals=c.visuals.copy(start=MotionPoint(),end=MotionPoint(zoom)),cleanupStrength=cleanup,volume=1f,voiceGain=1f)
}
object BeatDraft {
    fun create(clips:List<TimelineClip>,music:MusicTrack):List<TimelineClip>{
        require(!music.muted){"Unmute the music bed before making a beat draft"}
        require(clips.none{it.editor.videoLocked||it.editor.captionsLocked||it.editor.voiceLocked||it.editor.sfxLocked}){"Unlock the cut tracks before creating a beat draft"}
        val total=TimelineModel.duration(clips);var out=clips;var last= -1500L;var count=0
        for(ms in music.beatMarkers(minOf(total,60_000))) {
            if(ms-last<1500||count>=12||out.size>=60)continue
            val p=TimelineModel.position(out,ms)?:continue
            if(p.localMs<350||out[p.index].durationMs-p.localMs<350)continue
            out=TimelineModel.split(out,ms);last=ms;count++
        }
        require(count>0){"No usable beats inside the first 60 seconds; check music timing or BPM"}
        val originalIds=clips.map{it.id}.toSet();var accents=clips.sumOf{it.sounds.size}
        return out.mapIndexed{i,c->
            val added=c.id !in originalIds
            val nextAdded=out.getOrNull(i+1)?.id?.let{it !in originalIds}==true
            val sounds=if(added&&c.sounds.isEmpty()&&i%3==1&&accents<120){accents++;listOf(ClipSound(java.util.UUID.randomUUID().toString(),c.startMs,SoundKind.SWISH,.16f))}else c.sounds
            c.copy(visuals=if(added)c.visuals.copy(end=MotionPoint(1.06f)) else c.visuals,transition=if(nextAdded)CutTransition(CutTransitionKind.ZOOM_CUT,240) else c.transition,sounds=sounds)
        }
    }
}
/** Literal, longest-first and single-pass: replacement text is never processed again. */
object CaptionDictionary {
    private fun file(root:File)=File(root,"caption-dictionary.properties")
    fun validate(entries:Map<String,String>){require(entries.size<=200);entries.forEach{(a,b)->require(a.isNotBlank()&&a.length<=80&&b.isNotBlank()&&b.length<=100&&!(a+b).any{it.isISOControl()})}}
    fun read(root:File):Map<String,String>{val p=Properties();file(root).takeIf{it.isFile}?.reader(Charsets.UTF_8)?.use{p.load(it)};return p.stringPropertyNames().associateWith{p.getProperty(it)}.also(::validate)}
    fun write(root:File,entries:Map<String,String>){validate(entries);val target=file(root);val temp=File(target.path+".partial");try{temp.outputStream().use{stream->val p=Properties();p.putAll(entries);p.store(stream,"Personal caption corrections");stream.fd.sync()};check(temp.renameTo(target))}finally{temp.delete()}}
    fun correct(text:String,entries:Map<String,String>):String {
        validate(entries);if(entries.isEmpty())return text
        val lookup=entries.entries.associate{it.key.lowercase(Locale.ROOT) to it.value}
        val regex=Regex("(?<![\\p{L}\\p{N}_])(?:"+entries.keys.sortedByDescending{it.length}.joinToString("|"){Regex.escape(it)}+")(?![\\p{L}\\p{N}_])",RegexOption.IGNORE_CASE)
        return regex.replace(text){lookup[it.value.lowercase(Locale.ROOT)]?:it.value}
    }
    fun apply(c:TimelineClip,entries:Map<String,String>)=c.copy(caption=correct(c.caption,entries),subtitles=c.subtitles.map{it.copy(text=correct(it.text,entries))})
}
object ClipVersions {
    /** Caller holds MetadataGate: persist original first, then create an independent unapproved project. */
    fun create(root:File,project:EditorProject,label:String):EditorProject {
        require(label.isNotBlank()&&label.length<=40&&!label.any{it.isISOControl()})
        ProjectCatalog.save(root,project)
        return project.reviewedAgain().copy(highlightParentId="",projectId=ProjectCatalog.newId(),projectName=(project.projectName.take(36)+" · "+label).take(80)).also{ProjectCatalog.save(root,it)}
    }
}
