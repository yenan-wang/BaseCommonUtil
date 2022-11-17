package com.ngb.wyn.common.ui.card

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import java.lang.IllegalArgumentException

open class CardBaseRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    companion object {
        const val TAG = "CardBaseRecyclerView"
    }

    init {
        //打开允许自定义绘制层级
        isChildrenDrawingOrderEnabled = true
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        if (layout == null || layout !is CardBaseLayoutManager) {
            throw IllegalArgumentException("you should use class CardLayoutManager or it's child class.")
        }
        super.setLayoutManager(layout)
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        val centerPos = getRecyclerViewLayoutManager().getChildDrawingOrderCenter()
        val order = if (i < centerPos) {
            i
        } else if (i > centerPos) {
            childCount + centerPos - i - 1
        } else {
            childCount - 1
        }
        return order
    }

    fun getRecyclerViewLayoutManager(): CardBaseLayoutManager {
        return layoutManager as CardBaseLayoutManager
    }

    @JvmOverloads
    fun scrollToPositionCenter(pos: Int = getRecyclerViewLayoutManager().getCenterPosition()) {
        getRecyclerViewLayoutManager().scrollToPositionCenter(this, pos)
    }

    @JvmOverloads
    fun scrollToPositionCenterSmooth(
        duration: Int,
        pos: Int = getRecyclerViewLayoutManager().getCenterPosition()
    ) {
        getRecyclerViewLayoutManager().scrollToPositionCenterSmooth(this, pos, duration)
    }

    fun canScrollHorizontal(canScroll: Boolean) {
        getRecyclerViewLayoutManager().setCanScrollX(canScroll)
    }

    fun canScrollVertical(canScroll: Boolean) {
        getRecyclerViewLayoutManager().setCanScrollY(canScroll)
    }
}