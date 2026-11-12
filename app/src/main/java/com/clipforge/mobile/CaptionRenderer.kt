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
                       appearance: CaptionAppearance = CaptionAppearance(), bitmapScale: Float = 1f, activeWord: Int? = null, placement:TextPlacement=TextPlacement(),opening:OpeningStyle=OpeningStyle(),openingTimeMs:Long=0): Bitmap {
    appearance.validate(); placement.validate();opening.validate()
    require(bitmapScale.isFinite() && bitmapScale in .25f..1f) { "Invalid caption bitmap scale" }
    val bitmap = Bitmap.createBitmap((720 * bitmapScale).toInt(), (1280 * bitmapScale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(bitmapScale, bitmapScale) }
    val reference = appearance.layout != ReferenceLayout.STANDARD
    if (reference) {
        val layout = appearance.layout
        val matte = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layout.matteColor }
        val mask = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(0f, 0f, 720f, 1280f, android.graphics.Path.Direction.CW)
            addRoundRect(0f, layout.videoTop, 720f, layout.videoBottom, layout.cornerRadius, layout.cornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(mask, matte)
        if (layout.accentColor != 0) {
            matte.color = layout.accentColor; matte.style = Paint.Style.STROKE; matte.strokeWidth = 4f
            canvas.drawRoundRect(2f, layout.videoTop, 718f, layout.videoBottom, layout.cornerRadius, layout.cornerRadius, matte)
        }
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, if (preset == CaptionPreset.CLEAN) Typeface.NORMAL else Typeface.BOLD)
        setShadowLayer(5f, 2f, 3f, Color.BLACK)
    }
    data class Word(val text: String, val marked: Boolean, val index: Int)
    listOf(hook.take(65), caption.take(100)).forEachIndexed { index, text ->
        if (text.isBlank() || (index==0 && opening.alpha(openingTimeMs)<=0)) return@forEachIndexed
        val body = index == 1
        paint.typeface = Typeface.create(if (index == 0) opening.font.family else appearance.font.family,
            if (body && preset == CaptionPreset.EDITORIAL) Typeface.ITALIC else if (preset == CaptionPreset.CLEAN) Typeface.NORMAL else Typeface.BOLD)
        paint.setShadowLayer(5f, 2f, 3f, Color.BLACK)
        if (body && preset == CaptionPreset.NEON) paint.setShadowLayer(12f, 0f, 0f, appearance.highlightColor())
        if (body && preset in listOf(CaptionPreset.STICKER, CaptionPreset.CAPSULE)) paint.clearShadowLayer()
        val baseSize = (if (index == 0) if (reference) 38f else 45f else subtitleSize) * if(index==0)placement.hookScale*opening.scale(openingTimeMs) else placement.scale
        paint.textSize = baseSize
        val words = (if (body && preset == CaptionPreset.COMIC) text.uppercase(java.util.Locale.ROOT) else text).split(Regex("\\s+")).filter { it.isNotEmpty() }.mapIndexed { wordIndex, it ->
            val marked = it.startsWith("#") && it.length > 1
            Word(if (marked) it.drop(1) else it, marked, wordIndex)
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
            val defaultLeft = if (index == 0) (720f - width) / 2f else when (appearance.alignment) {
                CaptionAlignment.LEFT -> 48f
                CaptionAlignment.CENTER -> (720f - width) / 2f
                CaptionAlignment.RIGHT -> 672f - width
            }
            val left=(defaultLeft+(if(body)placement.x else placement.hookX)*720f).coerceIn(24f,(696f-width).coerceAtLeast(24f))
            val lineStep = if (body && preset in listOf(CaptionPreset.OUTLINE, CaptionPreset.NEON, CaptionPreset.CAPSULE, CaptionPreset.STICKER, CaptionPreset.COMIC, CaptionPreset.EDITORIAL)) maxOf(55f, baseSize + 28f) else 55f*(if(body)placement.scale else placement.hookScale)
            val newStyle = body && preset in listOf(CaptionPreset.OUTLINE, CaptionPreset.NEON, CaptionPreset.CAPSULE, CaptionPreset.STICKER, CaptionPreset.COMIC, CaptionPreset.EDITORIAL)
            val captionY = if (newStyle) minOf(appearance.positionY * 1280f, 1240f - (minOf(3, rows.size) - 1) * lineStep) else appearance.positionY * 1280f
            val y = ((if (index == 0) if (reference) appearance.layout.videoTop - 100f else 210f else captionY)+(if(body)placement.y else placement.hookY)*1280f).coerceIn(baseSize+20f,1240f-(minOf(3,rows.size)-1)*lineStep) + row * lineStep
            if(index==0&&opening.background){val card=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb((210*opening.alpha(openingTimeMs)).toInt(),0,0,0)};canvas.drawRoundRect(left-18f,y-paint.textSize-8f,left+width+18f,y+14f,12f,12f,card)}
            if (body && preset in listOf(CaptionPreset.CAPSULE, CaptionPreset.STICKER)) {
                val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (preset == CaptionPreset.STICKER) Color.WHITE else Color.argb(235, 8, 12, 20)
                    if (preset == CaptionPreset.STICKER) setShadowLayer(5f, 2f, 4f, Color.BLACK)
                }
                val radius = if (preset == CaptionPreset.CAPSULE) 28f else 8f
                canvas.drawRoundRect(left - 18f, y - paint.textSize - 8f, left + width + 18f, y + 14f, radius, radius, card)
                card.clearShadowLayer(); card.color = appearance.highlightColor()
                if (preset == CaptionPreset.CAPSULE) canvas.drawRoundRect(left - 12f, y - paint.textSize, left - 6f, y + 6f, 3f, 3f, card)
            }
            if (body && preset == CaptionPreset.EDITORIAL) {
                val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = appearance.highlightColor(); strokeWidth = 3f }
                canvas.drawLine(left, y + 11f, left + width, y + 11f, rule)
            }
            if (preset == CaptionPreset.CARD && index == 1) {
                paint.color = Color.argb(210, 0, 0, 0); paint.style = Paint.Style.FILL
                canvas.drawRoundRect(left - 18f, y - paint.textSize - 7f, left + width + 18f, y + 13f, 12f, 12f, paint)
            }
            paint.color = Color.BLACK; paint.style = Paint.Style.STROKE
            paint.strokeWidth = when {
                index == 0 && reference -> 0f
                body && preset == CaptionPreset.OUTLINE -> 13f
                body && preset == CaptionPreset.COMIC -> 11f
                body && preset in listOf(CaptionPreset.NEON, CaptionPreset.EDITORIAL) -> 3f
                preset == CaptionPreset.CLEAN -> 5f
                else -> 9f
            }
            paint.alpha=if(body)255 else (255*opening.alpha(openingTimeMs)).toInt()
            if (!(index == 0 && reference) && !(body && preset in listOf(CaptionPreset.STICKER, CaptionPreset.CAPSULE))) canvas.drawText(clean, left, y, paint)
            paint.style = Paint.Style.FILL
            var prefix = ""
            line.forEachIndexed { wordIndex, word ->
                val autoHighlight = index == 1 && preset in listOf(CaptionPreset.HIGHLIGHT, CaptionPreset.NEON, CaptionPreset.COMIC) &&
                    activeWord == null && !explicitHighlights && wordIndex == line.lastIndex
                paint.color = when {
                    word.marked || autoHighlight || (body && activeWord == word.index) -> appearance.highlightColor()
                    index == 0 && reference -> opening.color.resolve(appearance.layout.hookColor)
                    index == 0 -> opening.color.resolve(Color.WHITE)
                    else -> appearance.textColor(preset, subtitleColor)
                }
                paint.alpha=if(body)255 else (255*opening.alpha(openingTimeMs)).toInt()
                canvas.drawText(word.text, left + paint.measureText(prefix), y, paint)
                prefix += word.text + " "
            }
        }
    }
    return bitmap
}



