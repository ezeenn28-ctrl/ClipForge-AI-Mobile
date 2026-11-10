package com.clipforge.mobile

/** Shared SDR transform. Offsets use normalized RGB; captions are composited afterwards. */
data class ColorAdjustments(val brightness: Float = 0f, val contrast: Float = 1f,
                            val saturation: Float = 1f, val warmth: Float = 0f) {
    fun validate() {
        require(brightness.isFinite() && brightness in -0.4f..0.4f &&
            contrast.isFinite() && contrast in .5f..1.5f &&
            saturation.isFinite() && saturation in 0f..2f && warmth.isFinite() && warmth in -1f..1f) { "Invalid color adjustment" }
    }
    fun coefficients(monochrome: Boolean = false): FloatArray {
        val s = if (monochrome) 0f else saturation
        val luma = floatArrayOf(.2126f, .7152f, .0722f)
        val bias = .5f * (1f - contrast) + brightness
        return FloatArray(20).also { m ->
            for (row in 0..2) {
                for (column in 0..2) m[row * 5 + column] = contrast * ((1f - s) * luma[column] + if (row == column) s else 0f)
                m[row * 5 + 4] = bias + when (row) { 0 -> warmth * .1f; 2 -> -warmth * .1f; else -> 0f }
            }
            m[18] = 1f
        }
    }
    fun previewMatrix(monochrome: Boolean = false): FloatArray = coefficients(monochrome).also { m ->
        for (row in 0..2) m[row * 5 + 4] *= 255f
    }
    fun exportMatrix(monochrome: Boolean = false): FloatArray {
        val m = coefficients(monochrome)
        return FloatArray(16).also { result ->
            for (row in 0..2) for (column in 0..3) result[column * 4 + row] = m[row * 5 + if (column == 3) 4 else column]
            result[15] = 1f
        }
    }
}
