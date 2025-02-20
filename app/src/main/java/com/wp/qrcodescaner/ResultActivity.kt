package com.wp.qrcodescaner

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_result.*
import java.text.DateFormat

/**
 *
 * Created by wp on 2020/9/18.
 *
 * Description:
 *
 */
class ResultActivity :Activity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        updateUI()
    }

    private fun updateUI() {
        val bundle = intent.extras ?: return
        with(bundle){
            val rawResult = getString("rawResult")
            val barcode:Bitmap? = getParcelable("barcode")
            val barcodeFormat = getString("barcodeFormat")
            val time = getLong("time")

            image_barcode.setImageBitmap(barcode)
            tx_barcode_text.text = rawResult
            tx_barcode_type.text = "格式：$barcodeFormat"
            val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            tx_barcode_time.text = "时间：${formatter.format(time)}"
        }
    }
}