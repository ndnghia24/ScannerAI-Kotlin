package com.example.scannerai.presentation.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.scannerai.databinding.FragmentScannerBinding
import com.example.scannerai.domain.hit_test.HitTestResult
import com.example.scannerai.domain.ml.DetectedText
import com.example.scannerai.domain.use_cases.AnalyzeImage
import com.example.scannerai.domain.use_cases.HitTest
import com.example.scannerai.presentation.LabelObject
import com.example.scannerai.presentation.common.helpers.DisplayRotationHelper
import com.example.scannerai.presentation.confirmer.ConfirmFragment
import com.example.scannerai.presentation.preview.MainEvent
import com.example.scannerai.presentation.preview.MainShareModel
import com.example.scannerai.presentation.preview.PreviewFragment
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import dagger.hilt.android.AndroidEntryPoint
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.ar.arcore.ArFrame
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SMOOTH_DELAY = 0.5

@AndroidEntryPoint
class ScannerFragment: Fragment() {

    private val mainModel: MainShareModel by activityViewModels()

    @Inject
    lateinit var hitTest: HitTest
    @Inject
    lateinit var analyzeImage: AnalyzeImage

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val args: com.example.scannerai.presentation.scanner.ScannerFragmentArgs by navArgs()
    private val scanType by lazy { args.scanType }

    private lateinit var displayRotationHelper: DisplayRotationHelper
    private var lastDetectedObject: DetectedText? = null
    private var scanningNow: Boolean = false
    private var currentScanSmoothDelay: Double = 0.0
    private var scanningJob: Job? = null
    private var lastFrameTime = System.currentTimeMillis()

    private var navigating = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        displayRotationHelper = DisplayRotationHelper(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ScannerFragment", "onCreateView")
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainModel.frame.collect { frame ->
                    frame?.let { onFrame(it) }
                }
            }
        }
    }

    override fun onResume() {
        lastDetectedObject = null
        scanningNow = false
        currentScanSmoothDelay = 0.0
        scanningJob?.cancel()
        scanningJob = null
        navigating = false
        super.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        displayRotationHelper.onPause()
    }

    private fun onFrame(frame: ArFrame) {
        if (currentScanSmoothDelay > 0) {
            currentScanSmoothDelay -= getFrameInterval()
        }
        if (!scanningNow) {
            if (scanningJob?.isActive != true) {
                scanningJob?.cancel()
                scanningJob =
                    viewLifecycleOwner.lifecycleScope.launch {
                            if (currentScanSmoothDelay <= 0 && lastDetectedObject != null) {

                                val res = hitTestDetectedObject(lastDetectedObject!!, frame)
                                if (res != null && !navigating) {
                                    val confirmationObject = LabelObject(
                                        label = lastDetectedObject!!.detectedObjectResult.label,
                                        pos = res.orientatedPosition,
                                        anchor = res.hitResult.createAnchor()
                                    )

                                    mainModel.onEvent(
                                        MainEvent.NewConfirmationObject(
                                            confirmationObject
                                        )
                                    )
                                    toConfirm(
                                        when (scanType) {
                                            TYPE_INITIALIZE -> {
                                                ConfirmFragment.CONFIRM_INITIALIZE
                                            }
                                            TYPE_ENTRY -> {
                                                ConfirmFragment.CONFIRM_ENTRY
                                            }
                                            else -> {
                                                throw IllegalArgumentException("Unrealised type")
                                            }
                                        }
                                    )
                                } else {
                                    currentScanSmoothDelay = SMOOTH_DELAY
                                }
                            } else {
                                scanningNow = true
                                val detectedObject = tryGetDetectedObject(frame)
                                if (lastDetectedObject == null) {
                                    lastDetectedObject = detectedObject
                                    currentScanSmoothDelay = SMOOTH_DELAY
                                } else if (detectedObject == null) {
                                    currentScanSmoothDelay = SMOOTH_DELAY
                                } else {
                                    if (lastDetectedObject!!.detectedObjectResult.label !=
                                        detectedObject.detectedObjectResult.label
                                    ) {
                                        currentScanSmoothDelay = SMOOTH_DELAY
                                    }
                                    lastDetectedObject = detectedObject
                                }
                                scanningNow = false
                            }
                        };
            }
        }
    }

    private fun toConfirm(type: Int){
        if (!navigating){
            navigating = true
            val action =
                com.example.scannerai.presentation.scanner.ScannerFragmentDirections.actionScannerFragmentToConfirmFragment(
                    type
                )
            findNavController().navigate(action)
        }
    }

    private suspend fun tryGetDetectedObject(frame: ArFrame): DetectedText? {
        val camera = frame.camera
        val session = frame.session

        if (camera.trackingState != TrackingState.TRACKING) {
            return null
        }
        val cameraImage = frame.tryAcquireCameraImage()

        if (cameraImage != null) {
            val cameraId = session.cameraConfig.cameraId
            val imageRotation =
                displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
            val displaySize = Pair(
                session.displayWidth,
                session.displayHeight
            )

            val detectedResult = analyzeImage(
                cameraImage,
                imageRotation,
                PreviewFragment.DESIRED_CROP,
                displaySize
            )

            cameraImage.close()

            detectedResult.getOrNull()?.let {
                Log.d("ScannerFragment", "Detected: ${it.label}, ${frame.frame}, ${it.centerCoordinate}")
                it.label = it.label.replace("\n", " ")
                // Translate text
                val translatedText = translateText(it.label, TranslateLanguage.ENGLISH, TranslateLanguage.VIETNAMESE)
                it.label = translatedText

                Log.d("ScannerFragment", "Translated: ${it.label}")

                return DetectedText(it, frame.frame)
            }
            return null
        }
        cameraImage?.close()
        return null
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            // for other device how are able to connect with Ethernet
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            // for check internet over Bluetooth
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }
    }

    suspend fun translateText(text: String, sourceLanguageCode: String, targetLanguageCode: String): String {
        if (!isNetworkAvailable(requireContext())) {
            return "No internet connection"
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguageCode)
            .setTargetLanguage(targetLanguageCode)
            .build()

        val translator = Translation.getClient(options)

        return try {
            translator.downloadModelIfNeeded().await()
            translator.translate(text).await()
        } catch (e: Exception) {
            "Translation failed: ${e.message}"
        } finally {
            translator.close()
        }
    }

    private fun hitTestDetectedObject(detectedText: DetectedText, frame: ArFrame): HitTestResult? {
        val detectedObject = detectedText.detectedObjectResult
        return useHitTest(
            detectedObject.centerCoordinate.x,
            detectedObject.centerCoordinate.y,
            frame
        ).getOrNull()
    }

    private fun useHitTest(
        x: Float,
        y: Float,
        frame: ArFrame
    ): Result<HitTestResult> {
        return hitTest(frame, Float2(x, y))
    }

    private fun getFrameInterval(): Long {
        val frameTime = System.currentTimeMillis() - lastFrameTime
        lastFrameTime = System.currentTimeMillis()
        return frameTime
    }

    private fun ArFrame.tryAcquireCameraImage() = try {
        frame.acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }

    companion object {
        const val SCAN_TYPE = "scanType"
        const val TYPE_INITIALIZE = 0
        const val TYPE_ENTRY = 1
    }
}