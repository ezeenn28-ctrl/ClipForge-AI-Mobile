package com.clipforge.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Shared bitmap renderer for live captions and MP4 captions.
 * Highlight is a visual emphasis rule, not word-level speech alignment. */
fun makeCaptionOverlay(hook: String, caption: String, subtitleSize: Float = 39f,
                       subtitleColor: Int = Color.WHITE, preset: CaptionPreset = CaptionPreset.BOLD,
                       appearance: CaptionAppearance = CaptionAppearance(), bitmapScale: Float = 1f): Bitmap {
    appearance.validate()
    require(bitmapScale.isFinite() && bitmapScale in .25f..1f) { "Invalid caption bitmap scale" }
    val bitmap = Bitmap.createBitmap((720 * bitmapScale).toInt(), (1280 * bitmapScale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(bitmapScale, bitmapScale) }
    val reference = appearance.layout != ReferenceLayout.STANDARD
    if (reference) {
        val matte = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, 720f, appearance.layout.videoTop, matte)
        canvas.drawRect(0f, appearance.layout.videoBottom, 720f, 1280f, matte)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, if (preset == CaptionPreset.CLEAN) Typeface.NORMAL else Typeface.BOLD)
        setShadowLayer(5f, 2f, 3f, Color.BLACK)
    }
    data class Word(val text: String, val marked: Boolean)
    listOf(hook.take(65), caption.take(100)).forEachIndexed { index, text ->
        if (text.isBlank()) return@forEachIndexed
        paint.typeface = Typeface.create(if (index == 0) "sans-serif" else appearance.font.family,
            if (preset == CaptionPreset.CLEAN) Typeface.NORMAL else Typeface.BOLD)
        val baseSize = if (index == 0) if (reference) 38f else 45f else subtitleSize
        paint.textSize = baseSize
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
            val marked = it.startsWith("#") && it.length > 1
            Word(if (marked) it.drop(1) else it, marked)
        }
        val rows = mutableListOf<List<Word>>()
        var current = mutableListOf<Word>()
        words.forEach { word ->
            val proposed = (current + word).joinToString(" ") { it.text }
            if (current.isNotEmpty() && paint.measureText(proposed) > 624f) {
                rows.add(current); current = mutableListOf()
            }
            current.add(word)
        }
        if (current.isNotEmpty()) rows.add(current)
        val explicitHighlights = words.any { it.marked }
        rows.take(if (index == 0 && reference) 2 else 3).forEachIndexed { row, line ->
            val clean = line.joinToString(" ") { it.text }
            paint.textSize = baseSize
            val naturalWidth = paint.measureText(clean)
            if (naturalWidth > 624f) paint.textSize = baseSize * 624f / naturalWidth
            val width = paint.measureText(clean)
            val left = if (index == 0) (720f - width) / 2f else when (appearance.alignment) {
                CaptionAlignment.LEFT -> 48f
                CaptionAlignment.CENTER -> (720f - width) / 2f
                CaptionAlignment.RIGHT -> 672f - width
            }
            val y = (if (index == 0) if (reference) appearance.layout.videoTop - 100f else 210f else appearance.positionY * 1280f) + row * 55f
            if (preset == CaptionPreset.CARD && index == 1) {
                paint.color = Color.argb(210, 0, 0, 0); paint.style = Paint.Style.FILL
                canvas.drawRoundRect(left - 18f, y - paint.textSize - 7f, left + width + 18f, y + 13f, 12f, 12f, paint)
            }
            paint.color = Color.BLACK; paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (index == 0 && reference) 0f else if (preset == CaptionPreset.CLEAN) 5f else 9f
            if (!(index == 0 && reference)) canvas.drawText(clean, left, y, paint)
            paint.style = Paint.Style.FILL
            var prefix = ""
            line.forEachIndexed { wordIndex, word ->
                val autoHighlight = index == 1 && preset == CaptionPreset.HIGHLIGHT &&
                    !explicitHighlights && wordIndex == line.lastIndex
                paint.color = when {
                    word.marked || autoHighlight -> appearance.highlightColor()
                    index == 0 && reference -> if (appearance.layout == ReferenceLayout.WHITE_CLASSIC) 0xFF179B38.toInt() else Color.BLACK
                    index == 0 -> Color.WHITE
                    else -> appearance.textColor(preset, subtitleColor)
                }
                canvas.drawText(word.text, left + paint.measureText(prefix), y, paint)
                prefix += word.text + " "
            }
        }
    }
    return bitmap
}

