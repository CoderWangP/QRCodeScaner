package com.wp.qrcode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.wp.qrcode.camera.CameraManager

/**
 *
 * Created by wp on 2020/9/14.
 *
 * Description: 扫描框及蒙版，动画等View
 *
 */
class ScanBoxView : View {

    private val TAG = "ScanBoxView"

    /**
     * 相机管理，包含一些配置信息
     */
    private var cameraManager: CameraManager? = null

    /**
     * 非高亮区颜色
     */
    private var maskColor = 0x60000000

    /**
     * 二维码捕捉区矩形框线颜色
     */
    @ColorInt
    private var borderColor:Int = Color.WHITE

    /**
     * 二维码捕捉区矩形框线宽度
     */
    private var borderWidth: Float = Util.dp2px(context, 2f).toFloat()

    /**
     * 角的颜色
     */
    private var cornerColor: Int = Color.parseColor("#45DDDD")

    /**
     * 四个角宽度
     */
    private var cornerWidth: Float = Util.dp2px(context, 4f).toFloat()

    /**
     * 角的线长度
     */
    private var cornerLength: Float = Util.dp2px(context, 20f).toFloat()

    /**
     * 蒙版画笔
     */
    private lateinit var mMaskPaint: Paint

    /**
     * 高亮矩形框
     */
    private lateinit var mRectPaint: Paint

    /**
     * 扫描线
     */
    private lateinit var scanLineBitmap: Bitmap

    /**
     * 扫描线移动步长
     */
    private var moveStepDistance = Util.dp2px(context, 2f)

    /**
     * 扫描线画笔
     */
    private lateinit var scanDrawablePaint: Paint

    /**
     * 扫描线位置rect
     */
    private lateinit var scanDrawableRect: RectF


    /**
     * 动画间隔
     */
    private val animDelayTime = 10L

    /**
     * 动画扫描线可反转移动
     */
    private var scanLineReverse: Boolean = false


    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    @SuppressLint("CustomViewStyleable")
    private fun init(context: Context?, attrs: AttributeSet?) {
        if (context == null) {
            return
        }
        if (attrs == null) {
            return
        }
        val array = context.obtainStyledAttributes(attrs, R.styleable.QRCodeView)
        maskColor = array.getColor(R.styleable.QRCodeView_mask_color, maskColor)
        borderColor = array.getColor(R.styleable.QRCodeView_scan_rect_border_color, borderColor)
        borderWidth = array.getDimensionPixelSize(R.styleable.QRCodeView_scan_rect_border_width, borderWidth.toInt()).toFloat()
        cornerColor = array.getColor(R.styleable.QRCodeView_scan_rect_corner_color, cornerColor)
        cornerWidth = array.getDimensionPixelSize(R.styleable.QRCodeView_scan_rect_corner_width, cornerWidth.toInt()).toFloat()
        cornerLength = array.getDimensionPixelSize(R.styleable.QRCodeView_scan_rect_corner_length, cornerLength.toInt()).toFloat()
        scanLineBitmap = ((array.getDrawable(R.styleable.QRCodeView_scan_line_drawable)
                ?: ContextCompat.getDrawable(context, R.drawable.scan_light)) as BitmapDrawable).bitmap
        scanLineReverse = array.getBoolean(R.styleable.QRCodeView_scan_line_reverse, scanLineReverse)
        array.recycle()

        mMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mMaskPaint.style = Paint.Style.FILL
        mMaskPaint.color = maskColor

        mRectPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        scanDrawablePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        scanDrawableRect = RectF()
    }

