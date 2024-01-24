package com.example.scannerai.AnalyzeFeatures.analyzer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.scannerai.AnalyzeFeatures.helper.GraphicOverlay
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ObjectDetectorAnalyzer : ImageAnalysis.Analyzer {
    private var graphicOverlay: GraphicOverlay? = null
    private var previewView: PreviewView? = null

    constructor()
    constructor(graphicOverlay: GraphicOverlay?, previewView: PreviewView?) {
        this.graphicOverlay = graphicOverlay
        this.previewView = previewView
    }

    fun setGraphicOverlay(graphicOverlay: GraphicOverlay?) {
        this.graphicOverlay = graphicOverlay
    }

    fun setPreviewView(previewView: PreviewView?) {
        this.previewView = previewView
    }

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification() // Optional
            .build()
    )

    private fun scaleFactorWidth(imageProxy: ImageProxy): Float {
        val w = imageProxy.height.toFloat()
        val screenW = previewView!!.width.toFloat()
        Log.d("SCALE", "scaleFactorWidth: $screenW $w")
        return screenW / w
    }

    private fun scaleFactorHeight(imageProxy: ImageProxy): Float {
        val h = imageProxy.width.toFloat()
        val screenH = previewView!!.height.toFloat()
        Log.d("SCALE", "scaleFactorHeight: $screenH $h")
        return screenH / h
    }

    @OptIn(markerClass = arrayOf(ExperimentalGetImage::class))
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects: List<DetectedObject> ->
                    graphicOverlay!!.clear()
                    for (detectedObject in detectedObjects) {
                        val boundingBox = detectedObject.boundingBox
                        // Tạo và thiết lập paint cho bounding box
                        val paint = Paint()
                        paint.color = Color.RED
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 8.0f
                        boundingBox.left = (boundingBox.left * scaleFactorWidth(imageProxy)).toInt()
                        boundingBox.right =
                            (boundingBox.right * scaleFactorWidth(imageProxy)).toInt()
                        boundingBox.top = (boundingBox.top * scaleFactorHeight(imageProxy)).toInt()
                        boundingBox.bottom =
                            (boundingBox.bottom * scaleFactorHeight(imageProxy)).toInt()
                        if (detectedObject.labels.size > 0) {
                            val box = GraphicOverlay.Box(
                                boundingBox.left.toFloat(),
                                boundingBox.top.toFloat(),
                                boundingBox.right.toFloat(),
                                boundingBox.bottom.toFloat(),
                                paint,
                                detectedObject.labels[0].text
                            )
                            graphicOverlay!!.addBox(box)
                        }
                    }
                }
                .addOnFailureListener { e: Exception -> Log.d("LABEL 2", "Format = " + e.message) }
                .addOnCompleteListener {
                    // [START_EXCLUDE]
                    imageProxy.close()
                    // [END_EXCLUDE]
                }
        }
    }

    suspend fun analyzeFromBitmap(bitmap: Bitmap): Pair<Bitmap, Int> = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val res = bitmap.copy(bitmap.getConfig(), true)
        val canvas = Canvas(res)
        var count = 0

        objectDetector.process(image)
            .addOnSuccessListener { detectedObjects: List<DetectedObject> ->
                Log.d("OBJECT NUMBER", "Count = " + count)

                for (detectedObject in detectedObjects) {
                    val boundingBox = detectedObject.boundingBox
                    Log.d("BOUNDING BOX", "Box = " + boundingBox.toString())
                    val paint1 = Paint().apply {
                        color = Color.RED
                        style = Paint.Style.STROKE
                        strokeWidth = 8.0f
                    }
                    val paint2 = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                        strokeWidth = 3.0f
                        textSize = 50f
                    }
                    if (detectedObject.labels.isNotEmpty()) {
                        count++
                        canvas.drawRect(boundingBox, paint1)
                        canvas.drawText(
                            detectedObject.labels[0].text,
                            boundingBox.left.toFloat(),
                            boundingBox.top.toFloat(),
                            paint2
                        )
                    }
                }
                continuation.resume(Pair(res, count))
            }
            .addOnFailureListener { e: Exception ->
                Log.d("LABEL 2", "Format = " + e.message)
                continuation.resumeWithException(e)
            }
    }

    fun toBitmap(mediaImage: Image?): Bitmap {
        return if (mediaImage != null) {
            val planes = mediaImage.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer[nv21, 0, ySize]
            uBuffer[nv21, ySize, uSize]
            vBuffer[nv21, ySize + uSize, vSize]
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                mediaImage.width,
                mediaImage.height,
                null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, yuvImage.width, yuvImage.height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } else {
            throw IllegalStateException("MediaImage is null")
        }
    }

    companion object {
        fun saveImageFromProxy(context: Context, imageProxy: ImageProxy) {
            // Trích xuất dữ liệu hình ảnh từ ImageProxy
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer[data]

            // Tạo tên tệp và đường dẫn cho ảnh
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "IMG_$timeStamp.jpg"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            // Tạo tệp mới và lưu dữ liệu hình ảnh vào đó
            val imageFile = File(storageDir, imageFileName)
            try {
                val os: OutputStream = FileOutputStream(imageFile)
                os.write(data)
                os.close()

                // Cập nhật thông tin hình ảnh vào MediaStore để hiển thị trong thư viện ảnh
                val values = ContentValues()
                values.put(MediaStore.Images.Media.TITLE, imageFileName)
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                values.put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                // Quét tệp để hiển thị trong thư viện ảnh ngay lập tức
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    null,
                    null
                )

                // Đóng ImageProxy
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
