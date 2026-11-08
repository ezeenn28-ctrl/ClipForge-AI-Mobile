package com.clipforge.mobile

import androidx.media3.common.Effect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun creativeEffects(name: String): List<Effect> = when (name) {
    "Zoom" -> listOf(ScaleAndRotateTransformation.Builder().setScale(1.12f, 1.12f).build())
    "Monochrome" -> listOf(RgbFilter.createGrayscaleFilter())
    else -> emptyList()
}
