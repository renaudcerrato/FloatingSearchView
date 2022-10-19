package com.mypopsy.widget.internal

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView

class SuggestionItemDecorator(private val drawable: RoundRectDrawableWithShadow): RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        rect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val count = state.itemCount
        var shadows = RoundRectDrawableWithShadow.LEFT or RoundRectDrawableWithShadow.RIGHT
        if (position == count - 1) shadows = shadows or RoundRectDrawableWithShadow.BOTTOM
        drawable.apply {
            setShadow(shadows)
            getPadding(rect)
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val visibleCount = parent.childCount
        val count = state.itemCount
        val adapter = parent.adapter as RecyclerView.Adapter<out RecyclerView.ViewHolder?>?
        val adapterCount = adapter?.itemCount ?: 0
        for (i in 0 until visibleCount) {
            with(parent.getChildAt(i)) {
                val position = parent.getChildAdapterPosition(this)
                val params = layoutParams as RecyclerView.LayoutParams
                var shadows = RoundRectDrawableWithShadow.LEFT or RoundRectDrawableWithShadow.RIGHT
                if (position == count - 1 && adapterCount != 0) shadows =
                    shadows or RoundRectDrawableWithShadow.BOTTOM
                drawable.apply {
                    alpha = (255 * alpha)
                    setShadow(shadows)
                    setBounds(0, 0, parent.width, height)
                }
                 val saved = canvas.save()
                canvas.translate(
                    parent.paddingLeft + translationX,
                    top + params.topMargin + translationY
                )
                drawable.draw(canvas)
                canvas.restoreToCount(saved)
            }
        }
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        drawable.setColor(color)
    }

    fun setCornerRadius(radius: Float) {
        drawable.setCornerRadius(radius)
    }

}