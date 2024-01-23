package com.example.scannerai.AnalyzeFeatures.analyzer

import android.graphics.Bitmap
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope

class TextAnalyzer(
    var textView: TextView? = null,
    var startReg: Button? = null,
    var preview: PreviewView? = null) :

    ImageAnalysis.Analyzer {
    private var isAnalysisEnabled = false
    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        startReg?.setOnClickListener { v: View? -> enableAnalysis() }
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
                        textView?.text = resultText
                    }
                    preview?.let { ChangePreviewViewHeight(it, DeviceMetric.PREVIEWVVIEWDEFAULTHEIGHT) }
                    disableAnalysis()
                }
                .addOnFailureListener { e -> Log.d("TEXT_RECOGNITION", "Detection failed", e) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            Log.d("TEXT_RECOGNITION", "mediaImage is null")
            imageProxy.close()
        }
    }

    suspend fun analyzeFromBitmap(inputImage: Bitmap): String = coroutineScope {
        val resultText = CompletableDeferred<String>()

        Log.d("TEXT_RECOGNITION", "analyzeBitmapImage: ${inputImage.height} x ${inputImage.width}")

        recognizer.process(InputImage.fromBitmap(inputImage, 0))
            .addOnSuccessListener { text ->
                resultText.complete(text.text)
                Log.d("TEXT_RECOGNITION", "${text.text}")
            }
            .addOnFailureListener { e ->
                Log.d("TEXT_RECOGNITION", "Detection failed", e)
                resultText.complete("No text detected")
            }

        resultText.await()
    }
}