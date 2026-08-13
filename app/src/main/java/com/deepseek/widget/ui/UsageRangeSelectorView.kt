package com.deepseek.widget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.deepseek.widget.R

class UsageRangeSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onSelectionChanged: ((days: Int) -> Unit)? = null

    private val options = listOf(
        1 to R.string.range_1d,
        3 to R.string.range_3d,
        5 to R.string.range_5d,
        7 to R.string.range_7d,
        14 to R.string.range_14d,
        28 to R.string.range_28d,
        30 to R.string.range_30d
    )
    private val indicator = View(context)
    private val labels = mutableListOf<TextView>()
    private var selectedIndex = 3
    private var accentColor = ContextCompat.getColor(context, R.color.deepseek_blue)
    private var indicatorAnimator: ValueAnimator? = null

    init {
        minimumHeight = dp(48)
        background = ContextCompat.getDrawable(context, R.drawable.usage_range_track)
        clipToOutline = true

        indicator.background = ContextCompat.getDrawable(context, R.drawable.usage_range_indicator)
        addView(
            indicator,
            LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        options.forEachIndexed { index, (days, labelRes) ->
            val label = TextView(context).apply {
                gravity = Gravity.CENTER
                setText(labelRes)
                textSize = 11f
                isClickable = true
                isFocusable = true
                minHeight = dp(48)
                setOnClickListener { select(days, animate = true, notify = true) }
                contentDescription = context.getString(labelRes)
            }
            labels += label
            row.addView(label, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateLabelColors()
    }

    fun setAccentColor(colorRes: Int) {
        accentColor = ContextCompat.getColor(context, colorRes)
        updateLabelColors()
    }

    fun setSelectedDays(days: Int, animate: Boolean, notify: Boolean = false) {
        select(days, animate, notify)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { positionIndicator(selectedIndex, animate = false) }
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun select(days: Int, animate: Boolean, notify: Boolean) {
        val newIndex = options.indexOfFirst { it.first == days }.takeIf { it >= 0 } ?: 3
        if (newIndex == selectedIndex) {
            if (notify) onSelectionChanged?.invoke(options[newIndex].first)
            return
        }
        selectedIndex = newIndex
        updateLabelColors()
        positionIndicator(newIndex, animate)
        if (notify) onSelectionChanged?.invoke(options[newIndex].first)
    }

    private fun positionIndicator(index: Int, animate: Boolean) {
        if (width == 0) return
        val cellWidth = width.toFloat() / options.size
        val inset = dp(3).toFloat()
        val targetX = index * cellWidth + inset
        indicator.layoutParams = (indicator.layoutParams as LayoutParams).apply {
            width = (cellWidth - inset * 2).toInt().coerceAtLeast(1)
        }
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            indicator.translationX = targetX
            return
        }
        indicatorAnimator?.cancel()
        indicatorAnimator = ValueAnimator.ofFloat(indicator.translationX, targetX).apply {
            duration = 260L
            interpolator = DecelerateInterpolator(1.35f)
            addUpdateListener { indicator.translationX = it.animatedValue as Float }
            start()
        }
    }

    private fun updateLabelColors() {
        val idle = ContextCompat.getColor(context, R.color.label_text)
        labels.forEachIndexed { index, label ->
            label.setTextColor(if (index == selectedIndex) accentColor else idle)
            label.isSelected = index == selectedIndex
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
