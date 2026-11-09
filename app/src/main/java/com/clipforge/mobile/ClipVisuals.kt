package com.clipforge.mobile

/** Values are normalized to the output canvas; no resolution-dependent pixel offsets. */
data class MotionPoint(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun validate() { require(scale.isFinite() && scale in 1f..1.6f && x.isFinite() && x in -0.2f..0.2f && y.isFinite() && y in -0.2f..0.2f) { "Invalid motion keyframe" } }
}
data class ClipVisuals(val start: MotionPoint = MotionPoint(), val end: MotionPoint = MotionPoint(),
                       val ease: Boolean = true, val fadeInMs: Long = 0, val fadeOutMs: Long = 0) {
    fun validate() { start.validate(); end.validate(); require(fadeInMs in 0..5000 && fadeOutMs in 0..5000) { "Invalid video fade" } }
    fun point(localMs: Long, durationMs: Long): MotionPoint {
        val raw = (localMs.toDouble() / durationMs.coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
        val t = if (ease) raw * raw * (3 - 2 * raw) else raw
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return MotionPoint(mix(start.scale, end.scale), mix(start.x, end.x), mix(start.y, end.y))
    }
    fun opacity(localMs: Long, durationMs: Long): Float {
        val inside = localMs.coerceIn(0, durationMs)
        val from = minOf(fadeInMs, durationMs / 2)
        val until = minOf(fadeOutMs, durationMs / 2)
        return minOf(if (from > 0) (inside.toFloat() / from).coerceIn(0f, 1f) else 1f,
            if (until > 0) ((durationMs - inside).toFloat() / until).coerceIn(0f, 1f) else 1f)
    }
    /** Linear interpolation preserves motion exactly through a split; eased endpoints are retained. */
    fun split(localMs: Long, durationMs: Long): Pair<ClipVisuals, ClipVisuals> {
        val middle = point(localMs, durationMs)
        return copy(end = middle, fadeOutMs = 0) to copy(start = middle, fadeInMs = 0)
    }
}
enum class CaptionPreset(val label: String) { BOLD("Bold clean"), CLEAN("Minimal"), CARD("Caption card") }
