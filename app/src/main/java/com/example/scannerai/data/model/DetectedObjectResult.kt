package com.example.scannerai.data.model

import dev.romainguy.kotlin.math.Float2

data class DetectedObjectResult(
    var label: String,
    val centerCoordinate: Float2,
)