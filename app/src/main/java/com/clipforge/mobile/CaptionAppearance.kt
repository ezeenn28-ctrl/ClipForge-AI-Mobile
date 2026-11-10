package com.clipforge.mobile

enum class CaptionFont(val label: String, val family: String) {
    SANS("Sans", "sans-serif"), SERIF("Serif", "serif"), MONO("Mono", "monospace")
}
enum class CaptionAlignment(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }
enum class CaptionColor(val label: String, val argb: Int?) {
    INHERIT("Original", null), WHITE("White", -1), YELLOW("Yellow", 0xFFFFFF00.toInt()),
    CYAN("Cyan", 0xFF00E5FF.toInt()), PINK("Pink", 0xFFFF69B4.toInt()), MINT("Mint", 0xFF85E6C4.toInt()), GREEN("Green", 0xFF39E75F.toInt());
    fun resolve(fallback: Int): Int = argb ?: fallback
}

/** Project-wide caption appearance; positions use the shared 720x1280 text canvas.
 * The opening hook retains its own top placement and sans-serif typeface. */
data class CaptionAppearance(
    val font: CaptionFont = CaptionFont.SANS,
    val positionY: Float = 970f / 1280f,
    val color: CaptionColor = CaptionColor.INHERIT,
    val highlight: CaptionColor = CaptionColor.YELLOW,
    val alignment: CaptionAlignment = CaptionAlignment.CENTER,
    val layout: ReferenceLayout = ReferenceLayout.STANDARD
) {
    fun validate() { require(positionY.isFinite() && positionY in .35f.. .85f) { "Caption position must stay inside the safe area" } }
    fun textColor(preset: CaptionPreset, legacyColor: Int): Int =
        color.resolve(if (preset == CaptionPreset.HIGHLIGHT) -1 else legacyColor)
    fun highlightColor(): Int = highlight.resolve(0xFFFFFF00.toInt())
}

