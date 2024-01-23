package com.example.scannerai.AnalyzeFeatures.analyzer

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.scannerai.AnalyzeFeatures.helper.Animation.ChangePreviewViewHeight
import com.example.scannerai.AnalyzeFeatures.helper.DeviceMetric
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextAnalyzer(var textView: TextView, var startReg: Button, var preview: PreviewView) :
    ImageAnalysis.Analyzer {
    private var isAnalysisEnabled = false
    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        startReg.setOnClickListener { v: View? -> enableAnalysis() }
    }

    fun enableAnalysis() {
        isAnalysisEnabled = true
    }

    fun disableAnalysis() {
        isAnalysisEnabled = false
    }

    @OptIn(markerClass = arrayOf(ExperimentalGetImage::class))
    override fun analyze(imageProxy: ImageProxy) {
        if (!isAnalysisEnabled) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { text -> // Task completed successfully
                    val resultText = text.text
                    mainHandler.post { // Update TextView
                        textView.text = resultText
                    }
                    ChangePreviewViewHeight(preview, DeviceMetric.PREVIEWVVIEWDEFAULTHEIGHT)
                    disableAnalysis()
                }
                .addOnFailureListener { e -> Log.d("TEXT_RECOGNITION", "Detection failed", e) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            Log.d("TEXT_RECOGNITION", "mediaImage is null")
            imageProxy.close()
        }
    }
}