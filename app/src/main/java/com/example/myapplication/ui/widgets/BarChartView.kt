package com.example.myapplication.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import kotlin.math.max
import kotlin.math.min

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var labels: List<String> = emptyList()
    private var values: List<Long> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.purple_500)
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        textSize = dp(10f)
    }

    fun setData(labels: List<String>, values: List<Long>) {
        this.labels = labels
        this.values = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paddingBottom = dp(22f)
        val paddingTop = dp(8f)
        val paddingHorizontal = dp(8f)
        val chartLeft = paddingHorizontal
        val chartRight = width.toFloat() - paddingHorizontal
        val chartTop = paddingTop
        val chartBottom = height.toFloat() - paddingBottom
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        if (values.isEmpty() || chartHeight <= 0f || chartWidth <= 0f) return
        val maxValue = (values.maxOrNull() ?: 0L).toFloat()
        val minValue = (values.minOrNull() ?: 0L).toFloat()
        val range = (maxValue - minValue).let { if (it == 0f) 1f else it }
        val baselineValue = 0f.coerceIn(minValue, maxValue)
        val baselineY = chartTop + ((maxValue - baselineValue) / range) * chartHeight

        canvas.drawLine(chartLeft, baselineY, chartRight, baselineY, axisPaint)
        val count = values.size
        val gap = dp(6f)
        val barWidth = ((chartWidth - gap * (count - 1)).coerceAtLeast(0f)) / count

        for (i in 0 until count) {
            val v = values.getOrNull(i)?.toFloat() ?: 0f
            val barLeft = chartLeft + i * (barWidth + gap)
            val barRight = barLeft + barWidth
            val valueY = chartTop + ((maxValue - v) / range) * chartHeight
            val barTop = min(valueY, baselineY)
            val barBottom = max(valueY, baselineY)
            canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, dp(4f), dp(4f), barPaint)
            val label = labels.getOrNull(i).orEmpty()
            if (label.isNotBlank()) {
                val textWidth = textPaint.measureText(label)
                val x = barLeft + (barWidth - textWidth) / 2f
                canvas.drawText(label, x.coerceAtLeast(0f), height.toFloat() - dp(8f), textPaint)
            }
        }
    }


    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

