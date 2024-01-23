package com.example.scannerai.AnalyzeFeatures.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.scannerai.AnalyzeFeatures.analyzer.TextAnalyzer
import com.example.scannerai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageProcessActivity : AppCompatActivity() {

    var imageUri: String? = null
    lateinit var resultTextView: TextView
    var resultText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_process)
        imageUri = intent.getStringExtra("imageUri")
        resultTextView = findViewById(R.id.tvResult)
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
            Toast.makeText(this, "Detecting...", Toast.LENGTH_SHORT).show()
        }

        btnLabel.setOnClickListener {
            Toast.makeText(this, "Labeling...", Toast.LENGTH_SHORT).show()
        }

        btnTextReg.setOnClickListener  {
            lifecycleScope.launch {
                processTextRecognition()
            }
        }

        btnQrScan.setOnClickListener {
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun processTextRecognition() = lifecycleScope.launch(Dispatchers.IO) {
        val bitmapImage = Glide.with(this@ImageProcessActivity)
            .asBitmap()
            .load(imageUri)
            .submit()
            .get()

        resultText = "No text found"
        resultText = TextAnalyzer().analyzeBitmapImage(bitmapImage)

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
}