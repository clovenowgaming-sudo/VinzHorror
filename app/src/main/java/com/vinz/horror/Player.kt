package com.vinz.horror

import kotlin.math.cos
import kotlin.math.sin

class Player {
    var x = TILE * 0.5f
    var z = TILE * 0.5f
    var yaw = 0f
    var pitch = 0f
    var flashlightOn = false
    var battery = 1f
    val radius = 0.35f
    val eyeHeight = 1.55f

    fun forward(): FloatArray = floatArrayOf(sin(yaw), 0f, cos(yaw))
    fun right(): FloatArray = floatArrayOf(cos(yaw), 0f, -sin(yaw))
}
