package com.wp.qrcode

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.TextUtils
import android.view.WindowManager
import java.io.IOException

/**
 * Created by wp on 2017/11/23.
 *
 * 屏幕工具，单位转换
 */
object Util {

    private const val TAG = "qrcode.Util"

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    fun dp2pxF(context: Context, dpValue: Float): Float {
        val scale = context.resources.displayMetrics.density
        return dpValue * scale + 0.5f
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    fun px2dp(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    /**
     * 像素转sp
     * @param pxValue
     * @return
     */
    fun px2sp(context: Context, pxValue: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (pxValue / fontScale + 0.5f).toInt()
    }

    /**
     * sp转像素
     * @param spValue
     * @return
     */
    fun sp2px(context: Context, spValue: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    /**
     * sp转dp
     * @param spValue
     * @return
     */
    fun sp2dp(context: Context, spValue: Float): Int {
        return px2dp(context, sp2px(context, spValue).toFloat())
    }


    /**
     * 将本地图片文件转换成可解码二维码的 Bitmap。
     *
     * @param picturePath 本地图片文件路径
     */
    fun getDecodeBitmap(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) {
            return null
        }
        var pfd: ParcelFileDescriptor? = null
        var bitmap: Bitmap? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
            val sampleSize = calculateInSampleSize(context, options)
            Logger.d(TAG, "sampleSize = $sampleSize")
            options.inSampleSize = sampleSize
            options.inJustDecodeBounds = false
            bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                pfd?.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return bitmap
    }


    /**
     * 计算缩放的比例
     */
    private fun calculateInSampleSize(context: Context, options: BitmapFactory.Options): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        val screenResolution = getScreenResolution(context)
        val reqWidth = screenResolution.x
        val reqHeight = screenResolution.y
        if (reqWidth == 0 || reqHeight == 0) {
            return 1
        }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            /**
             * 计算最大的inSampleSize值
             */
            while (halfHeight / inSampleSize > reqHeight
                    && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


    /**
     * 屏幕参数
     * @return
     */
    private fun getScreenResolution(context: Context): Point {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
                ?: return Point()
        val display = manager.defaultDisplay
        val screenResolution = Point()
        display.getSize(screenResolution)
        return screenResolution
    }


    /**
     * 从 Uri 中获取文件路劲
     * @param uri
     * @return
     */
    fun getFilePathFromUri(context: Context, uri: Uri?): String? {
        if (uri == null) {
            return null
        }
        var filePath: String? = null
        var cursor: Cursor? = null
        try {
            val scheme = uri.scheme
            if (TextUtils.isEmpty(scheme) || TextUtils.equals(ContentResolver.SCHEME_FILE, scheme)) {
                filePath = uri.path
            } else if (TextUtils.equals(ContentResolver.SCHEME_CONTENT, scheme)) {
                val filePathColumn = arrayOf(MediaStore.MediaColumns.DATA)
                cursor = context.contentResolver.query(uri, filePathColumn, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                        if (columnIndex > -1) {
                            filePath = cursor.getString(columnIndex)
                        }
                    }
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return filePath
    }

    /*
    * 获取位图的YUV数据
    */
    fun getYUVByBitmap(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) {
            return null
        }
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return rgb2YCbCr420(pixels, width, height)
    }

    private fun rgb2YCbCr420(pixels: IntArray, width: Int, height: Int): ByteArray? {
        val len = width * height
        // yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
        val yuv = ByteArray(len * 3 / 2)
        var y: Int
        var u: Int
        var v: Int
        for (i in 0 until height) {
            for (j in 0 until width) {
                // 屏蔽ARGB的透明度值
                val rgb = pixels[i * width + j] and 0x00FFFFFF
                // 像素的颜色顺序为bgr，移位运算。
                val r = rgb and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb shr 16 and 0xFF
                // rgb2yuv 套用公式
                y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                // 调整
                y = if (y < 16) 16 else if (y > 255) 255 else y
                u = if (u < 0) 0 else if (u > 255) 255 else u
                v = if (v < 0) 0 else if (v > 255) 255 else v
                // 赋值
                yuv[i * width + j] = y.toByte()
                yuv[len + (i shr 1) * width + (j and 1.inv()) + 0] = u.toByte()
                yuv[len + +(i shr 1) * width + (j and 1.inv()) + 1] = v.toByte()
            }
        }
        return yuv
    }
}