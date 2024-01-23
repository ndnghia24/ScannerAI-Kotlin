package com.example.scannerai.AnalyzeFeatures.analyzer

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

class ImageLabelAnalyzer(var labelView: TextView) : ImageAnalysis.Analyzer {
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
                            labelView.text = String.format("%s: %.3f", labelDone, labelAcc)
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

    var labelDone = ""
    var labelAcc = 0.0
}
