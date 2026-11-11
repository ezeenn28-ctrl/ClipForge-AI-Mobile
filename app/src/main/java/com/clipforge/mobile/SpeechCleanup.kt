package com.clipforge.mobile

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Modest, optional rumble filtering, soft noise gating and gentle compression; not voice isolation. */
class VoiceCleanupFilter(private val rate:Int) {
    private var previous=0f; private var high=0f; private var envelope=0f
    private val pole=exp(-2*PI*85/rate).toFloat()
    fun sample(value:Float,strength:Float):Float {
        val input=if(value.isFinite())value.coerceIn(-1f,1f) else 0f
        high=pole*(high+input-previous);previous=input
        val attack=if(abs(high)>envelope).02f else .001f
        envelope+=(abs(high)-envelope)*attack
        val gate=(envelope/.008f).coerceIn(.12f,1f)
        val compression=if(envelope>.25f)(.25f+(envelope-.25f)/2.2f)/envelope else 1f
        val cleaned=high*gate*compression
        return (input+(cleaned-input)*strength.coerceIn(0f,1f)).coerceIn(-1f,1f)
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SpeechCleanupProcessor(private val strength:()->Float):BaseAudioProcessor() {
    private var pending=ByteArray(0);private var filters=emptyList<VoiceCleanupFilter>()
    override fun onConfigure(format:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        if(format.encoding!=C.ENCODING_PCM_16BIT&&format.encoding!=C.ENCODING_PCM_FLOAT)throw AudioProcessor.UnhandledAudioFormatException(format)
        return format
    }
    override fun onFlush(){pending=ByteArray(0);filters=List(inputAudioFormat.channelCount.coerceIn(1,8)){VoiceCleanupFilter(inputAudioFormat.sampleRate.coerceAtLeast(8000))}}
    override fun queueInput(buffer:ByteBuffer) {
        val bytes=pending+ByteArray(buffer.remaining()).also{buffer.get(it)}
        val width=if(inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT)4 else 2
        val complete=bytes.size/(width*inputAudioFormat.channelCount)*(width*inputAudioFormat.channelCount)
        pending=bytes.copyOfRange(complete,bytes.size)
        val input=ByteBuffer.wrap(bytes,0,complete).order(ByteOrder.nativeOrder());val out=replaceOutputBuffer(complete).order(ByteOrder.nativeOrder())
        val level=strength().let{if(it.isFinite())it.coerceIn(0f,1f) else 0f}
        repeat(complete/width){index->val x=if(width==4)input.float else input.short/32768f;val y=filters[index%filters.size].sample(x,level);if(width==4)out.putFloat(y) else out.putShort((y*32768).toInt().coerceIn(-32768,32767).toShort())};out.flip()
    }
    override fun onQueueEndOfStream(){check(pending.isEmpty()){ "Incomplete cleanup PCM frame" }}
}
