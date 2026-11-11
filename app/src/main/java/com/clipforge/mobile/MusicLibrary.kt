package com.clipforge.mobile
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
object MusicLibrary {
    const val RATE=22050
    const val ORIGIN="Original ClipForge procedural composition · no attribution required by ClipForge"
    fun duration(mood:MusicMood)=32L*60000/mood.bpm
    @Synchronized fun ensure(root:File,mood:MusicMood,variation:Int=0):MusicTrack {
        require(variation in 0..2)
        val folder=File(root,if(variation==0)"music-original-v1" else "music-original-v2").apply{mkdirs()}
        val suffix=if(variation==0)"" else "-$variation"
        val file=File(folder,mood.name+suffix+".wav")
        if(!file.isFile||file.length()!=44L+duration(mood)*RATE/1000*2){val temp=File(folder,file.name+".partial");try{temp.outputStream().use{it.write(wave(mood,variation));it.fd.sync()};check(temp.renameTo(file))}finally{temp.delete()}}
        val label=when(variation){1->"Soft instrumental";2->"Punch instrumental";else->"Original instrumental"}
        return MusicTrack(URI("file","",file.absolutePath,null).toASCIIString(),mood.label+" · "+label,duration(mood),volume=.14f,loop=true,ducking=true,bpm=mood.bpm,origin=ORIGIN,mood=mood.name,libraryId="original-"+mood.name+suffix)
    }
    fun wave(mood:MusicMood,variation:Int=0):ByteArray{val n=(duration(mood)*RATE/1000).toInt();val out=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);out.put("RIFF".toByteArray()).putInt(36+n*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(n*2)
        val scale=if(mood.minor)intArrayOf(0,3,7,10,7,3,5,7) else intArrayOf(0,4,7,12,7,4,9,7);val roots=when(variation){1->intArrayOf(53,48,55,45);2->intArrayOf(45,55,48,53);else->intArrayOf(48,53,45,55)};var noise=827L+mood.ordinal+variation*91
        repeat(n){i->val t=i.toDouble()/RATE;val beat=t*mood.bpm/60;val local=beat%1;val root=roots[(beat.toInt()/8)%4];val midi=root+scale[((beat*2).toInt()+variation*2)%8];val frequency=440*2.0.pow((midi-69)/12.0);val nt=(beat*2%1)*30/mood.bpm;val envelope=(1-exp(-nt*150))*exp(-nt*(if(mood in listOf(MusicMood.ROMANCE,MusicMood.EMOTIONAL,MusicMood.CHILL))5 else 12));val melody=(sin(2*PI*frequency*t)+.22*sin(4*PI*frequency*t))*.24*envelope;val bass=sin(2*PI*440*2.0.pow((root-81)/12.0)*t)*.12*(1-local*.5);val pad=sin(PI*frequency*t)*.035*sin(PI*(beat%4)/4).pow(2);noise=(noise*1664525+1013904223)and 0xffffffffL;val kick=sin(2*PI*(52*local*60/mood.bpm+3*(1-exp(-18*local))))*exp(-18*local)*if(beat.toInt()%2==0).13 else 0.0;val hat=((noise ushr 8).toDouble()/0xffffff*2-1)*exp(-55*(beat*2%1))*.025;val drums=if(variation==1||mood in listOf(MusicMood.ROMANCE,MusicMood.EMOTIONAL))0.0 else (kick+hat)*(if(variation==2)1.4 else 1.0);val seam=minOf(1.0,i/(RATE*.025),(n-1-i)/(RATE*.025)).coerceAtLeast(0.0);out.putShort(((melody+bass+pad+drums)*seam*(if(variation==1).7 else 1.0)*32767).roundToInt().coerceIn(-32767,32767).toShort())};return out.array()}
}
