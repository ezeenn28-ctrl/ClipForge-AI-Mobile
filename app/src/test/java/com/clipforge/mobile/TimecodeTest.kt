package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class TimecodeTest {
    @Test fun parsesLongVideoRangesWithoutPercentBasedMinimum() {
        val start = Timecode.parse("1:59:40.250")!!
        val end = Timecode.parse("2:00:00")!!
        assertEquals(7_180_250L, start)
        assertEquals(19_750L, end - start)
        assertTrue(Timecode.validTrim(start, end, 7_200_000))
    }
    @Test fun acceptsSecondsAndFractionalTimecodes() {
        assertEquals(20_500L, Timecode.parse("20.5"))
        assertEquals(80_050L, Timecode.parse("01:20.05"))
        assertEquals("1:00:00.123", Timecode.precise(3_600_123))
    }
    @Test fun rejectsInvalidInputOverflowAndReversedTrims() {
        listOf("-1", "1:60", "1:61:00", "1.1234", "1:", "999999999999999999", "NaN").forEach { assertNull(Timecode.parse(it)) }
        assertFalse(Timecode.validTrim(30_000, 20_000, 7_200_000))
        assertFalse(Timecode.validTrim(0, 30_001, 30_000))
    }
}
