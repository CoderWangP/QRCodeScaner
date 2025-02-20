package com.wp.qrcodescaner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.google.zxing.ResultPoint
import com.trello.rxlifecycle4.android.ActivityEvent
import com.trello.rxlifecycle4.components.support.RxAppCompatActivity
import com.wp.qrcode.Logger
import com.wp.qrcode.QRCodeDecoder
import com.wp.qrcode.QRCodeView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_scan.*

/**
 *
 * Created by wp on 2020/9/18.
 *
 * Description:
 *
 */
class ScanActivity : RxAppCompatActivity() {

    private val TAG = "ScanActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        setContentView(R.layout.activity_scan)

        qrcode_view.setOnParseResultCallback(object : QRCodeView.OnParseResultCallback {
            /**
             * A valid barcode has been found, so give an indication of success and show the results.
             *
             * @param rawResult   The contents of the barcode.
             * @param scaleFactor amount by which thumbnail was scaled
             * @param barcode     A greyscale bitmap of the camera data which was decoded.
             */
            override fun onResultSuccess(rawResult: Result?, barcode: Bitmap?, scaleFactor: Float) {
                /**
                 * so beep/vibrate and we have an image to draw on
                 * beepManager.playBeepSoundAndVibrate()
                 */
                qrcode_view.playBeepSoundAndVibrate(this@ScanActivity)
                drawResultPoints(barcode, scaleFactor, rawResult)
                val scanResult = rawResult?.text
                Logger.d(TAG, "scanResult = $scanResult")
                forward2Result(rawResult?.text, barcode, rawResult?.barcodeFormat?.toString() ?: "",
                        rawResult?.timestamp ?: 0L)
            }
        })
    }

    private fun forward2Result(rawResult: String?, barcode: Bitmap?, barcodeFormat: String, time: Long) {
        val bundle = Bundle().apply {
            putString("rawResult", rawResult)
            putParcelable("barcode", barcode)
            putString("barcodeFormat", barcodeFormat)
            putLong("time", time)
        }
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtras(bundle)
        }
        startActivity(intent)
    }

    fun choosePhoto(v: View) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
//            val picturePath = Util.getFilePathFromUri(this,uri)?: return
            Logger.d(TAG, "uri = $uri")
            decodePicture(uri)
        }
    }

    private fun decodePicture(uri: Uri) {
        Observable.just(uri)
                .map {
                    QRCodeDecoder.decodeQRCode(this@ScanActivity, uri)
                }
                .compose(bindUntilEvent(ActivityEvent.DESTROY))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it != null) {
                        qrcode_view.playBeepSoundAndVibrate(this)
                    }
                    val text = it?.text
                    forward2Result(text, null, it?.barcodeFormat?.toString()
                            ?: "", System.currentTimeMillis())
                }, {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                })
    }

    override fun onResume() {
        super.onResume()
        qrcode_view.startPreview()
    }

    override fun onPause() {
        super.onPause()
        qrcode_view.stopPreview()
    }


    /**
     * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
     *
     * @param barcode     A bitmap of the captured image.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param rawResult   The decoded results which contains the points to draw.
     */
    private fun drawResultPoints(barcode: Bitmap?, scaleFactor: Float, rawResult: Result?) {
        if (barcode == null || rawResult == null) {
            return
        }
        val points = rawResult.resultPoints
        if (points != null && points.isNotEmpty()) {
            val canvas = Canvas(barcode)
            val paint = Paint()
            paint.color = Color.parseColor("#c099cc00")
            if (points.size == 2) {
                paint.strokeWidth = 4.0f
                drawLine(canvas, paint, points[0], points[1], scaleFactor)
            } else if (points.size == 4 &&
                    (rawResult.barcodeFormat == BarcodeFormat.UPC_A ||
                            rawResult.barcodeFormat == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1], scaleFactor)
                drawLine(canvas, paint, points[2], points[3], scaleFactor)
            } else {
                paint.strokeWidth = 10.0f
                for (point in points) {
                    if (point != null) {
                        canvas.drawPoint(scaleFactor * point.x, scaleFactor * point.y, paint)
                    }
                }
            }
        }
    }

    private fun drawLine(canvas: Canvas, paint: Paint, a: ResultPoint?, b: ResultPoint?, scaleFactor: Float) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.x,
                    scaleFactor * a.y,
                    scaleFactor * b.x,
                    scaleFactor * b.y,
                    paint)
        }
    }
}