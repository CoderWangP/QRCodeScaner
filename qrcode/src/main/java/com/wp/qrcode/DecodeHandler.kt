package com.wp.qrcode

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.wp.qrcode.camera.CameraManager
import java.io.ByteArrayOutputStream


/**
 *
 * Created by wp on 2020/9/11.
 *
 * Description:
 *
 */
class DecodeHandler(looper: Looper, val parseCallback: (Int, Result?, Bundle?) -> Unit) : Handler(looper) {

    private val TAG = "DecodeHandler"

    private val mMultiFormatReader: MultiFormatReader = MultiFormatReader()

    private var running = true

    private var mCameraManager: CameraManager? = null

    fun setHints(hints: MutableMap<DecodeHintType, Any>) {
        mMultiFormatReader.setHints(hints)
    }

    fun setCameraManager(cameraManager: CameraManager) {
        mCameraManager = cameraManager
    }


    /**
     * 终止decode thread
     */
    fun quitSynchronously() {
        running = false
        looper.quit()
    }

    override fun handleMessage(msg: Message) {
        if (!running) {
            return
        }
        when (msg.what) {
            R.id.decode -> {
                Logger.d(TAG, "decode current thread = " + Thread.currentThread().name)
                decode(msg.obj as ByteArray, msg.arg1, msg.arg2)
            }
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
     * reuse the same reader objects from one decode to the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame. :相机预览宽
     * @param height The height of the preview frame.:相机预览高
     */
    private fun decode(data: ByteArray, width: Int, height: Int) {
        Logger.d(TAG, "decode: width = $width, height = $height")
        // 相机预览默认横屏，竖屏处理数据，portrait，竖屏
        val rectInPreview = mCameraManager?.framingRectInPreview ?: return
        val rectW = rectInPreview.width()
        val rectH = rectInPreview.height()
        val top = rectInPreview.top
        val left = rectInPreview.left
        val yEnd = left + rectW
        val xEnd = top + rectH
        Logger.d(TAG,"top = $top","yEnd = $yEnd","left = $left","xEnd = $xEnd")
        val rotatedData = ByteArray(data.size)
        for (y in left until yEnd) {
            for (x in top until xEnd) rotatedData[x * height + height - y - 1] = data[x + y * width]
        }
/*      val rectData = ByteArray(rectW * rectH)
        var i = 0
        for (y in left until yEnd) {
            for (x in top until xEnd) {
                rectData[i] = data[x + y * width]
                i++
            }
        }*/
        /**
         * 注意这里height，width 对换，处理竖屏扫描
         */
        var rawResult: Result? = null
        val source: PlanarYUVLuminanceSource? = mCameraManager?.buildLuminanceSource(rotatedData, height, width)
        var bitmap: BinaryBitmap?
        if (source != null) {
            try {
                /**
                 * 对于黑色二维码，白色背景，低端设备，低分辨率图像，GlobalHistogramBinarizer识别精度更高，
                 * 首选GlobalHistogramBinarizer，识别不出来，再构造HybridBinarizer识别
                 */
                bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                rawResult = mMultiFormatReader.decodeWithState(bitmap)
                if (rawResult == null) {
                    bitmap = BinaryBitmap(HybridBinarizer(source))
                    rawResult = mMultiFormatReader.decodeWithState(bitmap)
                }
            } catch (re: Throwable) {
                //continue
                re.printStackTrace()
                try {
                    bitmap = BinaryBitmap(HybridBinarizer(source))
                    rawResult = mMultiFormatReader.decodeWithState(bitmap)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } finally {
                mMultiFormatReader.reset()
            }
        }
        if (rawResult != null) {
            // Don't log the barcode contents for security.
            var bundle: Bundle? = null
            if (Constants.DEBUG) {
                bundle = Bundle()
                bundleThumbnail(source, bundle)
            }
            parseCallback(R.id.decode_succeeded, rawResult, bundle)
        } else {
            parseCallback(R.id.decode_failed, null, null)
        }
    }

    /**
     * 构建解析的二维码的预览缩略图
     * @param source
     * @param bundle
     */
    private fun bundleThumbnail(source: PlanarYUVLuminanceSource?, bundle: Bundle) {
        val pixels = source!!.renderThumbnail()
        val width = source.thumbnailWidth
        val height = source.thumbnailHeight
        val bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        bundle.putByteArray(Constants.BARCODE_BITMAP, out.toByteArray())
        bundle.putFloat(Constants.BARCODE_SCALED_FACTOR, width.toFloat() / source.width)
    }
}
