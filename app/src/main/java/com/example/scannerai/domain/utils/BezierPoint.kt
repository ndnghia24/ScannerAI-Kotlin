package com.example.scannerai.domain.utils

import dev.benedikt.math.bezier.vector.Vector3D

data class BezierPoint(
    val t: Double,
    var pos: Vector3D
)
