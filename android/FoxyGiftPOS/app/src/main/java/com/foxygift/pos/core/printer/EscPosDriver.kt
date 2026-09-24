package com.foxygift.pos.core.printer

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

/**
 * ESC/POS 58mm thermal printer driver over Bluetooth SPP.
 *
 * Baltic diacritics (Latvian, Lithuanian, Estonian) are rendered via
 * [BalticCharsetRenderer] which draws text to a Bitmap and sends it in
 * monochrome bitmap graphic mode (ESC * m nL nH d1...dk).
 * This guarantees 100% accurate character rendering regardless of the
 * printer's built-in codepage support.
 *
 * Standard ASCII characters are sent as plain text in Windows-1257
 * encoding for performance on non-diacritic lines.
 */
class EscPosDriver(private val balticRenderer: BalticCharsetRenderer) {

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // ESC/POS commands
        private val ESC_INIT          = byteArrayOf(0x1B, 0x40)
        private val ESC_ALIGN_LEFT    = byteArrayOf(0x1B, 0x61, 0x00)
        private val ESC_ALIGN_CENTER  = byteArrayOf(0x1B, 0x61, 0x01)
        private val ESC_ALIGN_RIGHT   = byteArrayOf(0x1B, 0x61, 0x02)
        private val ESC_BOLD_ON       = byteArrayOf(0x1B, 0x45, 0x01)
        private val ESC_BOLD_OFF      = byteArrayOf(0x1B, 0x45, 0x00)
        private val ESC_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
        private val ESC_NORMAL_SIZE   = byteArrayOf(0x1B, 0x21, 0x00)
        private val ESC_FEED_LINE     = byteArrayOf(0x0A)
        private val ESC_CUT_PAPER     = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
        private val ESC_BEEP          = byteArrayOf(0x1B, 0x42, 0x03, 0x01) // beep 3 times
        private val GS_LINE_FEED      = byteArrayOf(0x1D, 0x56, 0x00)

        const val PAPER_WIDTH_DOTS = 384  // 58mm at 203 DPI
    }

    sealed class PrintResult {
        object Success : PrintResult()
        data class Error(val message: String) : PrintResult()
    }

    /**
     * Connects to a Bluetooth thermal printer and prints the given receipt.
     *
     * @param device    Paired Bluetooth device
     * @param receipt   [ReceiptBuilder] output — list of receipt lines
     */
    suspend fun print(device: BluetoothDevice, receipt: List<ReceiptLine>): PrintResult =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            runCatching {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket!!.connect()
                outputStream = socket!!.outputStream

                // Initialize printer
                outputStream!!.write(ESC_INIT)

                // Render each receipt line
                for (line in receipt) {
                    when (line) {
                        is ReceiptLine.TextLine   -> renderTextLine(outputStream!!, line)
                        is ReceiptLine.BitmapLine -> renderBitmapLine(outputStream!!, line.bitmap)
                        is ReceiptLine.Divider    -> renderDivider(outputStream!!)
                        is ReceiptLine.Feed       -> repeat(line.lines) { outputStream!!.write(ESC_FEED_LINE) }
                        is ReceiptLine.BalticText -> {
                            val bmp = balticRenderer.renderToBitmap(line.text, line.textSizeSp)
                            renderBitmapLine(outputStream!!, bmp)
                        }
                    }
                }

                // Final feed and cut
                outputStream!!.write(ESC_FEED_LINE)
                outputStream!!.write(ESC_FEED_LINE)
                outputStream!!.write(ESC_FEED_LINE)
                outputStream!!.write(ESC_CUT_PAPER)
                outputStream!!.write(ESC_BEEP)
                outputStream!!.flush()

                PrintResult.Success
            }.getOrElse { e -> PrintResult.Error(e.message ?: "Unknown error") }
              .also {
                  runCatching { outputStream?.close() }
                  runCatching { socket?.close() }
              }
        }

    private fun renderTextLine(out: OutputStream, line: ReceiptLine.TextLine) {
        // Alignment
        out.write(when (line.align) {
            Align.LEFT   -> ESC_ALIGN_LEFT
            Align.CENTER -> ESC_ALIGN_CENTER
            Align.RIGHT  -> ESC_ALIGN_RIGHT
        })
        if (line.bold)   out.write(ESC_BOLD_ON)
        if (line.double) out.write(ESC_DOUBLE_HEIGHT) else out.write(ESC_NORMAL_SIZE)

        // Encode as Windows-1257 (Latin Extended + Baltic)
        val encoded = line.text.toByteArray(charset("windows-1257"))
        out.write(encoded)
        out.write(ESC_FEED_LINE)

        if (line.bold)   out.write(ESC_BOLD_OFF)
        out.write(ESC_NORMAL_SIZE)
        out.write(ESC_ALIGN_LEFT)
    }

    /** Renders a monochrome Bitmap in ESC * (bit image) mode. */
    private fun renderBitmapLine(out: OutputStream, bitmap: Bitmap) {
        out.write(ESC_ALIGN_LEFT)
        val width  = minOf(bitmap.width, PAPER_WIDTH_DOTS)
        val height = bitmap.height

        // ESC * mode 0 (8-dot single density) per row
        for (row in 0 until height step 8) {
            out.write(byteArrayOf(0x1B, 0x2A, 0x00, (width and 0xFF).toByte(), ((width shr 8) and 0xFF).toByte()))
            for (col in 0 until width) {
                var slice = 0
                for (bit in 0 until 8) {
                    val y = row + bit
                    if (y < height) {
                        val pixel = bitmap.getPixel(col, y)
                        if (Color.red(pixel) < 128) slice = slice or (1 shl (7 - bit))
                    }
                }
                out.write(slice)
            }
            out.write(ESC_FEED_LINE)
        }
    }

    private fun renderDivider(out: OutputStream) {
        out.write(ESC_ALIGN_LEFT)
        val line = "-".repeat(32).toByteArray(Charsets.US_ASCII)
        out.write(line)
        out.write(ESC_FEED_LINE)
    }
}

// ─────────────────────── Receipt DSL ─────────────────────────────────────────

enum class Align { LEFT, CENTER, RIGHT }

sealed class ReceiptLine {
    data class TextLine(
        val text:   String,
        val align:  Align   = Align.LEFT,
        val bold:   Boolean = false,
        val double: Boolean = false,
    ) : ReceiptLine()

    /** Text containing Baltic diacritics — rendered via Bitmap. */
    data class BalticText(
        val text: String,
        val textSizeSp: Float = 14f,
    ) : ReceiptLine()

    data class BitmapLine(val bitmap: Bitmap) : ReceiptLine()
    object Divider : ReceiptLine()
    data class Feed(val lines: Int = 1) : ReceiptLine()
}
