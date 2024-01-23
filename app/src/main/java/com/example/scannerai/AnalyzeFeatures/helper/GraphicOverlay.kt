package com.example.scannerai.AnalyzeFeatures.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GraphicOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val boxes: MutableList<Box> = ArrayList()
    fun clear() {
        boxes.clear()
        postInvalidate()
    }

    fun addBox(box: Box) {
        boxes.add(box)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) {
            canvas.drawRect(box.left, box.top, box.right, box.bottom, box.paint)
            if (box.label != null) {
                val textPaint = Paint()
                textPaint.color = Color.WHITE
                textPaint.textSize = 40f
                canvas.drawText(box.label!!, box.left, box.top, textPaint)
            }
        }
    }

    class Box(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var paint: Paint,
        var label: String?
    )
}
