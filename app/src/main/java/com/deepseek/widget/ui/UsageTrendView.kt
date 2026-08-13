package com.deepseek.widget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.deepseek.widget.R
import com.deepseek.widget.api.DailyUsagePoint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 方案三 · 柱状用量趋势图。
 * 上期浅色宽柱垫底，本期主色窄柱居中，本期峰值日用砖红高亮。
 * 柱子随 drawProgress 从底部生长。
 */
class UsageTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val previousBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.comparison_sage)
        style = Paint.Style.FILL
    }
    private val currentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.deepseek_blue)
        style = Paint.Style.FILL
    }
    private val peakBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.peak)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted_text)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
    }

    private val barRect = RectF()
    private var currentPoints: List<DailyUsagePoint> = emptyList()
    private var previousPoints: List<DailyUsagePoint> = emptyList()
    private var drawProgress = 1f
    private var animator: ValueAnimator? = null

    fun setAccentColor(colorRes: Int) {
        currentBarPaint.color = context.getColor(colorRes)
        invalidate()
    }

    fun submitData(
        current: List<DailyUsagePoint>,
        previous: List<DailyUsagePoint> = emptyList(),
        animate: Boolean = true
    ) {
        currentPoints = current.sortedBy { it.date }
        previousPoints = previous.sortedBy { it.date }
        animator?.cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            drawProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 480L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    drawProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            drawProgress = 1f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentPoints.isEmpty() && previousPoints.isEmpty()) return

        val left = 6f * density
        val right = width - 6f * density
        val top = 10f * density
        val bottom = height - 24f * density
        val chartHeight = max(1f, bottom - top)

        val maxCost = max(
            currentPoints.maxOfOrNull { it.actual_cost } ?: 0.0,
            previousPoints.maxOfOrNull { it.actual_cost } ?: 0.0
        ).coerceAtLeast(0.01)

        val currentPeak = currentPoints.maxOfOrNull { it.actual_cost } ?: 0.0

        // 以本期为主轴；若本期为空则用上期
        val slots = max(currentPoints.size, previousPoints.size).coerceAtLeast(1)
        val slotWidth = (right - left) / slots
        val prevBarWidth = slotWidth * 0.52f
        val currBarWidth = slotWidth * 0.30f
        val corner = 3f * density

        // 上期柱（宽、浅色、垫底）
        previousPoints.forEachIndexed { index, point ->
            if (index >= slots) return@forEachIndexed
            val cx = left + slotWidth * index + slotWidth / 2f
            val ratio = (point.actual_cost / maxCost).toFloat().coerceIn(0f, 1f)
            val barHeight = chartHeight * ratio * drawProgress
            if (barHeight > 0.5f) {
                barRect.set(cx - prevBarWidth / 2f, bottom - barHeight, cx + prevBarWidth / 2f, bottom)
                canvas.drawRoundRect(barRect, corner, corner, previousBarPaint)
            }
        }

        // 本期柱（窄、主色；峰值日砖红）
        currentPoints.forEachIndexed { index, point ->
            if (index >= slots) return@forEachIndexed
            val cx = left + slotWidth * index + slotWidth / 2f
            val ratio = (point.actual_cost / maxCost).toFloat().coerceIn(0f, 1f)
            val barHeight = chartHeight * ratio * drawProgress
            if (barHeight > 0.5f) {
                val isPeak = point.actual_cost >= currentPeak && currentPeak > 0.0
                barRect.set(cx - currBarWidth / 2f, bottom - barHeight, cx + currBarWidth / 2f, bottom)
                canvas.drawRoundRect(barRect, corner, corner, if (isPeak) peakBarPaint else currentBarPaint)
            }
        }

        // 日期标签
        val labels = currentPoints.ifEmpty { previousPoints }
        val labelIndices = usageLabelIndices(labels.size)
        labels.forEachIndexed { index, point ->
            if (index in labelIndices) {
                val cx = left + slotWidth * index + slotWidth / 2f
                canvas.drawText(point.date.takeLast(5), cx, height - 7f * density, labelPaint)
            }
        }
    }
}

/** 在长周期内均匀保留首尾与少量中间标签，避免尾部相邻日期重叠。 */
internal fun usageLabelIndices(pointCount: Int, maxLabels: Int = 5): Set<Int> {
    if (pointCount <= 0) return emptySet()
    if (pointCount <= 7) return (0 until pointCount).toSet()
    val count = maxLabels.coerceIn(2, pointCount)
    val last = pointCount - 1
    return (0 until count).map { slot ->
        (last * slot.toDouble() / (count - 1)).roundToInt()
    }.toSet()
}
