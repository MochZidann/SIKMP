package com.example.myapplication.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.myapplication.R

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Long> = emptyList()
    private var barColor: Int = ContextCompat.getColor(context, R.color.accent_teal)

    fun setData(labels: List<String>, values: List<Long>, colorResId: Int? = null) {
        this.labels = labels
        this.values = values
        barColor = if (colorResId != null)
            ContextCompat.getColor(context, colorResId)
        else
            ContextCompat.getColor(context, R.color.accent_teal)
        // Post to ensure view is measured before drawing
        post { invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = barColor
            style = Paint.Style.FILL
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22000000
            style = Paint.Style.STROKE
            strokeWidth = d
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            textSize = 10 * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF64748B.toInt()
            textSize = 10 * d
            textAlign = Paint.Align.CENTER
        }
        val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 9 * d
            textAlign = Paint.Align.RIGHT
        }

        val maxVal = values.maxOrNull() ?: 0L
        val yLabelText = if (maxVal >= 1_000_000) "${maxVal / 1_000_000}jt"
                         else if (maxVal >= 1_000) "${maxVal / 1_000}rb"
                         else maxVal.toString()

        val yAxisWidth = yLabelPaint.measureText(yLabelText) + 10 * d
        val paddingTop = 28 * d
        val paddingBottom = 32 * d   // space for x labels (straight, no rotation)
        val paddingRight = 8 * d

        val chartLeft = yAxisWidth
        val chartRight = width.toFloat() - paddingRight
        val chartTop = paddingTop
        val chartBottom = height.toFloat() - paddingBottom
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        if (values.isEmpty() || chartHeight <= 0 || chartWidth <= 0) {
            canvas.drawText("Tidak ada data", width / 2f, height / 2f, labelPaint)
            return
        }

        val maxValue = if (maxVal == 0L) 100f else maxVal.toFloat()

        // Gridlines
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)
        val midY = chartTop + chartHeight / 2f
        axisPaint.color = 0x11000000
        canvas.drawLine(chartLeft, midY, chartRight, midY, axisPaint)
        canvas.drawLine(chartLeft, chartTop, chartRight, chartTop, axisPaint)

        // Y-axis labels
        fun fmtY(v: Long) = if (v >= 1_000_000) "${v / 1_000_000}jt" else if (v >= 1_000) "${v / 1_000}rb" else v.toString()
        canvas.drawText(fmtY(maxVal), chartLeft - 4 * d, chartTop + 9 * d, yLabelPaint)
        canvas.drawText(fmtY(maxVal / 2), chartLeft - 4 * d, midY + 4 * d, yLabelPaint)
        canvas.drawText("0", chartLeft - 4 * d, chartBottom, yLabelPaint)

        // Bars + labels
        val count = values.size
        val totalGap = 8 * d * (count - 1).coerceAtLeast(0)
        val barWidth = ((chartWidth - totalGap) / count.coerceAtLeast(1)).coerceAtLeast(4 * d)

        for (i in 0 until count) {
            val v = values.getOrNull(i)?.toFloat() ?: 0f
            val barLeft = chartLeft + i * (barWidth + 8 * d)
            val barRight = barLeft + barWidth
            val barTop = if (v <= 0f) chartBottom else chartBottom - (v / maxValue) * chartHeight
            val cx = barLeft + barWidth / 2f

            // Bar
            barPaint.color = barColor
            canvas.drawRoundRect(barLeft, barTop, barRight, chartBottom, 4 * d, 4 * d, barPaint)

            // Value above bar
            val vText = fmtY(values.getOrNull(i) ?: 0L)
            val vY = (barTop - 5 * d).coerceAtLeast(chartTop + 12 * d)
            canvas.drawText(vText, cx, vY, valuePaint)

            // X label below bar (straight, no rotation, truncated)
            val rawLabel = labels.getOrNull(i).orEmpty()
            val maxChars = 5
            val disp = if (rawLabel.length > maxChars) rawLabel.substring(0, maxChars) else rawLabel
            canvas.drawText(disp, cx, chartBottom + 18 * d, labelPaint)
        }
    }
}