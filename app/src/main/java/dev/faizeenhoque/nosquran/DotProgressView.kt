package dev.faizeenhoque.nosquran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.min

class DotProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotSize = DpToPx(5f)
    private val rowGap = DpToPx(4f)
    private val readColor = ContextCompat.getColor(context, R.color.inverse_text)
    private val unreadColor = ContextCompat.getColor(context, R.color.dot_inverse_off)
    private val currentColor = ContextCompat.getColor(context, R.color.accent_fill)

    private var dotCount = MAX_DOTS
    private var currentDot = 9

    fun SetProgress(currentVerse: Int, verseCount: Int) {
        dotCount = min(verseCount, MAX_DOTS)
        // Long surahs share dots, so Al-Baqarah doesn't become 13 rows tall.
        currentDot = (currentVerse - 1) * dotCount / verseCount
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = ceil(dotCount / COLUMNS.toFloat()).toInt()
        val height = rows * dotSize + (rows - 1) * rowGap
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = dotSize / 2f
        val columnStep = (width - dotSize) / (COLUMNS - 1)

        for (i in 0 until dotCount) {
            val x = radius + (i % COLUMNS) * columnStep
            val y = radius + (i / COLUMNS) * (dotSize + rowGap)
            paint.color = when {
                i == currentDot -> currentColor
                i < currentDot -> readColor
                else -> unreadColor
            }
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun DpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private companion object {
        const val COLUMNS = 22
        const val MAX_DOTS = 110
    }
}