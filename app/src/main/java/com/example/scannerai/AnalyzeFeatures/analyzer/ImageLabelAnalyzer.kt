package com.example.scannerai.AnalyzeFeatures.analyzer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope

class ImageLabelAnalyzer(
    var labelView: TextView? = null
) : ImageAnalysis.Analyzer {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    @OptIn(markerClass = arrayOf(ExperimentalGetImage::class))
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    // Task completed successfully
                    for (label in labels) {
                        Log.d(
                            "LABEL 2", """
                             Format = ${label.text}
                             Value = ${label.confidence}
                             """.trimIndent()
                        )
                        // Assuming labelDone and labelAcc are declared elsewhere in your class
                        labelDone = label.text
                        labelAcc = label.confidence.toDouble()
                        mainHandler.post { // Cập nhật TextView
                            labelView?.text = String.format("%s: %.3f", labelDone, labelAcc)
                        }
                    }
                }
                .addOnFailureListener { e -> Log.d("LABEL", "Detection failed", e) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            Log.d("LABEL", "mediaImage is null")
            imageProxy.close()
        }
    }

    suspend fun analyzeFromBitmap(inputImage: Bitmap): String = coroutineScope {
        val resultText = CompletableDeferred<String>()

        Log.d("LABELING", "analyzeBitmapImage: ${inputImage.height} x ${inputImage.width}")

        labeler.process(InputImage.fromBitmap(inputImage, 0))
            .addOnSuccessListener { labels ->
                // find the label with the highest confidence and return it
                var maxConfidence = 0f
                var maxLabel = ""
                for (label in labels) {
                    if (label.confidence > maxConfidence) {
                        maxConfidence = label.confidence
                        maxLabel = label.text
                    }
                }
                resultText.complete(maxLabel)
                Log.d("LABELING", "resultText: $resultText")
            }
            .addOnFailureListener { e ->
                Log.d("LABELING", "Labeling failed", e)
                resultText.complete("No label detected")
            }
        resultText.await()
    }

    var labelDone = ""
    var labelAcc = 0.0
}
