package com.clipforge.mobile

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import java.io.File

enum class AudioPage(val label:String){MUSIC("Music"),SFX("SFX"),VOICE("Voice"),REVIEW("Review")}
@Composable fun AudioPageTabs(selected:AudioPage,onSelect:(AudioPage)->Unit){
    Row(Modifier.horizontalScroll(rememberScrollState())){AudioPage.entries.forEach{page->FilterChip(selected==page,{onSelect(page)},label={Text(page.label)})}}
}
@Composable fun AudioReviewControls(clips:List<TimelineClip>,music:MusicTrack?,enabled:Boolean,onMusic:(MusicTrack?)->Unit,onChange:(List<TimelineClip>)->Unit,onReplaceMusic:()->Unit,onMessage:(String)->Unit){
    var openClip by remember{mutableStateOf("")}
    StudioCard("Review audio draft","Keep, replace or remove each item. These choices never authorize an export."){
        music?.let{track->Text(track.name);Text(if(track.libraryId.startsWith("original-"))"Original instrumental. Current caption-based mood: ${AudioPlanning.mood(clips).label}; without useful words the fallback is Chill." else track.origin)
            Row{TextButton(enabled=enabled,onClick={onMessage("Music kept in this draft. Export still needs approval.")}){Text("Keep music")};TextButton(enabled=enabled,onClick=onReplaceMusic){Text("Replace music")};TextButton(enabled=enabled,onClick={onMusic(null)}){Text("Remove music")}}}
        clips.forEachIndexed{index,clip->
            TextButton(onClick={openClip=if(openClip==clip.id)"" else clip.id}){Text("Clip ${index+1} · ${clip.sounds.size} sound cues")}
            if(openClip==clip.id)clip.sounds.forEach{cue->
                Text("${cue.kind.name.lowercase()} · ${Timecode.precise(clip.outputOffsetMs(cue.sourceMs-clip.startMs))} into cut · ${(cue.volume*100).toInt()}%")
                Text(when{cue.asset!=null->"Imported sound; ${cue.asset.origin}.";cue.sourceMs==clip.startMs->"Opening cut accent.";else->"Timed accent near a caption or visual-change anchor; listen for context."},style=MaterialTheme.typography.bodySmall)
                Row{
                    TextButton(enabled=enabled,onClick={onMessage("Sound kept in draft. Review its timing in SFX.")}){Text("Keep cue")}
                    TextButton(enabled=enabled,onClick={val kind=SoundKind.entries[(cue.kind.ordinal+1)%SoundKind.entries.size];onChange(clips.map{c->if(c.id==clip.id)c.copy(sounds=c.sounds.map{if(it.id==cue.id)it.copy(kind=kind,asset=null) else it}) else c})}){Text("Replace cue")}
                    TextButton(enabled=enabled,onClick={onChange(clips.map{c->if(c.id==clip.id)c.copy(sounds=c.sounds.filterNot{it.id==cue.id}) else c})}){Text("Remove cue")}
                }
            }
        }
    }
}
object CampaignPresets {
    private fun path(root:File,id:String):File{require(id.matches(Regex("[a-f0-9]{32}")));return File(root,"campaign-presets/$id.properties")}
    fun list(root:File)=File(root,"campaign-presets").listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{32}\\.properties"))}.sortedByDescending{it.lastModified()}.take(30).mapNotNull{runCatching{ProjectStore.read(it)}.getOrNull()}
    @Synchronized fun save(root:File,name:String,project:EditorProject):EditorProject{
        require(list(root).size<30){"Up to 30 campaign presets"};val clean=name.trim().filterNot(Char::isISOControl).take(60);require(clean.isNotEmpty()){ "Name this campaign preset" }
        val preset=EditorProject("file:///campaign-template",1,0,1,vertical=project.vertical,fillFrame=project.fillFrame,subtitleSize=project.subtitleSize,subtitleYellow=project.subtitleYellow,captionPreset=project.captionPreset,captionAppearance=project.captionAppearance,hookTone=project.hookTone,campaignBrief=project.campaignBrief,audioIntensity=project.audioIntensity,projectId=ProjectCatalog.newId(),projectName=clean)
        ProjectStore.write(path(root,preset.projectId),preset);return preset
    }
    @Synchronized fun remove(root:File,id:String){val f=path(root,id);f.delete();ProjectStore.previousFile(f).delete()}
}
@Composable fun CampaignPresetControls(root:File,project:EditorProject?,enabled:Boolean,onApply:(EditorProject)->Unit,onMessage:(String)->Unit){
    var presets by remember{mutableStateOf(emptyList<EditorProject>())};var name by remember{mutableStateOf("")};var revision by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
    LaunchedEffect(revision){presets=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){CampaignPresets.list(root)}}
    StudioCard("Campaign presets","Save framing, captions, tags, duration rules and audio intensity. Rights/source confirmations and export approval are always cleared."){
        OutlinedTextField(name,{name=it.take(60)},enabled=enabled,label={Text("Campaign preset name")})
        Button(enabled=enabled&&project!=null&&name.isNotBlank(),onClick={scope.launch{try{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){CampaignPresets.save(root,name,project!!)};revision++;onMessage("Campaign preset saved")}catch(e:Exception){onMessage(e.message.orEmpty())}}}){Text("Save campaign preset")}
        presets.forEach{p->Row{TextButton(enabled=enabled,onClick={onApply(p)}){Text("Apply ${p.projectName}")};TextButton(enabled=enabled,onClick={scope.launch{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){CampaignPresets.remove(root,p.projectId)};revision++}}){Text("Delete preset")}}}
    }
}
enum class PreviewDetail(val label:String,val bitmapScale:Float){LIGHT("Light",.25f),BALANCED("Balanced",.5f),SHARP("Sharp",.75f)}
data class DeviceSnapshot(val freeBytes:Long,val ramBytes:Long,val battery:Int,val charging:Boolean){
    fun report(version:String,detail:PreviewDetail,error:String):String="ClipForge diagnostic\nVersion: $version\nAndroid API: ${Build.VERSION.SDK_INT}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nPreview: ${detail.label}\nFree storage MB: ${freeBytes/1048576}\nFree RAM MB: ${ramBytes/1048576}\nBattery: $battery%; charging: $charging\nPlayback error code: ${Regex("ERROR_CODE_[A-Z0-9_]{1,60}").find(error)?.value?:"none"}\nNo project names, paths, transcript or media included."
}
object DeviceHealth{
    fun read(context:Context):DeviceSnapshot{
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1;val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
        val charging=(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)!=0
        val memory=ActivityManager.MemoryInfo();(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return DeviceSnapshot(StatFs(context.filesDir.path).availableBytes,memory.availMem,if(level<0)-1 else level*100/scale.coerceAtLeast(1),charging)
    }
}
@Composable fun PerformanceControls(context:Context,detail:PreviewDetail,enabled:Boolean,durationMs:Long,error:String,onDetail:(PreviewDetail)->Unit){
    var open by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)};val health=remember(revision,open){DeviceHealth.read(context)}
    TextButton(onClick={open=!open}){Text("Device performance and diagnostics")}
    if(open)StudioCard("Preview detail","Light reduces overlay resolution and preview size. Source-video decoding resolution stays unchanged; MP4 export stays at the selected output quality."){
        Row{PreviewDetail.entries.forEach{d->FilterChip(detail==d,{onDetail(d)},enabled=enabled,label={Text(d.label)})}}
        Text("Free storage ${health.freeBytes/1048576} MB · free RAM ${health.ramBytes/1048576} MB · battery ${health.battery}%")
        if(health.freeBytes<EditingChecks.estimateBytes(durationMs))Text("Storage may be insufficient for this export.",color=MaterialTheme.colorScheme.error)
        if(health.battery in 0..19&&!health.charging)Text("Low battery: connect power before long processing.",color=MaterialTheme.colorScheme.error)
        TextButton(onClick={revision++}){Text("Refresh device status")}
        TextButton(enabled=enabled,onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,health.report(BuildConfig.VERSION_NAME,detail,error)),"Share ClipForge diagnostic"))}){Text("Share privacy-safe diagnostic")}
    }
}
