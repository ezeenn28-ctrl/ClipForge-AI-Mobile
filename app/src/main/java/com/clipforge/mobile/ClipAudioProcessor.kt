package com.clipforge.mobile

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipAudioProcessor(private val clip: TimelineClip) : BaseAudioProcessor() {
    private var frame = 0L
    private var pending = ByteArray(0)
    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (format.encoding != C.ENCODING_PCM_16BIT && format.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(format)
        return format
    }
    override fun onFlush() { frame = 0; pending = ByteArray(0) }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val incoming = ByteArray(inputBuffer.remaining()).also { inputBuffer.get(it) }
        val bytes = pending + incoming
        val sampleBytes = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = sampleBytes * inputAudioFormat.channelCount
        val frames = bytes.size / frameBytes
        val complete = frames * frameBytes
        pending = bytes.copyOfRange(complete, bytes.size)
        val input = ByteBuffer.wrap(bytes, 0, complete).order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(complete).order(ByteOrder.nativeOrder())
        repeat(frames) {
            val gain = TimelineModel.audioGain(clip, clip.outputOffsetMs(frame * 1000 / inputAudioFormat.sampleRate))
            repeat(inputAudioFormat.channelCount) {
                if (sampleBytes == 4) output.putFloat((VoiceDsp.sample(input.float, clip.voiceGain) * gain).coerceIn(-1f, 1f))
                else output.putShort((VoiceDsp.sample(input.short / 32768f, clip.voiceGain) * 32768f * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            frame++
        }
        output.flip()
    }
    override fun onQueueEndOfStream() { check(pending.isEmpty()) { "Audio ended with an incomplete PCM frame" } }
}

