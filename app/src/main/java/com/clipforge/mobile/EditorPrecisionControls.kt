package com.clipforge.mobile

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.log10

@Composable fun PrecisionReviewControls(enabled:Boolean,positionMs:Long,loop:Boolean,comparison:Boolean,onFrame:(Int)->Unit,onLoop:()->Unit,onPreroll:()->Unit,onCompare:()->Unit){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics{contentDescription="Precision review controls"},horizontalArrangement=Arrangement.spacedBy(6.dp)){
        TextButton(enabled=enabled,onClick={onFrame(-1)}){Text("Previous frame")}
        Text(Timecode.precise(positionMs),Modifier.align(Alignment.CenterVertically).semantics{contentDescription="Frame position "+Timecode.precise(positionMs)})
        TextButton(enabled=enabled,onClick={onFrame(1)}){Text("Next frame")}
        FilterChip(loop,onLoop,enabled=enabled,label={Text("Loop selected cut")})
        TextButton(enabled=enabled,onClick=onPreroll){Text("Play 2s before edit")}
        FilterChip(comparison,onCompare,enabled=enabled,label={Text("Before / after")})
    }
}
@Composable fun EditorTrackControls(clip:TimelineClip,music:MusicTrack?,enabled:Boolean,onClip:(TimelineClip)->Unit,onMusic:(MusicTrack?)->Unit){
    var open by remember{mutableStateOf(false)}
    TextButton(onClick={open=!open}){Text(if(open)"Hide track controls" else "Track mute / lock")}
    if(open)StudioCard("Track controls · selected cut","Locks block changes to that cut's lane. Mute keeps media and timings for later. Music controls apply to the whole music bed."){
        val e=clip.editor
        Row(Modifier.horizontalScroll(rememberScrollState())){Text("Video",Modifier.align(Alignment.CenterVertically));FilterChip(e.videoLocked,{onClip(clip.copy(editor=e.copy(videoLocked=!e.videoLocked)))},enabled=enabled,label={Text(if(e.videoLocked)"Unlock video" else "Lock video")})}
        listOf("Captions","Voice","SFX").forEach{lane->
            val locked=when(lane){"Captions"->e.captionsLocked;"Voice"->e.voiceLocked;else->e.sfxLocked}
            val muted=when(lane){"Captions"->e.captionsMuted;"Voice"->e.voiceMuted;else->e.sfxMuted}
            Row(Modifier.horizontalScroll(rememberScrollState())){
                Text(lane,Modifier.align(Alignment.CenterVertically))
                FilterChip(muted,{onClip(clip.copy(editor=when(lane){"Captions"->e.copy(captionsMuted=!muted);"Voice"->e.copy(voiceMuted=!muted);else->e.copy(sfxMuted=!muted)}))},enabled=enabled,label={Text(if(muted)"Unmute $lane" else "Mute $lane")})
                FilterChip(locked,{onClip(clip.copy(editor=when(lane){"Captions"->e.copy(captionsLocked=!locked);"Voice"->e.copy(voiceLocked=!locked);else->e.copy(sfxLocked=!locked)}))},enabled=enabled,label={Text(if(locked)"Unlock $lane" else "Lock $lane")})
            }
        }
        music?.let{track->Row(Modifier.horizontalScroll(rememberScrollState())){
            Text("Music",Modifier.align(Alignment.CenterVertically))
            FilterChip(track.muted,{onMusic(track.copy(muted=!track.muted))},enabled=enabled,label={Text(if(track.muted)"Unmute music bed" else "Mute music bed")})
            FilterChip(track.locked,{onMusic(track.copy(locked=!track.locked))},enabled=enabled,label={Text(if(track.locked)"Unlock music bed" else "Lock music bed")})
        }}
    }
}
@Composable fun SettingsTransferControls(clipboard:SettingsClipboard?,enabled:Boolean,onCopy:()->Unit,onPaste:(Set<SettingsPart>,Boolean)->Unit,onReset:(SettingsPart)->Unit){
    var open by remember{mutableStateOf(false)};var parts by remember{mutableStateOf(setOf(SettingsPart.CAPTIONS))}
    TextButton(onClick={open=!open}){Text(if(open)"Hide copy / reset" else "Copy settings / reset selected")}
    if(open)StudioCard("Settings transfer","Caption styling, color, effects and voice settings only. Source media, trim, speech text and approval are never pasted."){
        Row(Modifier.horizontalScroll(rememberScrollState())){SettingsPart.entries.forEach{part->FilterChip(part in parts,{parts=if(part in parts)parts-part else parts+part},enabled=enabled,label={Text(part.label)})}}
        Button(enabled=enabled,onClick=onCopy){Text("Copy selected cut settings")}
        TextButton(enabled=enabled&&clipboard!=null&&parts.isNotEmpty(),onClick={onPaste(parts,false)}){Text("Paste settings to selected cut")}
        TextButton(enabled=enabled&&clipboard!=null&&parts.isNotEmpty(),onClick={onPaste(parts,true)}){Text("Paste settings to all cuts")}
        Text("Reset selected cut",style=MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState())){SettingsPart.entries.forEach{part->TextButton(enabled=enabled,onClick={onReset(part)}){Text("Reset ${part.label.lowercase()}")}}}
    }
}
@Composable fun PreviewLevelMeters(voice:Float,music:Float,sfx:Float){
    var open by remember{mutableStateOf(false)}
    TextButton(onClick={open=!open}){Text(if(open)"Hide audio meters" else "Audio output meters")}
    if(open){
        listOf("Voice" to voice,"Music" to music,"SFX" to sfx).forEach{(label,value)->
            val level=value.coerceIn(0f,1f);Text("$label · ${if(level<.00001f)"silent" else "%.1f dBFS".format(java.util.Locale.ROOT,20*log10(level))}",style=MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress={level},modifier=Modifier.fillMaxWidth(),color=if(level>.98f)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Text("Decoded PCM peaks after lane volume; separate players, not a summed master meter. Listen for combined headroom.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun DirectTextHandles(placement:TextPlacement,appearance:CaptionAppearance,hook:Boolean,enabled:Boolean,onDraft:(TextPlacement?)->Unit,onCommit:(TextPlacement)->Unit){
    var size by remember{mutableStateOf(androidx.compose.ui.unit.IntSize(1,1))}
    var draft by remember(placement,hook){mutableStateOf(placement)}
    val x=if(hook)draft.hookX else draft.x;val dy=if(hook)draft.hookY else draft.y
    val factor=if(hook)draft.hookScale else draft.scale
    val baseline=if(hook)if(appearance.layout==ReferenceLayout.STANDARD)210f/1280 else (appearance.layout.videoTop-100)/1280 else appearance.positionY
    fun change(dx:Float,deltaY:Float,resize:Float=1f){
        val nx=((if(hook)draft.hookX else draft.x)+dx).coerceIn(-.35f,.35f).let{if(abs(it)<.015f)0f else it}
        val ny=(baseline+(if(hook)draft.hookY else draft.y)+deltaY).coerceIn(if(hook).08f else .2f,.88f)-baseline
        val scale=((if(hook)draft.hookScale else draft.scale)*resize).coerceIn(.5f,2f)
        draft=if(hook)draft.copy(hookX=nx,hookY=ny,hookScale=scale) else draft.copy(x=nx,y=ny,scale=scale)
        onDraft(draft)
    }
    BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(9f/16f).onSizeChanged{size=it}){
        Canvas(Modifier.fillMaxSize()){
            drawLine(Color.Cyan.copy(alpha=.6f),Offset(this.size.width/2,0f),Offset(this.size.width/2,this.size.height),1.dp.toPx())
            drawRect(Color.Cyan.copy(alpha=.5f),Offset(this.size.width*.067f,this.size.height*.08f),androidx.compose.ui.geometry.Size(this.size.width*.866f,this.size.height*.80f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        val boxWidth=maxWidth*.8f;val boxHeight=maxHeight*.12f
        Box(Modifier.offset(x=maxWidth*(.1f+x),y=maxHeight*(baseline+dy-.055f)).width(boxWidth).height(boxHeight)
            .border(1.dp,Color.Cyan).semantics{contentDescription=if(hook)"Drag opening text" else "Drag caption text"}
            .pointerInput(enabled,hook,placement){if(enabled)detectDragGestures(onDragStart={draft=placement},onDragCancel={draft=placement;onDraft(null)},onDragEnd={onCommit(draft);onDraft(null)}){event,drag->event.consume();change(drag.x/size.width,drag.y/size.height)}}){
            Box(Modifier.align(Alignment.BottomEnd).size(28.dp).background(Color.Cyan).semantics{contentDescription=if(hook)"Resize opening text" else "Resize caption text"}
                .pointerInput(enabled,hook,placement){if(enabled)detectDragGestures(onDragStart={draft=placement},onDragCancel={draft=placement;onDraft(null)},onDragEnd={onCommit(draft);onDraft(null)}){event,drag->event.consume();change(0f,0f,1f+(drag.x+drag.y)/size.width)}})
        }
    }
}
@Composable fun Modifier.precisionPinch(enabled:Boolean,onPinch:(Float,Offset)->Unit):Modifier {
    val callback by rememberUpdatedState(onPinch)
    return pointerInput(enabled){if(enabled)awaitEachGesture{
        awaitFirstDown(requireUnconsumed=false)
        while(true){val event=awaitPointerEvent();if(event.changes.count{it.pressed}>=2){val factor=event.calculateZoom();if(factor.isFinite()&&factor>0f)callback(factor,event.calculateCentroid());event.changes.forEach{it.consume()}};if(event.changes.none{it.pressed})break}
    }}
}
