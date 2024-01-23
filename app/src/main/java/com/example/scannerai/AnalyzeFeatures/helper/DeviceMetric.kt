package com.example.scannerai.AnalyzeFeatures.helper

import android.content.Context
import android.util.DisplayMetrics

object DeviceMetric {
    @JvmField
    var PREVIEWVVIEWDEFAULTHEIGHT = 0
    @JvmField
    var PREVIEWVIEWMAXHEIGHT = 0
    fun getDeviceWidthInDp(displayMetrics: DisplayMetrics?): Int {
        return if (displayMetrics != null) {
            (displayMetrics.widthPixels / displayMetrics.density).toInt()
        } else 0
    }

    fun getDeviceWidthInPx(displayMetrics: DisplayMetrics?): Int {
        return if (displayMetrics != null) {
            displayMetrics.widthPixels
        } else 0
    }

    fun getDeviceHeightInDp(displayMetrics: DisplayMetrics?): Int {
        return if (displayMetrics != null) {
            (displayMetrics.heightPixels / displayMetrics.density).toInt()
        } else 0
    }

    fun getDeviceHeightInPx(displayMetrics: DisplayMetrics?): Int {
        return if (displayMetrics != null) {
            displayMetrics.heightPixels
        } else 0
    }

    fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
