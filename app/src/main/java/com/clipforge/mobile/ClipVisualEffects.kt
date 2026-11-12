package com.clipforge.mobile

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ClipVisualEffects {
    fun transitionMotion(clip: TimelineClip, incoming: CutTransition?, outgoing: CutTransition?): MatrixTransformation {
        val clock = ClipPresentationClock()
        return MatrixTransformation { timestamp ->
            val state = TimelineTransitions.frame(clip, incoming, outgoing, clip.outputOffsetMs(clock.relativeUs(timestamp) / 1000))
            Matrix().apply { setScale(state.scale, state.scale) }
        }
    }
    fun transitionFade(clip: TimelineClip, incoming: CutTransition?, outgoing: CutTransition?): RgbMatrix {
        val clock = ClipPresentationClock()
        return RgbMatrix { timestamp, _ ->
            val opacity = TimelineTransitions.frame(clip, incoming, outgoing, clip.outputOffsetMs(clock.relativeUs(timestamp) / 1000)).opacity
            floatArrayOf(opacity,0f,0f,0f,0f,opacity,0f,0f,0f,0f,opacity,0f,0f,0f,0f,1f)
        }
    }
    fun motion(clip: TimelineClip): MatrixTransformation {
        val clock = ClipPresentationClock()
        return MatrixTransformation { timestamp ->
            val point = clip.visuals.point(clip.outputOffsetMs(clock.relativeUs(timestamp) / 1000), clip.durationMs)
            Matrix().apply { setScale(point.scale, point.scale); postTranslate(point.x * 2, -point.y * 2) }
        }
    }
    fun fade(clip: TimelineClip): RgbMatrix {
        val clock = ClipPresentationClock()
        return RgbMatrix { timestamp, _ ->
            val opacity = clip.visuals.opacity(clip.outputOffsetMs(clock.relativeUs(timestamp) / 1000), clip.durationMs)
            floatArrayOf(opacity, 0f, 0f, 0f, 0f, opacity, 0f, 0f, 0f, 0f, opacity, 0f, 0f, 0f, 0f, 1f)
        }
    }
}


