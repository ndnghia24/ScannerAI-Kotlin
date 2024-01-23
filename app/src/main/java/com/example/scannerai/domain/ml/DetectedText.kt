package com.example.scannerai.domain.ml

import com.example.scannerai.data.model.DetectedObjectResult
import com.google.ar.core.Frame

data class DetectedText(
    val detectedObjectResult: DetectedObjectResult,
    val frame: Frame
)
