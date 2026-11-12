package com.clipforge.mobile
import org.junit.Assert.*
import org.junit.Test
class FrameRateCapTest {
    @Test fun sixtyFpsSourceUsesExactlyTwentyFourSlotsPerSecond(){val b=FrameBudget(24f);val accepted=(0 until 120).map{5_000_000L+it*1_000_000L/60}.filter{b.accept(it)};assertEquals(48,accepted.size);assertTrue(accepted.zipWithNext().all{it.second>it.first})}
    @Test fun lowerRateVfrIsPreservedAndNewStreamResetsBudget(){val b=FrameBudget(30f);val times=listOf(0L,66667L,150000L,260000L,900000L);assertTrue(times.all{b.accept(it)});assertFalse(b.accept(900000));b.reset();assertTrue(b.accept(0))}
}
