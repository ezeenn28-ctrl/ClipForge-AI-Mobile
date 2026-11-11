package com.clipforge.mobile

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong

class PcmPeak {
    private val bits=java.util.concurrent.atomic.AtomicInteger(0)
    fun offer(sample:Float){val value=abs(sample).let{if(it.isFinite())it.coerceIn(0f,1f) else 0f};while(true){val old=bits.get();if(value<=Float.fromBits(old))return;if(bits.compareAndSet(old,value.toBits()))return}}
    fun take(volume:Float,playing:Boolean):Float=Float.fromBits(bits.getAndSet(0))*if(playing)volume else 0f
}

/** Seek real video presentation timestamps; no assumed 30fps for variable-rate footage. */
object AdjacentVideoFrame {
    fun find(context:Context,uri:Uri,sourceMs:Long,direction:Int,fromMs:Long,untilMs:Long):Long? {
        require(direction==1||direction== -1)
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(context,uri,null)
            val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString("mime")?.startsWith("video/")==true}?:return null
            extractor.selectTrack(track)
            // Include the previous GOP when stepping back at a keyframe.
            extractor.seekTo((if(direction<0)(sourceMs-1).coerceAtLeast(0) else sourceMs)*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var best:Long?=null
            var count=0
            while(extractor.sampleTime>=0&&count++<10000){
                val time=(extractor.sampleTime/1000.0).roundToLong()
                if(time in fromMs until untilMs&&if(direction>0)time>sourceMs else time<sourceMs){
                    if(best==null||if(direction>0)time<best else time>best)best=time
                }
                if(time>sourceMs+1500)break
                if(!extractor.advance())break
            }
            return best
        } finally { extractor.release() }
    }
}
data class WavePeak(val sourceMs:Long,val amplitude:Float)
data class WaveformResult(val peaks:List<WavePeak>,val sampled:Boolean)
object EditorWaveform {
    fun key(clip:TimelineClip,primary:String)=clip.resolvedSource(primary)+":"+clip.startMs+":"+clip.endMs
    fun analyze(context:Context,clip:TimelineClip,primary:String,check:()->Unit={}):WaveformResult {
        val sampled=clip.sourceSpanMs>90000
        val starts=if(sampled)listOf(clip.startMs,clip.startMs+clip.sourceSpanMs/2-15000,clip.endMs-30000).distinct() else generateSequence(clip.startMs){it+30000}.takeWhile{it<clip.endMs}.toList()
        val peaks=mutableListOf<WavePeak>()
        for(start in starts){
            check();val end=minOf(start+30000,clip.endMs);val file=File.createTempFile("editor-wave-",".wav",context.cacheDir)
            try {
                OfflineAudioPreparer.extractWav(context,Uri.parse(clip.resolvedSource(primary)),file,start,end)
                RandomAccessFile(file,"r").use{input->
                    require(input.length()>=44){"No decoded audio"}
                    val header=ByteArray(44);input.readFully(header)
                    val rate=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(24);require(rate>0)
                    val frames=(input.length()-44)/2;val bucket=maxOf(1L,(frames+255)/256);var cursor=0L
                    while(cursor<frames){check();val size=minOf(bucket,frames-cursor).toInt();val bytes=ByteArray(size*2);input.readFully(bytes);val data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);var peak=0f;repeat(size){peak=maxOf(peak,abs(data.short/32768f))};peaks.add(WavePeak(start+cursor*1000/rate,peak));cursor+=size}
                }
            } finally { file.delete() }
        }
        return WaveformResult(peaks,sampled)
    }
}
