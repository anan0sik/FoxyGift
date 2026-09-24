package com.foxygift.pos.core.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders text containing Baltic/Eastern European diacritics to a monochrome
 * [Bitmap] suitable for ESC/POS bit-image printing mode.
 *
 * Supports all characters from Latvian (ā,č,ē,ģ,ī,ķ,ļ,ņ,š,ū,ž),
 * Lithuanian (ą,ę,ė,į,ų), Estonian (õ,ä,ö,ü), German (ä,ö,ü,ß), and
 * standard Latin — guaranteed by Android's system font renderer (Roboto/NotoSans).
 */
@Singleton
class BalticCharsetRenderer @Inject constructor() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.BLACK
        textSize  = 28f   // ~14sp at 2x
        typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isAntiAlias = true
    }

    /**
     * Renders [text] to a monochrome Bitmap exactly 384 pixels wide (58mm at 203 DPI).
     *
     * Text wraps automatically at word boundaries. Returns a black-on-white bitmap
     * ready for [EscPosDriver.renderBitmapLine].
     *
     * @param text        The text to render (may contain any Unicode diacritics).
     * @param textSizeSp  Approximate text size. Default 14sp ≈ 28px at 2x density.
     */
    fun renderToBitmap(text: String, textSizeSp: Float = 14f): Bitmap {
        paint.textSize = textSizeSp * 2f  // 2x for better thermal resolution

        val paperWidth = EscPosDriver.PAPER_WIDTH_DOTS
        val lines      = wrapText(text, paperWidth)
        val lineHeight = (paint.descent() - paint.ascent()).toInt() + 2
        val bmpHeight  = maxOf(lineHeight, lines.size * lineHeight)

        val bitmap = Bitmap.createBitmap(paperWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = -paint.ascent()
        for (line in lines) {
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }

        return bitmap.toMonochrome()
    }

    /**
     * Word-wraps [text] to fit within [maxWidthPx] pixels.
     */
    private fun wrapText(text: String, maxWidthPx: Int): List<String> {
        val words  = text.split(" ")
        val result = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidthPx) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    /**
     * Converts any ARGB bitmap to strict 1-bit monochrome (black/white)
     * using a 50% grey threshold. Required for ESC/POS bit-image mode.
     */
    private fun Bitmap.toMonochrome(): Bitmap {
        val mono = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel  = getPixel(x, y)
                val grey   = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
                mono.setPixel(x, y, if (grey < 128) Color.BLACK else Color.WHITE)
            }
        }
        return mono
    }
}
