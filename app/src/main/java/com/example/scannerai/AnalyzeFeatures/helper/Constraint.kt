package com.example.scannerai.AnalyzeFeatures.helper

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

object Constraint {
    fun setConstaintToParent(view: View) {
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        view.layoutParams = params
    }
}
