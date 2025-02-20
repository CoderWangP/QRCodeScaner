package com.wp.qrcode

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.*

/**
 *
 * Created by wp on 2020/9/16.
 *
 * Description:
 *
 */
object QRCodeDecoder {

    /**
     * 耗时，调用时，注意异步执行
     */
    fun decodeQRCode(context: Context,uri: Uri):Result?{
        val bitmap = Util.getDecodeBitmap(context,uri)
        return decodeQRCode(bitmap)
    }

    private fun decodeQRCode(bitmap: Bitmap?): Result?{
        if(bitmap == null){
            return null
        }
        val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"

        var result: Result? = null
        var source: RGBLuminanceSource? = null
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
//            val data = Util.getYUVByBitmap(bitmap)
//            source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            source = RGBLuminanceSource(width, height, pixels)
            result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                result = QRCodeReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints)
            }catch (e:Throwable){
                e.printStackTrace()
            }
        }
        return result
    }
}