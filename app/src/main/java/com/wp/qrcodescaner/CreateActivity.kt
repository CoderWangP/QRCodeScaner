package com.wp.qrcodescaner

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.wp.qrcode.QRCodeEncoder
import kotlinx.android.synthetic.main.activity_create.*

/**
 *
 * Created by wp on 2020/10/21.
 *
 * Description:
 *
 */
class CreateActivity :Activity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)
    }


    fun create(v:View){
        val text = et_qr_content.text.toString()
        if(text.isEmpty()){
            return
        }
        val bitmap = QRCodeEncoder.encodeAsBitmap(text,dp2px(200f),dp2px(200f))
        image_qr.setImageBitmap(bitmap)
    }


    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    private fun dp2px(dpValue: Float): Int {
        val scale: Float = resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }
}