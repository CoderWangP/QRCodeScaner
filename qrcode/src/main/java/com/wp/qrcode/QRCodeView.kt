package com.wp.qrcode

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.Result
import com.wp.qrcode.camera.CameraManager
import java.util.*

/**
 *
 * Created by wp on 2020/9/11.
 *
 * Description:
 *
 */
class QRCodeView : FrameLayout{

    private val TAG = "QRCodeView"

    /**
     * 预览surfaceView
     */
    private lateinit var mCameraPreview: CameraPreview

    /**
     * 扫描框及蒙版
     */
    private lateinit var mScanBoxView: ScanBoxView

    /**
     * 支持解码格式
     */
    private lateinit var mHints: MutableMap<DecodeHintType, Any>


    /**
     * 震动声音管理器
     */
    private var mBeepManager:BeepManager? = null



    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.QRCodeView)
        array.recycle()

        mCameraPreview = CameraPreview(context,attrs)
        addView(mCameraPreview,LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)

        mScanBoxView = ScanBoxView(context,attrs)
        addView(mScanBoxView,LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)
        initDecodeFormats()
        registerListener()
    }

    /**
     * 注册监听，回调
     */
    private fun registerListener() {
        mCameraPreview.setOnPreviewStatusChangedCallback(object :CameraPreview.OnPreviewStatusChangedCallback{
            override fun onPreviewReady(cameraManager: CameraManager) {
                mScanBoxView.setCameraManager(cameraManager)
            }

            override fun onPreviewClose() {

            }
        })
    }

    /**
     * 初始化解析格式
     */
    private fun initDecodeFormats() {
        mHints = EnumMap(DecodeHintType::class.java)
        val decodeFormats = EnumSet.noneOf(BarcodeFormat::class.java)
        decodeFormats.add(BarcodeFormat.QR_CODE)
        mHints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
    }

    /**
     * 初始化相机参数，并开启预览
     */
    fun startPreview() {
        mCameraPreview.startPreview(mHints)
        mBeepManager?.openMediaPlayer()
    }

    /**
     * 播放声音震动
     */
    fun playBeepSoundAndVibrate(activity: Activity){
        if(mBeepManager == null){
            mBeepManager = BeepManager(activity)
        }
        mBeepManager?.playBeepSoundAndVibrate()
    }

    /**
     * 停止预览，释放资源
     */
    fun stopPreview(){
        mCameraPreview.stopPreview()
        mBeepManager?.close()
    }




    /***************解析结果回调****************/
    interface OnParseResultCallback {
        fun onResultSuccess(rawResult: Result?, barcode: Bitmap?, scaleFactor: Float)
    }

    fun setOnParseResultCallback(onParseResultCallback: OnParseResultCallback) {
        mCameraPreview.setOnParseResultCallback(onParseResultCallback)
    }
    /***************解析结果回调****************/
}