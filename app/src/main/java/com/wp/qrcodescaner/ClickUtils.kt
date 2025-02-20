package com.wp.qrcodescaner

import android.view.View

object ClickUtils {
    private const val TIME: Long = 500
    private var sLastClickTime: Long = 0
    private var sLastClickViewId = 0

    @Synchronized
    fun isFastClick(v: View): Boolean {
        val time = System.currentTimeMillis()
        return if (time - sLastClickTime < TIME && sLastClickViewId == v.id) {
            true
        } else {
            sLastClickTime = time
            sLastClickViewId = v.id
            false
        }
    }
}