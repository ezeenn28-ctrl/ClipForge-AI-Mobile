package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties

object MusicCatalog {
    fun id(track:MusicTrack):String=track.libraryId.ifEmpty{java.security.MessageDigest.getInstance("SHA-256").digest(track.source.toByteArray()).joinToString(""){"%02x".format(it)}.take(32)}
    @Synchronized fun save(root:File,track:MusicTrack):MusicTrack{val key=id(track);val safe=track.copy(libraryId=key);safe.validate();val dir=File(root,"music-library").apply{mkdirs()};ProjectStore.write(File(dir,"$key.properties"),EditorProject("file:///music-template",1,0,1,music=safe,projectName=safe.name.take(80)));return safe}
    fun list(root:File):List<MusicTrack> = File(root,"music-library").listFiles().orEmpty().filter{it.name.matches(Regex("[A-Za-z0-9-]{1,80}\\.properties"))}.take(100).mapNotNull{runCatching{ProjectStore.read(it)?.music}.getOrNull()}
    fun favorites(root:File):Set<String> = runCatching{Properties().apply{File(root,"music-favorites.properties").takeIf(File::isFile)?.reader()?.use{load(it)}}.stringPropertyNames()}.getOrDefault(emptySet())
    @Synchronized fun toggle(root:File,track:MusicTrack){val keys=favorites(root).toMutableSet();if(!keys.add(id(track)))keys.remove(id(track));val file=File(root,"music-favorites.properties");val temp=File(file.path+".partial");try{temp.outputStream().use{out->Properties().apply{keys.forEach{setProperty(it,"true")}}.store(out,"Music favorites");out.fd.sync()};check(temp.renameTo(file))}finally{temp.delete()}}
}
data class AudioUndo(val before:List<TimelineClip>,val after:List<TimelineClip>,val beforeMusic:MusicTrack?,val afterMusic:MusicTrack?){
    fun restore(clips:List<TimelineClip>,music:MusicTrack?):Pair<List<TimelineClip>,MusicTrack?> = clips.map{clip->val old=before.firstOrNull{it.id==clip.id};val applied=after.firstOrNull{it.id==clip.id};if(old!=null&&applied!=null&&clip.source==applied.source&&clip.sourceDurationMs==applied.sourceDurationMs&&clip.sounds==applied.sounds&&clip.voiceGain==applied.voiceGain&&clip.audioProfile==applied.audioProfile)clip.copy(sounds=old.sounds,voiceGain=old.voiceGain,audioProfile=old.audioProfile,volume=if(clip.volume==applied.volume)old.volume else clip.volume) else clip} to if(music==afterMusic)beforeMusic else music
}
@Composable fun AutoAudioControls(auto:Boolean,enabled:Boolean,canUndo:Boolean,onAuto:(Boolean)->Unit,onGenerate:(AudioIntensity,MusicMood?,Boolean,Boolean)->Unit,onUndo:()->Unit){
    var level by remember{mutableStateOf(AudioIntensity.BALANCED)};var mood by remember{mutableStateOf<MusicMood?>(null)};var keepMusic by remember{mutableStateOf(true)};var keepSounds by remember{mutableStateOf(true)}
    StudioCard("Auto Audio","Audio activity, captions and cut boundaries guide a music/SFX draft. Mood is a local heuristic, not full scene understanding."){
        Row(verticalAlignment=Alignment.CenterVertically){Switch(auto,onAuto,enabled=enabled);Text("Auto Audio for new clips",Modifier.weight(1f))}
        Text("New imports default to Auto Audio. Existing projects remain manual until enabled. Nothing is exported automatically.",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())){AudioIntensity.entries.forEach{item->FilterChip(level==item,{level=item},enabled=enabled,label={Text(item.label)})}}
        Row(Modifier.horizontalScroll(rememberScrollState())){FilterChip(mood==null,{mood=null},enabled=enabled,label={Text("Auto mood")});MusicMood.entries.forEach{item->FilterChip(mood==item,{mood=item},enabled=enabled,label={Text(item.label)})}}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(keepMusic,{keepMusic=it},enabled=enabled);Text("Keep current music",Modifier.weight(1f))}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(keepSounds,{keepSounds=it},enabled=enabled);Text("Keep existing sound effects",Modifier.weight(1f))}
        Button(enabled=enabled,onClick={onGenerate(level,mood,keepMusic,keepSounds)}){Text("Generate Auto Audio draft")}
        TextButton(enabled=enabled&&canUndo,onClick=onUndo){Text("Undo Auto Audio")}
    }
}
@Composable fun MusicLibraryControls(root:File,enabled:Boolean,onUse:(MusicTrack)->Unit,onPreview:(MusicTrack)->Unit,onStop:()->Unit,onMessage:(String)->Unit){
    var open by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var favoritesOnly by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)};var favorites by remember{mutableStateOf(emptySet<String>())};var tracks by remember{mutableStateOf(emptyList<MusicTrack>())};var loading by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    LaunchedEffect(open,revision){if(open){loading=true;try{val data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){Triple(MusicMood.entries.map{MusicLibrary.ensure(root,it)}+MusicCatalog.list(root),MusicCatalog.favorites(root),Unit)};tracks=data.first;favorites=data.second}catch(error:Exception){onMessage(error.message?:"Music library unavailable")}finally{loading=false}}}
    DisposableEffect(open){onDispose{onStop()}}
    OutlinedButton(enabled=enabled,onClick={open=!open;if(!open)onStop()}){Text(if(open)"Close music library" else "Music library · Original + imported")}
    if(open)StudioCard("Background music","Original procedural instrumentals. Imported tracks keep their source description; verify reuse permission."){
        OutlinedTextField(query,{query=it.take(80)},enabled=enabled,label={Text("Search music")},modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Switch(favoritesOnly,{favoritesOnly=it});Text("Music favorites only")}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        tracks.filter{(query.isBlank()||it.name.contains(query,true)||it.mood.contains(query,true))&&(!favoritesOnly||MusicCatalog.id(it) in favorites)}.forEach{track->
            Text(track.name);Text(track.origin,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(enabled=enabled,onClick={onPreview(track)}){Text("Preview with clip")};TextButton(enabled=enabled,onClick={onUse(track)}){Text("Use music")};TextButton(enabled=enabled,onClick={scope.launch{try{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){MusicCatalog.toggle(root,track)};revision++}catch(error:Exception){onMessage(error.message?:"Favorite failed")}}}){Text(if(MusicCatalog.id(track) in favorites)"Unfavorite music" else "Favorite music")}}
        }
        TextButton(onClick=onStop){Text("Stop music preview")}
    }
}
@Composable fun AudioDiagnostics(clip:TimelineClip,enabled:Boolean,onAnalyze:()->Unit,onGain:(Float)->Unit,onSeek:(Long)->Unit,onRemove:(AudioRegion)->Unit){
    StudioCard("Dialogue and pauses","Activity is not guaranteed speech detection. Listen before removing any gap."){
        if(!clip.audioProfile.analyzed)OutlinedButton(enabled=enabled,onClick=onAnalyze){Text("Analyze original audio")} else {
            Text("RMS ${(clip.audioProfile.rms*100).toInt()}% · source peak ${(clip.audioProfile.peak*100).toInt()}% · voice ${"%.2f".format(java.util.Locale.ROOT,clip.voiceGain)}×")
            Row{OutlinedButton(enabled=enabled,onClick={onGain(clip.audioProfile.suggestedGain)}){Text("Level voice")};TextButton(enabled=enabled,onClick={onGain(1f)}){Text("Reset voice gain")}}
            Slider(clip.voiceGain,{onGain(it)},enabled=enabled,valueRange=.5f..4f)
            if(clip.audioProfile.peak*clip.voiceGain>.9f)Text("Voice limiter active; check music/SFX headroom.",style=MaterialTheme.typography.bodySmall)
            if(clip.audioProfile.sampled)Text("Long cut sampled at beginning/middle/end. Shorten cuts for silence suggestions; peak and gain are estimates.",style=MaterialTheme.typography.bodySmall)
            val gaps=AudioPlanning.silence(clip);Text("${gaps.size} possible silence gaps · no automatic removal")
            gaps.take(10).forEach{gap->Text("${Timecode.precise(gap.startMs)} — ${Timecode.precise(gap.endMs)}");Row{TextButton(enabled=enabled,onClick={onSeek(gap.startMs)}){Text("Listen to gap")};TextButton(enabled=enabled,onClick={onRemove(gap)}){Text("Remove this gap")}}}
        }
    }
}
