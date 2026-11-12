package com.clipforge.mobile

enum class CutTransitionKind(val label: String) { CUT("Cut"), DIP_BLACK("Dip to black"), ZOOM_CUT("Zoom cut") }

/** Boundary transitions preserve edit duration and source/audio clocks. No overlapping tracks. */
data class CutTransition(val kind: CutTransitionKind = CutTransitionKind.CUT, val durationMs: Long = 400) {
    fun validate() { require(durationMs in 200..1000) { "Transition duration must be 0.2–1s" } }
}

object TimelineTransitions {
    data class Frame(val opacity: Float = 1f, val scale: Float = 1f)
    fun frame(clip: TimelineClip, incoming: CutTransition?, outgoing: CutTransition?, localMs: Long): Frame {
        var opacity = 1f
        var scale = 1f
        fun apply(transition: CutTransition?, remaining: Long) {
            if (transition == null || transition.kind == CutTransitionKind.CUT) return
            val half = minOf(transition.durationMs / 2, clip.durationMs / 2).coerceAtLeast(1)
            val strength = (1f - remaining.coerceAtLeast(0).toFloat() / half).coerceIn(0f, 1f)
            when (transition.kind) {
                CutTransitionKind.DIP_BLACK -> opacity *= 1f - strength
                CutTransitionKind.ZOOM_CUT -> scale *= 1f + .12f * strength
                CutTransitionKind.CUT -> Unit
            }
        }
        apply(incoming, localMs)
        apply(outgoing, clip.durationMs - localMs)
        return Frame(opacity, scale)
    }
    fun frame(clips: List<TimelineClip>, index: Int, localMs: Long): Frame {
        val clip = clips.getOrNull(index) ?: return Frame()
        return frame(clip, clips.getOrNull(index - 1)?.transition,
            clip.transition.takeIf { index < clips.lastIndex }, localMs)
    }
}

