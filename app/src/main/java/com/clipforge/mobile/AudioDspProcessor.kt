package com.clipforge.mobile
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioDspProcessor(private val transform:(Float,Long)->Float):BaseAudioProcessor(){
    private var frame=0L;private var pending=ByteArray(0)
    override fun onConfigure(format:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat{if(format.encoding!=C.ENCODING_PCM_16BIT&&format.encoding!=C.ENCODING_PCM_FLOAT)throw AudioProcessor.UnhandledAudioFormatException(format);return format}
    override fun onFlush(){frame=0;pending=ByteArray(0)}
    override fun queueInput(buffer:ByteBuffer){val bytes=pending+ByteArray(buffer.remaining()).also{buffer.get(it)};val width=if(inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT)4 else 2;val n=bytes.size/(width*inputAudioFormat.channelCount);val complete=n*width*inputAudioFormat.channelCount;pending=bytes.copyOfRange(complete,bytes.size);val input=ByteBuffer.wrap(bytes,0,complete).order(ByteOrder.nativeOrder());val out=replaceOutputBuffer(complete).order(ByteOrder.nativeOrder());repeat(n){val ms=frame*1000/inputAudioFormat.sampleRate;repeat(inputAudioFormat.channelCount){val value=transform(if(width==4)input.float else input.short/32768f,ms).let{if(it.isFinite())it.coerceIn(-1f,1f) else 0f};if(width==4)out.putFloat(value) else out.putShort((value*32768).toInt().coerceIn(-32768,32767).toShort())};frame++};out.flip()}
    override fun onQueueEndOfStream(){check(pending.isEmpty()){"Incomplete PCM frame"}}
}
