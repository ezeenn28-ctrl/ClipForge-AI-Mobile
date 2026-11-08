package com.clipforge.mobile

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClipAudioProcessorTest {
    @Test fun fragmentedStereoFramesArePreservedAndVolumeIsApplied() {
        val processor = ClipAudioProcessor(TimelineClip("a", 0, 1000, volume = .5f))
        processor.configure(AudioProcessor.AudioFormat(1000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putShort(1000).putShort(-1000).putShort(2000).putShort(-2000).array()
        processor.queueInput(ByteBuffer.wrap(pcm.copyOfRange(0, 3)))
        assertEquals(0, processor.output.remaining())
        processor.queueInput(ByteBuffer.wrap(pcm.copyOfRange(3, 8)))
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(500, output.short.toInt()); assertEquals(-500, output.short.toInt())
        assertEquals(1000, output.short.toInt()); assertEquals(-1000, output.short.toInt())
        processor.queueEndOfStream()
    }
    @Test fun flushRestartsFadeEnvelopeAtClipStart() {
        val processor = ClipAudioProcessor(TimelineClip("a", 0, 1000, fadeInMs = 1000))
        processor.configure(AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_FLOAT)); processor.flush()
        processor.queueInput(ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(1f).apply { flip() })
        assertEquals(0f, processor.output.order(ByteOrder.nativeOrder()).float, .0001f)
        processor.flush()
        processor.queueInput(ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(1f).apply { flip() })
        assertEquals(0f, processor.output.order(ByteOrder.nativeOrder()).float, .0001f)
    }
}
