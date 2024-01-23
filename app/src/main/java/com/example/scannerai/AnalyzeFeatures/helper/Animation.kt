package com.example.scannerai.AnalyzeFeatures.helper

import android.animation.ValueAnimator
import androidx.camera.view.PreviewView

object Animation {
    @JvmStatic
    fun ChangePreviewViewHeight(previewView: PreviewView, height: Int) {
        val currentHeight = previewView.height
        val anim = ValueAnimator.ofInt(currentHeight, height)
        anim.setDuration(100)
        anim.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            val layoutParams = previewView.layoutParams
            layoutParams.height = animatedValue
            previewView.layoutParams = layoutParams
        }
        anim.start()
    }
}
