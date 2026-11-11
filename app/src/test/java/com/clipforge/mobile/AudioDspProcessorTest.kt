package com.clipforge.mobile
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class AudioDspProcessorTest {
    @Test fun unityIsBitExactAndFragmentedStereoPreservesFrameClock() {
        val times=mutableListOf<Long>();val p=AudioDspProcessor { x,t -> times+=t;x }
        p.configure(AudioProcessor.AudioFormat(1000,2,C.ENCODING_PCM_16BIT));p.flush()
        val bytes=ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putShort(-32768).putShort(32767).putShort(12345).putShort(-23456).array()
        p.queueInput(ByteBuffer.wrap(bytes.copyOfRange(0,3)));assertEquals(0,p.output.remaining())
        p.queueInput(ByteBuffer.wrap(bytes.copyOfRange(3,8)));val actual=ByteArray(8);p.output.get(actual)
        assertArrayEquals(bytes,actual);assertEquals(listOf(0L,0L,1L,1L),times);p.queueEndOfStream()
    }
    @Test fun flushResetsEnvelopeAndRejectsNonFiniteSamples() {
        val p=AudioDspProcessor{x,t->if(t==0L)Float.NaN else x*.5f}
        p.configure(AudioProcessor.AudioFormat(1000,1,C.ENCODING_PCM_FLOAT));p.flush()
        p.queueInput(ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putFloat(1f).putFloat(1f).apply{flip()})
        val out=p.output.order(ByteOrder.nativeOrder());assertEquals(0f,out.float,0f);assertEquals(.5f,out.float,0f)
        p.flush();p.queueInput(ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(1f).apply{flip()});assertEquals(0f,p.output.order(ByteOrder.nativeOrder()).float,0f)
    }
}
