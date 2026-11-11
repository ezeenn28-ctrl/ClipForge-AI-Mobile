package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class AudioRegion(val startMs:Long,val endMs:Long){fun validate(duration:Long){require(startMs>=0&&endMs>startMs&&endMs<=duration)}}
data class ClipAudioProfile(val analyzed:Boolean=false,val rms:Float=0f,val peak:Float=0f,val activity:List<AudioRegion> = emptyList()){
    fun validate(duration:Long){require(rms.isFinite()&&peak.isFinite()&&rms in 0f..1f&&peak in 0f..1f&&activity.size<=2000);activity.forEach{it.validate(duration)}}
    val suggestedGain:Float get()=if(rms<.003f)1f else (.11f/rms).coerceIn(.5f,4f)
}
enum class AudioIntensity(val label:String,val cues:Int){MINIMAL("Minimal",1),BALANCED("Balanced",3),ENERGETIC("Energetic",5)}
enum class MusicMood(val label:String,val bpm:Int,val minor:Boolean){FUNNY("Funny",112,false),SUSPENSE("Suspense",88,true),EMOTIONAL("Emotional",72,true),ROMANCE("Romance",84,false),CINEMATIC("Cinematic",96,true),GAMING("Gaming",124,true),CHILL("Chill",80,false)}
object AudioPlanning {
    fun mood(clips:List<TimelineClip>):MusicMood {
        val text=clips.joinToString(" "){it.hook+" "+it.caption+" "+it.subtitles.joinToString(" "){c->c.text}}.lowercase(java.util.Locale.ROOT)
        fun has(vararg words:String)=words.any{Regex("(?<![\\p{L}])"+Regex.escape(it)+"(?![\\p{L}])").containsMatchIn(text)}
        return when{has("love","mahal","kiss","kilig","romance")->MusicMood.ROMANCE;has("sad","cry","iyak","sorry","lungkot")->MusicMood.EMOTIONAL;has("danger","secret","takot","mystery","scary")->MusicMood.SUSPENSE;has("game","gaming","rank","boss","gg")->MusicMood.GAMING;has("haha","funny","joke","tawa","loko")->MusicMood.FUNNY;has("fight","battle","epic","laban","hero")->MusicMood.CINEMATIC;else->MusicMood.CHILL}
    }
    fun merge(regions:List<AudioRegion>):List<AudioRegion>{val out=mutableListOf<AudioRegion>();regions.sortedBy{it.startMs}.forEach{r->val last=out.lastOrNull();if(last!=null&&r.startMs<=last.endMs+120)out[out.lastIndex]=AudioRegion(last.startMs,maxOf(last.endMs,r.endMs)) else out.add(r)};return out}
    fun activity(clips:List<TimelineClip>):List<AudioRegion> = merge(clips.flatMapIndexed{index,clip->
        if(clip.volume<=.001f)return@flatMapIndexed emptyList<AudioRegion>()
        val offset=TimelineModel.offset(clips,index)
        val regions=clip.audioProfile.activity+clip.subtitles.map{AudioRegion(it.startMs,it.endMs)}
        regions.mapNotNull{r->val a=maxOf(r.startMs,clip.startMs);val b=minOf(r.endMs,clip.endMs);if(b<=a)null else AudioRegion(offset+clip.outputOffsetMs(a-clip.startMs),offset+clip.outputOffsetMs(b-clip.startMs)).takeIf{it.endMs>it.startMs}}
    })
    fun duck(ms:Long,regions:List<AudioRegion>,floor:Float):Float{var active=0f;for(r in regions){if(ms in r.startMs until r.endMs)return floor;if(ms in r.startMs-120 until r.startMs)active=maxOf(active,1f-(r.startMs-ms)/120f);if(ms in r.endMs until r.endMs+350)active=maxOf(active,1f-(ms-r.endMs)/350f)};return 1f-active*(1f-floor)}
    fun sounds(clip:TimelineClip,level:AudioIntensity,mood:MusicMood,keep:Boolean):List<ClipSound>{
        val anchors=mutableListOf(clip.startMs);clip.subtitles.filter{it.startMs in clip.startMs until clip.endMs}.forEach{cue->if(cue.text.trim().lastOrNull() in listOf('!','?')||Regex("(?i)\\b(wait|why|bakit|grabe|wow)\\b").containsMatchIn(cue.text))anchors.add((cue.endMs+80).coerceAtMost(clip.endMs-1))}
        if(level==AudioIntensity.ENERGETIC&&anchors.size<3)anchors.add(clip.startMs+clip.sourceSpanMs/2)
        val old=if(keep)clip.sounds else emptyList();val added=mutableListOf<ClipSound>()
        for(anchor in anchors.distinct().sorted()){if(added.size>=level.cues||old.size+added.size>=12)break;if((old+added).any{abs(it.sourceMs-anchor)/clip.speed<2500})continue
            val speaking=(clip.audioProfile.activity+clip.subtitles.map{AudioRegion(it.startMs,it.endMs)}).any{anchor in it.startMs until it.endMs}
            val kind=if(added.isEmpty()&&anchor==clip.startMs)SoundKind.WHOOSH else when(mood){MusicMood.FUNNY->SoundKind.POP;MusicMood.ROMANCE->SoundKind.CHIME;MusicMood.SUSPENSE->SoundKind.HEARTBEAT;MusicMood.GAMING->SoundKind.CLICK;else->SoundKind.IMPACT}
            added.add(ClipSound(java.util.UUID.randomUUID().toString(),anchor,kind,if(speaking).10f else if(level==AudioIntensity.ENERGETIC).22f else .16f))};return old+added
    }
    fun silence(clip:TimelineClip):List<AudioRegion>{if(!clip.audioProfile.analyzed)return emptyList();var cursor=clip.startMs;val out=mutableListOf<AudioRegion>();for(r in merge(clip.audioProfile.activity)){val a=r.startMs.coerceIn(clip.startMs,clip.endMs);val b=r.endMs.coerceIn(clip.startMs,clip.endMs);if(a-cursor>=700)out.add(AudioRegion(cursor,a));cursor=maxOf(cursor,b)};if(clip.endMs-cursor>=700)out.add(AudioRegion(cursor,clip.endMs));return out}
}
class AudioActivityCache{private var previous:List<TimelineClip>?=null;private var regions=emptyList<AudioRegion>();fun get(clips:List<TimelineClip>):List<AudioRegion>{if(previous!==clips){regions=AudioPlanning.activity(clips);previous=clips};return regions}}
object VoiceDsp{fun sample(input:Float,gain:Float):Float{if(!input.isFinite())return 0f;if(gain==1f)return input.coerceIn(-1f,1f);val x=input*gain;return if(abs(x)<=.9f)x else sign(x)*(.9f+.08f*tanh((abs(x)-.9f)/.08f))}}
object AudioInspection {
    fun wave(file:File,sourceStartMs:Long=0):ClipAudioProfile{RandomAccessFile(file,"r").use{input->
        val h=ByteArray(44);input.readFully(h);val header=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);require(String(h,0,4)=="RIFF"&&String(h,8,4)=="WAVE"&&header.getShort(22).toInt()==1&&header.getShort(34).toInt()==16)
        val rate=header.getInt(24);require(rate in 8000..192000);val frames=(input.length()-44)/2;require(frames>0)
        val block=rate/20;val bins=mutableListOf<Pair<Long,Float>>();var sum=0.0;var peak=0f;var position=0L
        while(position<frames){val n=minOf(block.toLong(),frames-position).toInt();val bytes=ByteArray(n*2);input.readFully(bytes);val data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);var energy=0.0;repeat(n){val x=data.short/32768f;energy+=x*x;peak=maxOf(peak,abs(x))};sum+=energy;bins.add(position*1000/rate to sqrt(energy/n).toFloat());position+=n}
        val floor=bins.map{it.second}.sorted().let{it[it.size/5]};val threshold=(floor*2.2f).coerceIn(.004f,.012f)
        val regions=bins.filter{it.second>=threshold}.map{AudioRegion(sourceStartMs+it.first,sourceStartMs+minOf(frames*1000/rate,it.first+50))}.filter{it.endMs>it.startMs}
        return ClipAudioProfile(true,sqrt(sum/frames).toFloat(),peak,AudioPlanning.merge(regions))}}
    fun beats(file:File,offsetMs:Long):List<Long>{RandomAccessFile(file,"r").use{input->val h=ByteArray(44);input.readFully(h);val header=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);val rate=header.getInt(24);require(rate in 8000..192000&&header.getShort(22).toInt()==1);val frames=(input.length()-44)/2;var cursor=0L;var previous=0.0;var last=-1000L;val out=mutableListOf<Long>();while(cursor<frames){val n=minOf((rate/50).toLong(),frames-cursor).toInt();val bytes=ByteArray(n*2);input.readFully(bytes);val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);var energy=0.0;repeat(n){val x=b.short/32768.0;energy+=x*x};val rms=sqrt(energy/n);val t=cursor*1000/rate;if(rms>.018&&rms>previous*1.65&&t-last>=250){out.add(offsetMs+t);last=t};previous=.6*previous+.4*rms;cursor+=n};return out.take(500)}}
    fun inspect(context:Context,clip:TimelineClip,primary:String,check:()->Unit={}):ClipAudioProfile{
        val uri=Uri.parse(clip.resolvedSource(primary));val meta=MediaMetadataRetriever();val audio=try{meta.setDataSource(context,uri);meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"}finally{meta.release()};if(!audio)return ClipAudioProfile(analyzed=true)
        val profiles=mutableListOf<Pair<ClipAudioProfile,Long>>();var cursor=clip.startMs
        while(cursor<clip.endMs){check();val end=minOf(cursor+30000,clip.endMs);val temp=File.createTempFile("audio-inspect-",".wav",context.cacheDir);try{OfflineAudioPreparer.extractWav(context,uri,temp,cursor,end);profiles.add(wave(temp,cursor) to end-cursor)}finally{temp.delete()};cursor=end}
        val duration=profiles.sumOf{it.second}.coerceAtLeast(1);val regions=AudioPlanning.merge(profiles.flatMap{it.first.activity});require(regions.size<=2000){"Analyze shorter cuts: too many audio activity regions"}
        return ClipAudioProfile(true,sqrt(profiles.sumOf{it.first.rms.toDouble().pow(2)*it.second}/duration).toFloat(),profiles.maxOfOrNull{it.first.peak}?:0f,regions)
    }
}
