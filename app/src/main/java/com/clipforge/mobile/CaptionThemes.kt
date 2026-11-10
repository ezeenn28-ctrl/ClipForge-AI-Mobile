package com.clipforge.mobile

object CaptionThemes {
    fun apply(preset: CaptionPreset, current: CaptionAppearance): CaptionAppearance = when (preset) {
        CaptionPreset.OUTLINE -> current.copy(color = CaptionColor.WHITE, highlight = CaptionColor.YELLOW)
        CaptionPreset.NEON -> current.copy(color = CaptionColor.WHITE, highlight = CaptionColor.CYAN)
        CaptionPreset.CAPSULE -> current.copy(color = CaptionColor.WHITE, highlight = CaptionColor.YELLOW)
        CaptionPreset.STICKER -> current.copy(color = CaptionColor.BLACK, highlight = CaptionColor.PINK)
        CaptionPreset.COMIC -> current.copy(font = CaptionFont.CONDENSED, color = CaptionColor.WHITE, highlight = CaptionColor.YELLOW)
        CaptionPreset.EDITORIAL -> current.copy(font = CaptionFont.SERIF, color = CaptionColor.WHITE, highlight = CaptionColor.MINT)
        else -> current
    }
}
