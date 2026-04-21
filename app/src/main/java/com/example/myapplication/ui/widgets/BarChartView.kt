package com.example.myapplication.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import kotlin.math.max

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Long> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // ✅ Default warna teal, bukan merah
        color = ContextCompat.getColor(context, R.color.accent_teal)
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


    // ✅ Tambah parameter warna opsional
    fun setData(labels: List<String>, values: List<Long>, colorResId: Int? = null) {
        this.labels = labels
        this.values = values
        if (colorResId != null) {
            barPaint.color = ContextCompat.getColor(context, colorResId)
        } else {
            barPaint.color = ContextCompat.getColor(context, R.color.accent_teal)
        }
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

        if (values.isEmpty() || chartHeight <= 0f || chartWidth <= 0f) {
            canvas.drawText("Tidak ada data", width / 2f - dp(30f), height / 2f, textPaint)
            return
        }

        val maxVal = values.maxOrNull() ?: 0L
        val maxValue = if (maxVal == 0L) 100f else maxVal.toFloat()

        val baselineY = chartBottom
        canvas.drawLine(chartLeft, baselineY, chartRight, baselineY, axisPaint)

        val count = values.size
        val gap = dp(8f)
        val barWidth = ((chartWidth - gap * (count - 1)).coerceAtLeast(0f)) / count

        for (i in 0 until count) {
            val v = values.getOrNull(i)?.toFloat() ?: 0f
            val barLeft = chartLeft + i * (barWidth + gap)
            val barRight = barLeft + barWidth
            val barTop = chartBottom - (v / maxValue) * chartHeight
            val barBottom = chartBottom

            canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, dp(4f), dp(4f), barPaint)

            val label = labels.getOrNull(i).orEmpty()
            if (label.isNotBlank()) {
                val textWidth = textPaint.measureText(label)
                val x = barLeft + (barWidth - textWidth) / 2f
                canvas.drawText(label, x, height.toFloat() - dp(8f), textPaint)
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}