package com.clipforge.mobile

enum class ReferenceLayout(val label: String, val videoAspect: Float?) {
    STANDARD("Original canvas", null), WHITE_SQUARE("White square crop", 1f), WHITE_CLASSIC("White 4:3 crop", 4f / 3f);
    val videoHeight: Float get() = videoAspect?.let { 720f / it } ?: 1280f
    val videoTop: Float get() = (1280f - videoHeight) / 2f
    val videoBottom: Float get() = videoTop + videoHeight
}
