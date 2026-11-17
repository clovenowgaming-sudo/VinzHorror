package com.vinz.horror

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import kotlin.math.sqrt

/** GLSurfaceView that is also its own touch controller: left half = move joystick, right half = look drag. */
class GameView(context: Context, private val renderer: GameRenderer) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private var moveTouchId = -1
    private var lookTouchId = -1
    private var moveOriginX = 0f
    private var moveOriginY = 0f
    private var lookLastX = 0f
    private var lookLastY = 0f
    private val joystickRadius = 140f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(index)
                val y = event.getY(index)
                val id = event.getPointerId(index)
                if (x < width / 2f) {
                    if (moveTouchId == -1) {
                        moveTouchId = id
                        moveOriginX = x
                        moveOriginY = y
                    }
                } else {
                    if (lookTouchId == -1) {
                        lookTouchId = id
                        lookLastX = x
                        lookLastY = y
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (id == moveTouchId) {
                        var dx = x - moveOriginX
                        var dy = y - moveOriginY
                        val len = sqrt(dx * dx + dy * dy)
                        if (len > joystickRadius) {
                            dx = dx / len * joystickRadius
                            dy = dy / len * joystickRadius
                        }
                        renderer.moveX = (dx / joystickRadius).coerceIn(-1f, 1f)
                        renderer.moveZ = (-dy / joystickRadius).coerceIn(-1f, 1f)
                    } else if (id == lookTouchId) {
                        val dx = x - lookLastX
                        val dy = y - lookLastY
                        renderer.lookDX += dx
                        renderer.lookDY += dy
                        lookLastX = x
                        lookLastY = y
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(index)
                if (id == moveTouchId) {
                    moveTouchId = -1
                    renderer.moveX = 0f
                    renderer.moveZ = 0f
                } else if (id == lookTouchId) {
                    lookTouchId = -1
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                moveTouchId = -1
                lookTouchId = -1
                renderer.moveX = 0f
                renderer.moveZ = 0f
            }
        }
        return true
    }
}
