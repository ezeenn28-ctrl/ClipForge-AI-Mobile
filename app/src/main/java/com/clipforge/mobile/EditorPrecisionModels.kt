package com.clipforge.mobile

import java.util.Properties
import kotlin.math.abs

data class TextPlacement(val x:Float=0f,val y:Float=0f,val scale:Float=1f,val hookX:Float=0f,val hookY:Float=0f,val hookScale:Float=1f) {
    fun validate(){ require(listOf(x,y,hookX,hookY).all{it.isFinite()&&it in -1f..1f});require(scale.isFinite()&&scale in .5f..2f&&hookScale.isFinite()&&hookScale in .5f..2f) }
}
data class CaptionLook(val appearance:CaptionAppearance=CaptionAppearance(),val size:Float=39f,val preset:String="BOLD",val yellow:Boolean=false){
    fun validate(){appearance.validate();require(size.isFinite()&&size in 28f..55f&&preset in CaptionPreset.entries.map{it.name})}
}
data class ClipEditorState(val videoLocked:Boolean=false,val captionsLocked:Boolean=false,val voiceLocked:Boolean=false,val sfxLocked:Boolean=false,
    val captionsMuted:Boolean=false,val voiceMuted:Boolean=false,val sfxMuted:Boolean=false,val placement:TextPlacement=TextPlacement(),val look:CaptionLook?=null,val opening:OpeningStyle=OpeningStyle(),val framing:SubjectFraming?=null){
    fun validate(){placement.validate();look?.validate();opening.validate();framing?.validate()}
}
enum class SettingsPart(val label:String){CAPTIONS("Captions"),COLOR("Color"),EFFECTS("Effects"),AUDIO("Audio")}
data class SettingsClipboard(val clip:TimelineClip,val captionLook:CaptionLook){
    fun apply(target:TimelineClip,parts:Set<SettingsPart>):TimelineClip {
        var out=target
        if(SettingsPart.CAPTIONS in parts)out=out.copy(editor=out.editor.copy(placement=clip.editor.placement,look=clip.editor.look?:captionLook,opening=clip.editor.opening))
        if(SettingsPart.COLOR in parts)out=out.copy(color=clip.color)
        if(SettingsPart.EFFECTS in parts)out=out.copy(effect=clip.effect,visuals=clip.visuals.copy(fadeInMs=minOf(clip.visuals.fadeInMs,target.durationMs),fadeOutMs=minOf(clip.visuals.fadeOutMs,target.durationMs)),transition=clip.transition)
        if(SettingsPart.AUDIO in parts)out=out.copy(volume=clip.volume,voiceGain=clip.voiceGain,cleanupStrength=clip.cleanupStrength,fadeInMs=minOf(clip.fadeInMs,target.durationMs),fadeOutMs=minOf(clip.fadeOutMs,target.durationMs))
        return out
    }
}
object EditorPrecision {
    fun adjacentSample(samples:List<Long>,position:Long,direction:Int):Long? {
        require(direction==1||direction== -1)
        val times=samples.distinct().sorted()
        val nearest=times.minByOrNull{abs(it-position)}
        // Composition/source rounding at changed speed can put the clock 1ms beside its frame.
        val anchor=nearest?.takeIf{abs(it-position)<=2}?:position
        return if(direction>0)times.firstOrNull{it>anchor} else times.lastOrNull{it<anchor}
    }