    fun setCameraManager(cameraManager: CameraManager?) {
        if (cameraManager == null) {
            return
        }
        this.cameraManager = cameraManager
        val frameRect: Rect = cameraManager?.framingRect ?: return
        val halfCornerWidth = cornerWidth / 2.0f
        with(scanDrawableRect) {
            left = frameRect.left + halfCornerWidth
            top = frameRect.top + halfCornerWidth
            right = frameRect.right - halfCornerWidth
            bottom = this.top + scanLineBitmap.height
        }
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cameraManager == null) {
            return  // not ready yet, early draw before done configuring
        }
        val frame: Rect = cameraManager?.framingRect ?: return
        cameraManager?.framingRectInPreview ?: return
        Logger.d(TAG,"ScanBoxView invalidate")
        drawMask(canvas, frame)
        drawBorderLine(canvas, frame)
        drawCorner(canvas, frame)
        drawScanDrawable(canvas, frame)
        moveScanDrawable(frame)
    }


    /**
     * 画遮罩层
     */
    private fun drawMask(canvas: Canvas, frame: Rect) {
        val width = width
        val height = height
        //绘制蒙版
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), mMaskPaint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), frame.bottom + 1.toFloat(), mMaskPaint)
        canvas.drawRect(frame.right + 1.toFloat(), frame.top.toFloat(), width.toFloat(), frame.bottom + 1.toFloat(), mMaskPaint)
        canvas.drawRect(0f, frame.bottom + 1.toFloat(), width.toFloat(), height.toFloat(), mMaskPaint)
    }

    /**
     * 画边框线
     */
    private fun drawBorderLine(canvas: Canvas, frameRect: Rect) {
        if (borderWidth > 0) {
            with(mRectPaint) {
                style = Paint.Style.STROKE
                color = borderColor
                strokeWidth = borderWidth
            }
            canvas.drawRect(frameRect, mRectPaint)
        }
    }

    /**
     * 画角
     */
    private fun drawCorner(canvas: Canvas, frameRect: Rect) {
        if (cornerWidth > 0) {
            with(mRectPaint) {
                style = Paint.Style.STROKE
                color = cornerColor
                strokeWidth = cornerWidth
            }
            //左上
            canvas.drawLine(frameRect.left.toFloat() - borderWidth, frameRect.top.toFloat(), frameRect.left + cornerLength,
                    frameRect.top.toFloat(), mRectPaint)
            canvas.drawLine(frameRect.left.toFloat(), frameRect.top.toFloat(), frameRect.left.toFloat(),
                    frameRect.top + cornerLength, mRectPaint)

            //右上
            canvas.drawLine(frameRect.right.toFloat() + borderWidth, frameRect.top.toFloat(),
                    frameRect.right - cornerLength, frameRect.top.toFloat(), mRectPaint)
            canvas.drawLine(frameRect.right.toFloat(), frameRect.top.toFloat(), frameRect.right.toFloat(),
                    frameRect.top + cornerLength, mRectPaint)

            //左下
            canvas.drawLine(frameRect.left.toFloat() - borderWidth, frameRect.bottom.toFloat(), frameRect.left + cornerLength,
                    frameRect.bottom.toFloat(), mRectPaint)
            canvas.drawLine(frameRect.left.toFloat(), frameRect.bottom.toFloat(), frameRect.left.toFloat(),
                    frameRect.bottom - cornerLength, mRectPaint)

            //右下
            canvas.drawLine(frameRect.right.toFloat() + borderWidth, frameRect.bottom.toFloat(), frameRect.right - cornerLength,
                    frameRect.bottom.toFloat(), mRectPaint)
            canvas.drawLine(frameRect.right.toFloat(), frameRect.bottom.toFloat(), frameRect.right.toFloat(),
                    frameRect.bottom - cornerLength, mRectPaint)
        }
    }


    /**
     * 画扫描线
     */
    private fun drawScanDrawable(canvas: Canvas, frameRect: Rect) {
        canvas.drawBitmap(scanLineBitmap, null, scanDrawableRect, scanDrawablePaint)
    }


    /**
     * 开启动画
     */
    private fun moveScanDrawable(frame: Rect) {
        var scanLineTop = scanDrawableRect.top
        // 处理非网格扫描图片的情况
        scanLineTop += moveStepDistance
        val scanLineHeight: Int = scanLineBitmap.height
        val halfCornerWidth = cornerWidth / 2.0f
        if (scanLineReverse) {
            if (scanLineTop + scanLineHeight > frame.bottom - halfCornerWidth
                    || scanLineTop < frame.top + halfCornerWidth) {
                moveStepDistance = -moveStepDistance
            }
        } else {
            if (scanLineTop + scanLineHeight > frame.bottom - halfCornerWidth) {
                scanLineTop = frame.top + halfCornerWidth + 0.5f
            }
        }
        with(scanDrawableRect) {
            left = frame.left + halfCornerWidth
            top = scanLineTop
            right = frame.right - halfCornerWidth
            bottom = this.top + scanLineHeight
        }
        postInvalidateDelayed(animDelayTime,frame.left,frame.top,frame.right,frame.bottom)
    }
}