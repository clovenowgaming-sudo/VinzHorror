package com.vinz.horror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Pure-overlay HUD: crosshair, battery bar, jumpscare flash, game-over text. */
class HudView(context: Context) : View(context) {

    @Volatile var batteryPct = 1f
    @Volatile var jumpscareFlash = false
    @Volatile var showGameOver = false

    private val paint = Paint().apply { isAntiAlias = true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(180, 255, 255, 255)
        paint.strokeWidth = 3f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - 10f, cy, cx + 10f, cy, paint)
        canvas.drawLine(cx, cy - 10f, cx, cy + 10f, paint)

        paint.color = Color.WHITE
        val bx = 40f
        val by = 60f
        val bw = 160f
        val bh = 24f
        canvas.drawRect(bx, by, bx + bw, by + bh, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (batteryPct > 0.3f) Color.rgb(180, 220, 120) else Color.rgb(220, 70, 60)
        canvas.drawRect(bx + 3f, by + 3f, bx + 3f + (bw - 6f) * batteryPct, by + bh - 3f, paint)
        paint.color = Color.WHITE
        paint.textSize = 24f
        canvas.drawText("BATTERY", bx, by - 10f, paint)

        if (jumpscareFlash) {
            paint.color = Color.argb(160, 160, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        if (showGameOver) {
            paint.color = Color.WHITE
            paint.textSize = 54f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("YOU DIDN'T MAKE IT", width / 2f, height / 2f, paint)
            paint.textSize = 28f
            canvas.drawText("respawning...", width / 2f, height / 2f + 50f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }
}