    fun snapTargets(clips:List<TimelineClip>,music:MusicTrack?):List<Long> = (clips.indices.map{TimelineModel.offset(clips,it)}+TimelineModel.duration(clips)+TimelineModel.subtitles(clips).flatMap{listOf(it.startMs,it.endMs)}+SoundTimeline.events(clips).flatMap{listOf(it.startMs,it.endMs)}+music?.beatMarkers(TimelineModel.duration(clips)).orEmpty()).distinct().sorted()
    fun describe(before:List<TimelineClip>,after:List<TimelineClip>):String {
        if(after.size>before.size)return if(after.size==before.size+1&&after.any{n->before.any{it.copy(id=n.id)==n}})"Duplicate cut" else "Add / split cut"
        if(after.size<before.size)return "Remove cut"
        if(before.map{it.id}!=after.map{it.id})return "Reorder cuts"
        val pair=before.zip(after).firstOrNull{it.first!=it.second}?:return "Edit"
        val(a,b)=pair
        return when{a.startMs!=b.startMs||a.endMs!=b.endMs->"Trim";a.speed!=b.speed->"Speed";a.editor.placement!=b.editor.placement->"Text placement";a.editor.look!=b.editor.look->"Caption style";a.hook!=b.hook||a.caption!=b.caption||a.subtitles!=b.subtitles->"Caption text";a.sounds!=b.sounds->"Sound effects";a.color!=b.color->"Color";a.visuals!=b.visuals->"Motion";a.effect!=b.effect->"Video look";a.transition!=b.transition->"Transition";a.editor!=b.editor->"Track controls";else->"Audio"}
    }
    fun validateLocks(before:List<TimelineClip>,after:List<TimelineClip>){
        before.forEachIndexed{index,a->
            val b=after.firstOrNull{it.id==a.id}
            if(b==null){require(!a.editor.videoLocked&&!a.editor.captionsLocked&&!a.editor.voiceLocked&&!a.editor.sfxLocked){"Unlock this cut's tracks before removing it"};return@forEachIndexed}
            if(a.editor.videoLocked)require(a.editor.framing==b.editor.framing&&index==after.indexOf(b)&&a.copy(editor=b.editor,hook=b.hook,caption=b.caption,captionStartMs=b.captionStartMs,captionEndMs=b.captionEndMs,subtitles=b.subtitles,volume=b.volume,voiceGain=b.voiceGain,cleanupStrength=b.cleanupStrength,audioProfile=b.audioProfile,fadeInMs=b.fadeInMs,fadeOutMs=b.fadeOutMs,sounds=b.sounds)==b){"Video track is locked"}
            if(a.editor.captionsLocked)require(a.hook==b.hook&&a.caption==b.caption&&a.captionStartMs==b.captionStartMs&&a.captionEndMs==b.captionEndMs&&a.subtitles==b.subtitles&&a.editor.placement==b.editor.placement&&a.editor.look==b.editor.look&&a.editor.opening==b.editor.opening){"Captions track is locked"}
            if(a.editor.voiceLocked)require(a.volume==b.volume&&a.voiceGain==b.voiceGain&&a.cleanupStrength==b.cleanupStrength&&a.fadeInMs==b.fadeInMs&&a.fadeOutMs==b.fadeOutMs){"Voice track is locked"}
            if(a.editor.sfxLocked)require(a.sounds==b.sounds){"SFX track is locked"}
        }
    }
    fun reset(clip:TimelineClip,part:SettingsPart):TimelineClip=when(part){
        SettingsPart.CAPTIONS->clip.copy(editor=clip.editor.copy(placement=TextPlacement(),look=null,opening=OpeningStyle()))
        SettingsPart.COLOR->clip.copy(color=ColorAdjustments())
        SettingsPart.EFFECTS->clip.copy(effect="None",visuals=ClipVisuals(),transition=CutTransition())
        SettingsPart.AUDIO->clip.copy(volume=1f,voiceGain=1f,cleanupStrength=0f,fadeInMs=0,fadeOutMs=0)
    }
}
object EditorStateStore {
    fun write(p:Properties,key:String,s:ClipEditorState){
        fun put(k:String,v:Any){p.setProperty("$key.$k",v.toString())}
        put("videoLock",s.videoLocked);put("captionLock",s.captionsLocked);put("voiceLock",s.voiceLocked);put("sfxLock",s.sfxLocked)
        put("captionMute",s.captionsMuted);put("voiceMute",s.voiceMuted);put("sfxMute",s.sfxMuted)
        val t=s.placement;put("x",t.x);put("y",t.y);put("scale",t.scale);put("hx",t.hookX);put("hy",t.hookY);put("hs",t.hookScale)
        val o=s.opening;put("opening.font",o.font.name);put("opening.color",o.color.name);put("opening.bg",o.background);put("opening.animation",o.animation.name);put("opening.start",o.startMs);put("opening.end",o.endMs)
        s.framing?.let{f->put("framing",true);put("framing.x",f.offsetX);put("framing.y",f.offsetY);put("framing.zoom",f.zoom);put("framing.points",f.points.joinToString(";"){"${it.ms},${it.x},${it.y}"})}
        put("look",s.look!=null);s.look?.let{l->put("size",l.size);put("preset",l.preset);put("yellow",l.yellow);val a=l.appearance;put("font",a.font.name);put("py",a.positionY);put("color",a.color.name);put("highlight",a.highlight.name);put("align",a.alignment.name);put("layout",a.layout.name);put("words",a.wordHighlight)}
    }
    fun read(p:Properties,key:String):ClipEditorState{
        fun b(k:String)=p.getProperty("$key.$k","false").toBooleanStrict()
        fun f(k:String,d:String="0")=p.getProperty("$key.$k",d).toFloat()
        val look=if(b("look"))CaptionLook(CaptionAppearance(CaptionFont.valueOf(p.getProperty("$key.font","SANS")),f("py",(970f/1280).toString()),CaptionColor.valueOf(p.getProperty("$key.color","INHERIT")),CaptionColor.valueOf(p.getProperty("$key.highlight","YELLOW")),CaptionAlignment.valueOf(p.getProperty("$key.align","CENTER")),ReferenceLayout.valueOf(p.getProperty("$key.layout","STANDARD")),b("words")),f("size","39"),p.getProperty("$key.preset","BOLD"),b("yellow")) else null
        val opening=OpeningStyle(CaptionFont.valueOf(p.getProperty("$key.opening.font","SANS")),CaptionColor.valueOf(p.getProperty("$key.opening.color","INHERIT")),b("opening.bg"),OpeningAnimation.valueOf(p.getProperty("$key.opening.animation","NONE")),p.getProperty("$key.opening.start","0").toLong(),p.getProperty("$key.opening.end","21600000").toLong())
        val framing=if(b("framing"))SubjectFraming(p.getProperty("$key.framing.points","").split(";").filter{it.isNotEmpty()}.map{val a=it.split(",");require(a.size==3);FramingPoint(a[0].toLong(),a[1].toFloat(),a[2].toFloat())},f("framing.x"),f("framing.y"),f("framing.zoom","1")) else null
        return ClipEditorState(b("videoLock"),b("captionLock"),b("voiceLock"),b("sfxLock"),b("captionMute"),b("voiceMute"),b("sfxMute"),TextPlacement(f("x"),f("y"),f("scale","1"),f("hx"),f("hy"),f("hs","1")),look,opening,framing).also{it.validate()}
    }
}
