package com.example.smartupiannotation.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DashboardBarChart @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f // Reduced text size for compact view
        textAlign = Paint.Align.CENTER
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 2f
    }

    private var data: List<Pair<String, Float>> = emptyList()
    private val barRect = RectF()

    fun setData(newData: List<Pair<String, Float>>) {
        this.data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        // Reduced padding for compact layout
        val paddingLeftRight = 40f
        val paddingTopBottom = 30f
        
        val chartWidth = width - (paddingLeftRight * 2)
        val chartHeight = height - (paddingTopBottom * 2)
        val maxVal = data.maxOfOrNull { it.second } ?: 1f
        val safeMax = if (maxVal == 0f) 1f else maxVal
        
        val barWidth = (chartWidth / data.size) * 0.6f
        val spacing = (chartWidth / data.size) * 0.4f

        // Draw horizontal grid lines (fewer lines for small height)
        for (i in 0..2) {
            val y = chartHeight + paddingTopBottom - (chartHeight / 2 * i)
            canvas.drawLine(paddingLeftRight, y, width - paddingLeftRight, y, axisPaint)
        }

        data.forEachIndexed { index, pair ->
            val x = paddingLeftRight + (index * (barWidth + spacing)) + (spacing / 2)
            val h = (pair.second / safeMax) * chartHeight
            
            // Draw Bar
            barRect.set(x, chartHeight + paddingTopBottom - h, x + barWidth, chartHeight + paddingTopBottom)
            canvas.drawRoundRect(barRect, 8f, 8f, barPaint)

            // Draw Label
            canvas.drawText(pair.first, x + (barWidth / 2), height - 5f, textPaint)
            
            // Draw Value on top of bar (only if height is enough)
            if (h > 15) {
                canvas.drawText("%.0f".format(pair.second), x + (barWidth / 2), chartHeight + paddingTopBottom - h - 5f, textPaint)
            }
        }
    }
}
