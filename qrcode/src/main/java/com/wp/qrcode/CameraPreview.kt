package com.wp.qrcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.hardware.Camera
import android.os.Bundle
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.zxing.DecodeHintType
import com.google.zxing.Result
import com.wp.qrcode.camera.CameraManager
import java.io.IOException

/**
 *
 * Created by wp on 2020/9/16.
 *
 * Description:
 * 当Activity完全显示之后，SurfaceView才会被创建
 * 只要Activity不是在前台，SurfaceView就会销毁
 */
class CameraPreview : SurfaceView, SurfaceHolder.Callback, Camera.PreviewCallback {
    private val TAG = "CameraPreview"

    private var hasSurface = false
    private lateinit var cameraManager: CameraManager

    private var decodeHandler: DecodeHandler? = null

    private lateinit var hints: MutableMap<DecodeHintType, Any>


    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context?, attrs: AttributeSet?) {

    }

    /**
     * 不在前台，surfaceView都会销毁，surfaceView销毁之后，
     * camera也没必要继续打开，与camera相关的资源都可以释放，
     * 所以重新preview时，需要重新初始化相机相关组件
     */
    fun startPreview(hints: MutableMap<DecodeHintType, Any>) {
        Logger.d(TAG, "startPreview")
        this.hints = hints
        cameraManager = CameraManager(context)
        if (hasSurface) {
            /**
             * The activity was paused but not stopped, so the surface still exists. Therefore
             * surfaceCreated() won't be called, so init the camera here.
             */
            initCamera(holder)
        } else {
            /**
             * Install the callback and wait for surfaceCreated() to init the camera.
             */
            holder.addCallback(this)
        }
    }

    fun stopPreview() {
        Logger.d(TAG, "stopPreview")
        /**
         * 停止预览
         */
        cameraManager.stopPreview()
        /**
         * 终止decode thread，释放decodeHandler
         */
        decodeHandler?.quitSynchronously()
        decodeHandler = null
        /**
         * 释放相机资源
         */
        cameraManager.closeDriver()
        if (!hasSurface) {
            //surface已销毁，清除callback
            holder.removeCallback(this)
        }
        mOnPreviewStatusChangedCallback?.onPreviewClose()
    }


    /****************SurfaceView***************/
    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated")
        hasSurface = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG, "surfaceChanged")
        if (holder == null) {
            throw IllegalStateException("surfaceCreated() gave us a null surface!")
        }
        stopPreview()
        initCamera(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceDestroyed")
        hasSurface = false
    }

    /****************SurfaceView***************/

    /**
     * 初始化相机，开启预览
     */
    private fun initCamera(holder: SurfaceHolder?) {
        if (holder == null) {
            throw IllegalStateException("surfaceCreated() gave us a null surface!")
        }
        if (cameraManager.isOpen) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?")
            return
        }
        try {
            /**
             * 打开相机
             */
            cameraManager.openDriver(holder)
            /**
             * 打开预览
             */
            cameraManager.startPreview()
            /**
             * 开启预览
             */
            mOnPreviewStatusChangedCallback?.onPreviewReady(cameraManager)
            /**
             * 初始化解析二维码线程及handler
             */
            if (decodeHandler == null) {
                val decodeThread = HandlerThread("decode_thread")
                decodeThread.start()
                decodeHandler = DecodeHandler(decodeThread.looper, parseCallback)
            }
            decodeHandler?.setHints(hints)
            decodeHandler?.setCameraManager(cameraManager)
            /**
             * 相机预览已准备就绪，单次请求一帧数据，解析
             */
            cameraManager.requestPreviewFrame(this)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            Logger.e(TAG, ioe.message)
        } catch (re: RuntimeException) {
            re.printStackTrace()
            Logger.e(TAG, re.message)
        }
    }


    /**
     * 解析回调
     */
    private val parseCallback = { id: Int, result: Result?, bundle: Bundle? ->
        Unit
        when (id) {
            R.id.decode_succeeded -> {
                var barcode: Bitmap? = null
                var scaleFactor = 1.0f
                if (bundle != null) {
                    val compressedBitmap = bundle.getByteArray(Constants.BARCODE_BITMAP)
                    if (compressedBitmap != null) {
                        barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.size, null)
                        // Mutable copy:
                        barcode = barcode?.copy(Bitmap.Config.ARGB_8888, true)
                    }
                    scaleFactor = bundle.getFloat(Constants.BARCODE_SCALED_FACTOR)
                }
                post {
                    /**
                     * A valid barcode has been found, so give an indication of success and show the results.
                     * @param result     The contents of the barcode.
                     * @param scaleFactor  amount by which thumbnail was scaled
                     * @param barcode     A greyscale bitmap of the camera data which was decoded.
                     */
                    mOnParseResultCallback?.onResultSuccess(result, barcode, scaleFactor)
                }
            }
            R.id.decode_failed -> {
                Logger.d(TAG, "decode current thread = " + Thread.currentThread().name)
                // We're decoding as fast as possible, so when one decode fails, start another.
                post {
                    Logger.d(TAG, "current thread = " + Thread.currentThread().name)
                    cameraManager.requestPreviewFrame(this)
                }
            }
        }
        Unit
    }

    /***************Camera.PreviewCallback****************/
    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        /**
         * Preview frames are delivered here
         */
        Logger.d(TAG, "onPreviewFrame Callback")
        val cameraResolution: Point = cameraManager.cameraResolution ?: return

        /**
         * 预览回调一帧数据，交给decode thread 去处理
         * [com.wp.qrcode.DecodeHandler]
         * message [R.id.decode]
         */
        val message: Message? = decodeHandler?.obtainMessage(R.id.decode, cameraResolution.x,
                cameraResolution.y, data)
        message?.sendToTarget()
    }
    /***************Camera.PreviewCallback****************/


    private var mOnParseResultCallback: QRCodeView.OnParseResultCallback? = null

    fun setOnParseResultCallback(onParseResultCallback: QRCodeView.OnParseResultCallback) {
        this.mOnParseResultCallback = onParseResultCallback
    }

    private var mOnPreviewStatusChangedCallback: OnPreviewStatusChangedCallback? = null

    interface OnPreviewStatusChangedCallback {
        fun onPreviewReady(cameraManager: CameraManager)
        fun onPreviewClose()
    }

    fun setOnPreviewStatusChangedCallback(onPreviewStatusChangedCallback: OnPreviewStatusChangedCallback) {
        this.mOnPreviewStatusChangedCallback = onPreviewStatusChangedCallback
    }
}