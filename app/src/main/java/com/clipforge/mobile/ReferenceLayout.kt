package com.clipforge.mobile

enum class ReferenceLayout(val label: String, val videoAspect: Float?,
                           val matteColor: Int = -1, val hookColor: Int = 0xFF000000.toInt(),
                           val accentColor: Int = 0, val cornerRadius: Float = 0f,
                           val captionY: Float = .73f) {
    STANDARD("Original canvas", null, captionY = 970f / 1280f),
    WHITE_SQUARE("White square crop", 1f),
    WHITE_CLASSIC("White 4:3 crop", 4f / 3f, hookColor = 0xFF179B38.toInt(), captionY = .79f),
    DARK_SQUARE("Dark square", 1f, 0xFF101820.toInt(), -1, 0xFF00D1D9.toInt()),
    DARK_CLASSIC("Dark classic", 4f / 3f, 0xFF101820.toInt(), -1, 0xFFFFD166.toInt(), captionY = .79f),
    PAPER_CLASSIC("Paper classic", 4f / 3f, 0xFFF2E9D8.toInt(), 0xFF382819.toInt(), 0xFF98734F.toInt(), captionY = .79f),
    NEON_SQUARE("Neon square", 1f, 0xFF100B22.toInt(), -1, 0xFFF05DFF.toInt()),
    CINEMA_WIDE("Cinema wide", 16f / 9f, 0xFF070B12.toInt(), -1, 0xFFFFD15C.toInt(), captionY = .75f),
    PORTRAIT_CARD("Portrait card", 3f / 4f, 0xFFEDF2FF.toInt(), 0xFF17233A.toInt(), 0xFF5271FF.toInt(), 24f, .82f);
    val videoHeight: Float get() = videoAspect?.let { 720f / it } ?: 1280f
    val videoTop: Float get() = (1280f - videoHeight) / 2f
    val videoBottom: Float get() = videoTop + videoHeight
}

