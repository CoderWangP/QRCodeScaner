package com.wp.qrcodescaner

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.tbruyelle.rxpermissions3.RxPermissions
import com.trello.rxlifecycle4.android.ActivityEvent
import com.trello.rxlifecycle4.components.support.RxAppCompatActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class MainActivity : RxAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    fun scan(view: View) {
        if(ClickUtils.isFastClick(view)){
            return
        }
        RxPermissions(this).request(Manifest.permission.CAMERA)
                .compose(bindUntilEvent(ActivityEvent.DESTROY))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { aBoolean ->
                    if (aBoolean) {
                        startActivity(Intent(this, ScanActivity::class.java))
                    } else {
                        //用户在设置中关闭了权限，或者选择了不再弹出
                        showOpenPermissionDialog()
                    }
                }
    }

    fun create(view: View?) {
        startActivity(Intent(this,CreateActivity::class.java))
    }

    fun zxing(view: View?) {
    }


    private fun showOpenPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        // 设置参数
        builder.setMessage("无法获取摄像头数据，请检查是否已经打开摄像头权限。")
                .setPositiveButton("确定") { dialog, which -> }
        val alertDialog = builder.create()
        alertDialog.show()
        val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        if (positiveButton != null) {
            positiveButton.isAllCaps = false
            positiveButton.setTextColor(Color.parseColor("#27ADC7"))
        }
    }
}

