package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class AudioWindowTest {
    @Test fun trimsLeadingDecodedSamplesBeforeSeekTarget() {
        val slice = AudioWindow.slice(9_500_000, 16_000, 16_000, 10_000_000, 11_000_000)
        assertEquals(8_000, slice.firstFrame)
        assertEquals(16_000, slice.endFrame)
        assertEquals(0L, slice.outputFrame)
    }
    @Test fun trimsExclusiveEndAndPreservesSourceOffset() {
        val slice = AudioWindow.slice(10_500_000, 16_000, 16_000, 10_000_000, 11_000_000)
        assertEquals(0, slice.firstFrame)
        assertEquals(8_000, slice.endFrame)
        assertEquals(8_000L, slice.outputFrame)
    }
    @Test fun framesOutsideWindowAreEmpty() {
        val before = AudioWindow.slice(0, 160, 16_000, 1_000_000, 2_000_000)
        val after = AudioWindow.slice(3_000_000, 160, 16_000, 1_000_000, 2_000_000)
        assertEquals(before.firstFrame, before.endFrame)
        assertEquals(after.firstFrame, after.endFrame)
    }
}
