package com.example.scannerai

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scannerai.AnalyzeFeatures.adapter.OptionsAdapter
import com.example.scannerai.AnalyzeFeatures.adapter.OptionsAdapter.OnOptionClickListener
import com.example.scannerai.AnalyzeFeatures.analyzer.ImageLabelAnalyzer
import com.example.scannerai.AnalyzeFeatures.analyzer.ObjectDetectorAnalyzer
import com.example.scannerai.AnalyzeFeatures.analyzer.TextAnalyzer
import com.example.scannerai.AnalyzeFeatures.fragments.StorageImagesFragment
import com.example.scannerai.AnalyzeFeatures.helper.Animation
import com.example.scannerai.AnalyzeFeatures.helper.Constraint
import com.example.scannerai.AnalyzeFeatures.helper.DeviceMetric
import com.example.scannerai.AnalyzeFeatures.helper.GraphicOverlay
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity(), OnOptionClickListener {
    var ImagesList: FrameLayout? = null
    var optionsList: RecyclerView? = null
    var previewView: PreviewView? = null
    var cameraExecutor: ExecutorService? = null
    var topper: ConstraintLayout? = null
    var scrollView: ScrollView? = null
    private var imageAnalyzer: ImageAnalysis.Analyzer? = null

    @SuppressLint("RestrictedApi")
    private val imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(720, 1280))
        .setImageQueueDepth(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    // ------------------------ Activity Life Cycle ------------------------
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ImagesList = findViewById(R.id.fragment_storage_images)
        optionsList = findViewById(R.id.optionList)
        topper = findViewById(R.id.topper)
        scrollView = findViewById(R.id.scrollView)
        requestCameraUsingPermission()
        SetUpOptionsList()
        addFragment(StorageImagesFragment())
        SetPreviewViewHeight()
        imageAnalyzer = ObjectDetectorAnalyzer()
        val topper_child = LayoutInflater.from(this).inflate(R.layout.object_detector, null)
        Constraint.setConstaintToParent(topper_child)
        (topper as ConstraintLayout).addView(topper_child, 0)
        previewView = findViewById(R.id.previewView)
        val graphicOverlay = findViewById<GraphicOverlay>(R.id.graphicOverlay)
        (imageAnalyzer as ObjectDetectorAnalyzer).setGraphicOverlay(graphicOverlay)
        (imageAnalyzer as ObjectDetectorAnalyzer).setPreviewView(previewView)
        SetPreviewViewHeightChangeListener(previewView)
        previewView?.let {
            Animation.ChangePreviewViewHeight(it, DeviceMetric.PREVIEWVIEWMAXHEIGHT)
        }
        if (!hasCameraUsingPermission()) requestCameraUsingPermission() else StartCamera(previewView)
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer as ObjectDetectorAnalyzer)
    }

    override fun onDestroy() {
        super.onDestroy()
        //        cameraExecutor.shutdown();
    }

    fun StartCamera(previewView: PreviewView?) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: ExecutionException) {
                // Handle any errors (including cancellation) here.
                Log.e(ContentValues.TAG, "Unhandled exception", e)
            } catch (e: InterruptedException) {
                Log.e(ContentValues.TAG, "Unhandled exception", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun SetPreviewViewHeightChangeListener(previewView: PreviewView?, dataView: View?) {
        previewView!!.setOnTouchListener(object : OnTouchListener {
            private var pointY0 = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pointY0 = event.y
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - pointY0
                        if (deltaY > 0) {
                            Animation.ChangePreviewViewHeight(
                                previewView,
                                DeviceMetric.PREVIEWVIEWMAXHEIGHT
                            )
                        } else {
                            Animation.ChangePreviewViewHeight(
                                previewView,
                                DeviceMetric.PREVIEWVVIEWDEFAULTHEIGHT
                            )
                            if (dataView != null) {
                                (dataView as TextView).text = "Regconizing Text ..."
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun SetPreviewViewHeightChangeListener(previewView: PreviewView?) {
        previewView!!.setOnTouchListener(object : OnTouchListener {
            private var pointY0 = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pointY0 = event.y
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - pointY0
                        if (deltaY > 0) {
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Object Detector must run on full screen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun SetUpOptionsList() {
        val adapter: RecyclerView.Adapter<*> = OptionsAdapter()
        (adapter as OptionsAdapter).setOnOptionClickListener(this)
        optionsList!!.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        optionsList!!.layoutManager = layoutManager
    }

    private fun SetPreviewViewHeight() {
        DeviceMetric.PREVIEWVIEWMAXHEIGHT =
            DeviceMetric.getDeviceHeightInPx(this.resources.displayMetrics)
        DeviceMetric.PREVIEWVVIEWDEFAULTHEIGHT = DeviceMetric.dpToPx(300, this)
    }

    private fun addFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_storage_images, fragment)
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }

    private fun hasCameraUsingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraUsingPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Log.d("TAG", "requestCameraUsingPermission: " + "shouldShowRequestPermissionRationale")
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                124
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 124 && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("TAG", "onRequestPermissionsResult: " + "PERMISSION_GRANTED")
        } else {
            Toast.makeText(
                this,
                "Camera permission denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(previewView!!.surfaceProvider)
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            imageAnalysis
        )
    }

    override fun onOptionClick(position: Int) {
        var topper_child: View? = null
        val currentHeight = previewView!!.height
        when (position) {
            0 -> {
                scrollView!!.visibility = View.GONE
                imageAnalysis.clearAnalyzer()
                topper!!.removeViewAt(0)
                imageAnalyzer = ObjectDetectorAnalyzer()
                topper_child = LayoutInflater.from(this).inflate(R.layout.object_detector, null)
                Constraint.setConstaintToParent(topper_child)
                topper!!.addView(topper_child, 0)
                previewView = findViewById(R.id.previewView)
                val graphicOverlay = findViewById<GraphicOverlay>(R.id.graphicOverlay)
                (imageAnalyzer as ObjectDetectorAnalyzer).setGraphicOverlay(graphicOverlay)
                (imageAnalyzer as ObjectDetectorAnalyzer).setPreviewView(previewView)
                previewView?.let {
                    Animation.ChangePreviewViewHeight(it, DeviceMetric.PREVIEWVIEWMAXHEIGHT)
                }
                SetPreviewViewHeightChangeListener(previewView)
                StartCamera(previewView)
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer as ObjectDetectorAnalyzer)
            }

            1 -> {
                scrollView!!.visibility = View.GONE
                imageAnalysis.clearAnalyzer()
                topper!!.removeViewAt(0)
                topper_child = LayoutInflater.from(this).inflate(R.layout.image_label, null)
                Constraint.setConstaintToParent(topper_child)
                topper!!.addView(topper_child, 0)
                previewView = findViewById(R.id.previewView)
                val labelView = findViewById<TextView>(R.id.label)
                imageAnalyzer = ImageLabelAnalyzer(labelView)
                previewView?.let {
                    Animation.ChangePreviewViewHeight(it, currentHeight)
                }
                SetPreviewViewHeightChangeListener(previewView, null)
                StartCamera(previewView)
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer as ImageLabelAnalyzer)
            }

            2 -> {
                scrollView!!.visibility = View.VISIBLE
                imageAnalysis.clearAnalyzer()
                topper!!.removeViewAt(0)
                topper_child = LayoutInflater.from(this).inflate(R.layout.image_reg_text, null)
                Constraint.setConstaintToParent(topper_child)
                topper!!.addView(topper_child, 0)
                previewView = findViewById(R.id.previewView)
                val btnRegText = findViewById<Button>(R.id.btnTextReg)
                val regText = findViewById<TextView>(R.id.tvRegText)
                previewView?.let {
                    imageAnalyzer = TextAnalyzer(regText, btnRegText, it)
                }
                previewView?.let {
                    Animation.ChangePreviewViewHeight(it, currentHeight)
                }
                SetPreviewViewHeightChangeListener(previewView, regText)
                StartCamera(previewView)
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer as TextAnalyzer)
            }

            3 -> {}
            else -> throw IllegalStateException("Unexpected value: $position")
        }
    }
}