package com.eddam.heysary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class VoiceWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFFFF0033.toInt() // Cyberpunk Red
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val barWidth = 8f
    private val barGap = 6f
    private val numBars = 7
    private val barHeights = FloatArray(numBars) { 10f }
    private var currentAmplitude = 0f
    private var isActive = false

    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            for (i in barHeights.indices) barHeights[i] = 10f
        }
        invalidate()
    }

    fun updateAmplitude(rmsDb: Float) {
        // Mapear rmsDb (normalmente -2 a 10) a un valor de amplitud útil
        // Usamos una interpolación simple para suavizar
        val targetAmplitude = ((rmsDb + 2f).coerceAtLeast(0f) * (height / 12f)).coerceAtMost(height.toFloat())
        currentAmplitude = targetAmplitude
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val midY = height / 2f
        val totalWidth = (numBars * barWidth) + ((numBars - 1) * barGap)
        var startX = (width - totalWidth) / 2f

        for (i in 0 until numBars) {
            if (isActive) {
                // Cada barra tiene una variación leve basada en la amplitud central para dinamismo
                val variation = if (i % 2 == 0) 0.8f else 1.1f
                barHeights[i] = (currentAmplitude * variation).coerceAtLeast(10f)
                    .coerceAtMost(height.toFloat())
            }

            val h = barHeights[i]
            canvas.drawRoundRect(
                startX, midY - h / 2f,
                startX + barWidth, midY + h / 2f,
                barWidth / 2f, barWidth / 2f,
                paint
            )
            startX += barWidth + barGap
        }
    }
}
