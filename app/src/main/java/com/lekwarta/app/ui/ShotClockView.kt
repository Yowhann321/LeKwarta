package com.lekwarta.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.lekwarta.app.R

/**
 * The "shot clock": a ring showing how much of today's budget has been used.
 * Gold while under budget, red once the budget is blown.
 */
class ShotClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val stroke = 7f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = ContextCompat.getColor(context, R.color.court_line)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textSize = 13f * density
    }
    private val oval = RectF()

    /** Fraction of the budget used; values above 1 mean over budget. */
    var progress: Float = 0f
        set(value) { field = value.coerceAtLeast(0f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = stroke / 2
        oval.set(half, half, width - half, height - half)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        val over = progress > 1f
        val color = ContextCompat.getColor(context, if (over) R.color.red_soft else R.color.gold)
        arcPaint.color = color
        canvas.drawArc(oval, -90f, 360f * progress.coerceAtMost(1f), false, arcPaint)

        textPaint.color = color
        val label = "${(progress * 100).toInt().coerceAtMost(999)}%"
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, width / 2f, y, textPaint)
    }
}
