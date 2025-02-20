package com.wp.qrcode

import android.graphics.Bitmap
import com.google.zxing.*
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*

/**
 *
 * Created by wp on 2020/9/16.
 *
 * Description:
 *
 */
object QRCodeEncoder {

    private const val WHITE = -0x1
    private const val BLACK = -0x1000000

    private val FORMAT = BarcodeFormat.QR_CODE


    fun encodeAsBitmap(contents: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (contents.isNullOrEmpty()) {
            return null
        }
        var bitmap: Bitmap? = null
        try {
            val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = "1"
            val result :BitMatrix = QRCodeWriter().encode(contents, FORMAT, reqWidth, reqHeight, hints) ?: return null
            val width = result.width
            val height = result.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (result[x, y]) BLACK else WHITE
                }
            }
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmap
    }

    private fun guessAppropriateEncoding(contents: CharSequence): String? {
        // Very crude at the moment
        for (element in contents) {
            if (element.toInt() > 0xFF) {
                return "UTF-8"
            }
        }
        return null
    }
}