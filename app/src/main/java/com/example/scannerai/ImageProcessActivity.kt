package com.example.scannerai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.scannerai.AnalyzeFeatures.analyzer.ImageLabelAnalyzer
import com.example.scannerai.AnalyzeFeatures.analyzer.ObjectDetectorAnalyzer
import com.example.scannerai.AnalyzeFeatures.analyzer.TextAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

class ImageProcessActivity : AppCompatActivity() {

    var imageUri: String? = null
    lateinit var resultTextView: TextView
    lateinit var resultImageView: ImageView
    var resultText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_process)
        imageUri = intent.getStringExtra("imageUri")
        resultTextView = findViewById(R.id.tvResult)
        resultImageView = findViewById(R.id.imageFullView)
        LoadImage();
        SetTopButtons();
        SetFunctionButtons();
    }

    fun LoadImage() {
        // get image view and user glide to load image
        var imageFullView = findViewById<ImageView>(R.id.imageFullView);
        Glide.with(this).load(imageUri).into(imageFullView);
    }

    fun SetTopButtons() {
        var btnBack = findViewById<ImageView>(R.id.iv_back);
        btnBack.setOnClickListener {
            finish()
        }
    }

    fun SetFunctionButtons() {
        var btnDetect = findViewById<ImageView>(R.id.imgDetection);
        var btnLabel = findViewById<ImageView>(R.id.imgLabeling);
        var btnTextReg = findViewById<ImageView>(R.id.imgTextRecognition);
        var btnQrScan = findViewById<ImageView>(R.id.imgQRCode);

        var btnCopy = findViewById<ImageView>(R.id.imgCopy);
        var btnUndo = findViewById<ImageView>(R.id.imgUndo);

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", resultText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnUndo.setOnClickListener {
            resultTextView.setText("")
            LoadImage()
        }

        btnDetect.setOnClickListener {
            lifecycleScope.launch {
                processObjectDetection()
            }
        }

        btnLabel.setOnClickListener {
            lifecycleScope.launch {
                processLabeling()
            }
        }

        btnTextReg.setOnClickListener  {
            lifecycleScope.launch {
                processTextRecognition()
            }
        }

        btnQrScan.setOnClickListener {
            lifecycleScope.launch {
                processQRCodeDetection()
            }
        }
    }

    suspend fun processObjectDetection() = lifecycleScope.launch(Dispatchers.IO) {
        val bitmapImage = Glide.with(this@ImageProcessActivity)
            .asBitmap()
            .load(imageUri)
            .submit()
            .get()

        var resultBitmap : Bitmap? = null
        var count = 0

        do {
            val result = ObjectDetectorAnalyzer().analyzeFromBitmap(bitmapImage)
            resultBitmap = result.first
            count = result.second
        } while (count == 0)

        withContext(Dispatchers.Main) {
            if (resultBitmap != null) {
                resultImageView.setImageBitmap(resultBitmap)
                resultTextView.setText("No bounding box -> No object detected")
            } else {
                resultTextView.setText("No object detected")
            }
        }
    }

    suspend fun processLabeling() = lifecycleScope.launch(Dispatchers.IO) {
        val bitmapImage = Glide.with(this@ImageProcessActivity)
            .asBitmap()
            .load(imageUri)
            .submit()
            .get()

        resultText = "No label found"
        Log.d("LABELING", "analyzeBitmapImage: ${resultText}")
        resultText = ImageLabelAnalyzer().analyzeFromBitmap(bitmapImage)
        Log.d("LABELING1", "analyzeBitmapImage: ${resultText}")

        Log.d("TextAnalyzer: ", resultText!!)
        withContext(Dispatchers.Main) {
            if (resultText == "") resultText = "Cannot detect any label"

            var maxLengthSize = 50;

            if (resultText!!.length < maxLengthSize) {
                resultTextView.setText(resultText + "...")
            } else {
                resultTextView.setText(resultText!!.substring(0, maxLengthSize-3) + "...")
            }

            Log.d("LABELING", "resultText: $resultText")
        }
    }

    suspend fun processTextRecognition() = lifecycleScope.launch(Dispatchers.IO) {
        val bitmapImage = Glide.with(this@ImageProcessActivity)
            .asBitmap()
            .load(imageUri)
            .submit()
            .get()

        resultText = "No text found"
        resultText = TextAnalyzer().analyzeFromBitmap(bitmapImage)

        Log.d("TextAnalyzer: ", resultText!!)
        withContext(Dispatchers.Main) {
            var maxLengthSize = 50;

            if (resultText!!.length < maxLengthSize) {
                resultTextView.setText(resultText + "...")
            } else {
                resultTextView.setText(resultText!!.substring(0, maxLengthSize-3) + "...")
            }
        }
    }

    suspend fun processQRCodeDetection() = lifecycleScope.launch(Dispatchers.IO) {
        val bitmapImage = Glide.with(this@ImageProcessActivity)
            .asBitmap()
            .load(imageUri)
            .submit()
            .get()

        var resultText: String? = null
        resultText = decodeQRImage(bitmapImage)

        Log.d("QRCodeAnalyzer: ", resultText ?: "No QR code detected")
        withContext(Dispatchers.Main) {
            if (resultText != null) {
                resultTextView.setText(resultText)
            } else {
                resultTextView.setText("No QR code detected")
            }
        }
    }

    fun decodeQRImage(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            Log.e("QRCode", "Error decoding QR Code: ${e.message}")
            null
        }
    }
}