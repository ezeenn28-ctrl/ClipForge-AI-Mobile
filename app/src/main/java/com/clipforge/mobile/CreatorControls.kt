package com.clipforge.mobile

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import java.io.File

@Composable private fun <T> Choices(items:List<T>,selected:T,onChange:(T)->Unit,label:(T)->String){Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){items.forEach{item->FilterChip(item==selected,{onChange(item)},label={Text(label(item))})}}}
@Composable fun CreatorControls(clip:TimelineClip?,enabled:Boolean,onTemplate:(CreatorTemplate)->Unit,onBeat:()->Unit,onFraming:()->Unit,onClip:(TimelineClip)->Unit,onDictionary:(String,String)->Unit,onCorrect:()->Unit,onVersion:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    TextButton(onClick={open=!open}){Text(if(open)"Hide creator tools" else "Creator tools · templates, beats, framing")}
    if(open){
        Text("Templates · selected cut",style=MaterialTheme.typography.titleSmall)
        Text("Caption look, opening text, gentle motion and voice cleanup. Your footage, words and music stay in place.",style=MaterialTheme.typography.bodySmall)
        CreatorTemplate.entries.forEach{t->OutlinedButton(enabled=enabled&&clip!=null,onClick={onTemplate(t)}){Text("Review ${t.label} template")}}
        OutlinedButton(enabled=enabled&&clip!=null,onClick=onBeat){Text("Create beat-synced draft")}
        Text("Up to 12 cuts in the first 60s, subtle zoom cuts. Uses the music bed's detected or BPM-estimated beat grid.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=enabled&&clip!=null,onClick=onFraming){Text("Analyze subject framing")}
        clip?.let{c->
            c.editor.framing?.let{f->
                Text("Framing adjustment · selected cut")
                Text("Horizontal offset");Slider(f.offsetX,{onClip(c.copy(editor=c.editor.copy(framing=f.copy(offsetX=it))))},valueRange= -.5f.. .5f,enabled=enabled)
                Text("Vertical offset");Slider(f.offsetY,{onClip(c.copy(editor=c.editor.copy(framing=f.copy(offsetY=it))))},valueRange= -.5f.. .5f,enabled=enabled)
                Text("Zoom");Slider(f.zoom,{onClip(c.copy(editor=c.editor.copy(framing=f.copy(zoom=it))))},valueRange=1f..2f,enabled=enabled)
                TextButton(enabled=enabled,onClick={onClip(c.copy(editor=c.editor.copy(framing=null)))}){Text("Remove subject framing")}
            }
            var textOpen by remember{mutableStateOf(false)}
            TextButton(onClick={textOpen=!textOpen}){Text("Opening-text style and timing")}
            if(textOpen){
                val o=c.editor.opening
                fun change(next:OpeningStyle){if(enabled)onClip(c.copy(editor=c.editor.copy(opening=next)))}
                Choices(CaptionFont.entries.toList(),o.font,{change(o.copy(font=it))}){it.label}
                Choices(CaptionColor.entries.toList(),o.color,{change(o.copy(color=it))}){it.label}
                Row{Switch(o.background,{change(o.copy(background=it))},enabled=enabled);Text("Opening text background")}
                Choices(OpeningAnimation.entries.toList(),o.animation,{change(o.copy(animation=it))}){it.name}
                var from by remember(c.id,o.startMs){mutableStateOf((o.startMs/1000.0).toString())};var to by remember(c.id,o.endMs,c.durationMs){mutableStateOf((minOf(o.endMs,c.durationMs)/1000.0).toString())};var error by remember{mutableStateOf("")}
                OutlinedTextField(from,{from=it},label={Text("Show at output seconds")},enabled=enabled)
                OutlinedTextField(to,{to=it},label={Text("Hide at output seconds")},enabled=enabled)
                TextButton(enabled=enabled,onClick={runCatching{val a=(from.toDouble()*1000).toLong();val b=(to.toDouble()*1000).toLong();require(a>=0&&b>a&&b<=c.durationMs){"Use a valid interval inside this cut"};change(o.copy(startMs=a,endMs=b));error=""}.onFailure{error=it.message.orEmpty()}}){Text("Apply opening timing")}
                if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
            }
        }
        var wrong by remember{mutableStateOf("")};var right by remember{mutableStateOf("")}
        Text("Personal caption dictionary",style=MaterialTheme.typography.titleSmall)
        OutlinedTextField(wrong,{wrong=it},label={Text("Recognized word or phrase")},enabled=enabled)
        OutlinedTextField(right,{right=it},label={Text("Your correction · blank deletes rule")},enabled=enabled)
        TextButton(enabled=enabled&&wrong.isNotBlank(),onClick={onDictionary(wrong.trim(),right.trim())}){Text("Save correction")}
        TextButton(enabled=enabled&&clip!=null,onClick=onCorrect){Text("Apply dictionary to selected captions")}
        Text("Future transcription uses saved rules. Existing captions change only when you apply them.",style=MaterialTheme.typography.bodySmall)
        var version by remember{mutableStateOf("Alternative hook")}
        OutlinedTextField(version,{version=it.take(40)},label={Text("New clip version name")},enabled=enabled)
        OutlinedButton(enabled=enabled&&version.isNotBlank(),onClick={onVersion(version)}){Text("Create independent clip version")}
        Text("The original stays in Projects. Each version needs its own review and export approval.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun QualityControls(q:ExportQuality,enabled:Boolean,onChange:(ExportQuality)->Unit){
    Text("Export quality",style=MaterialTheme.typography.titleMedium)
    Choices(listOf(720,1080),q.height,{if(enabled)onChange(q.copy(height=it))}){"${it}p"}
    Choices(listOf(24,30,60),q.fps,{if(enabled)onChange(q.copy(fps=it))}){"Up to $it fps"}
    Choices(listOf(2_000_000,4_000_000,8_000_000,12_000_000,20_000_000),q.bitrate,{if(enabled)onChange(q.copy(bitrate=it))}){"${it/1_000_000} Mbps"}
    Text("Frame rate is a cap; lower-rate footage is not interpolated. Encoder may adjust bitrate to device support. File size is approximate.",style=MaterialTheme.typography.bodySmall)
}
data class CreatorDraft(val label:String,val before:EditorProject,val clips:List<TimelineClip>,val note:String="")
@Composable fun CreatorDraftDialog(draft:CreatorDraft,onDismiss:()->Unit,onApply:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var uri by remember{mutableStateOf<android.net.Uri?>(null)};var message by remember{mutableStateOf("Preview renders only the first 60 seconds privately. It does not save to Gallery.")};var running by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)}
    val player=remember{ExoPlayer.Builder(context).build()};val binding=remember(player){PreviewSurfaceBinding(player)}
    DisposableEffect(Unit){onDispose{job?.cancel();player.release();uri?.lastPathSegment?.let{name->context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.let{File(it,name).delete()}}}}
    LaunchedEffect(uri){uri?.let{player.setMediaItem(MediaItem.fromUri(it));player.prepare();player.playWhenReady=true}}
    Dialog(onDismissRequest={if(!running)onDismiss()}){Surface{Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())){
        Text(draft.label,style=MaterialTheme.typography.titleLarge);Text(draft.note);Text("${draft.before.timelineClips.size} → ${draft.clips.size} cuts · source media unchanged")
        if(uri!=null)PreviewVideo(binding,Modifier.fillMaxWidth().height(300.dp))
        Text(message,style=MaterialTheme.typography.bodySmall)
        Button(enabled=!running,onClick={running=true;job=scope.launch{try{
            var remaining=60_000L;val preview=draft.clips.mapNotNull{c->if(remaining<=0)null else {val n=minOf(c.durationMs,remaining);remaining-=n;c.trimmed(c.startMs,c.startMs+c.sourceOffsetMs(n))}}
            val p=draft.before.copy(timelineMode=true,timelineClips=preview,selectedTimelineId="",currentTimelineId="",exportQuality=ExportQuality(720,24,2_000_000),vertical=true,fillFrame=true)
            val result=QueueExporter.render(context,p,{message="Preparing draft preview · $it%"},previewOnly=true);uri=result.uri;message="Draft preview · first 60s maximum"
        }catch(e:CancellationException){throw e}catch(e:Exception){message="Preview failed: ${e.message}"}finally{running=false}}}){Text("Preview draft")}
        Row{TextButton(enabled=!running,onClick=onDismiss){Text("Discard")};Button(enabled=!running&&uri!=null,onClick=onApply){Text("Apply draft")}}
        if(running)TextButton(onClick={job?.cancel();running=false;message="Preview cancelled"}){Text("Cancel preview")}
    }}}
}
