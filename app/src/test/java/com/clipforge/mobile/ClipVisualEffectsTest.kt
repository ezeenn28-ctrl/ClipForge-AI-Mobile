package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipVisualEffectsTest {
    @Test fun exportEffectsUseItemLocalTimeAndPreserveAlpha() {
        val clip = TimelineClip("a", 60_000, 70_000, visuals = ClipVisuals(end = MotionPoint(1.4f, .2f), ease = false, fadeInMs = 1000))
        val motion = ClipVisualEffects.motion(clip)
        motion.getMatrix(20_000_000)
        val values = FloatArray(9)
        motion.getMatrix(25_000_000).getValues(values)
        assertEquals(1.2f, values[0], .0001f); assertEquals(.2f, values[2], .0001f)
        val fade = ClipVisualEffects.fade(clip)
        assertEquals(0f, fade.getMatrix(20_000_000, false)[0], .0001f)
        val halfway = fade.getMatrix(20_500_000, false)
        assertEquals(.5f, halfway[0], .0001f); assertEquals(1f, halfway[15], .0001f)
    }
}
