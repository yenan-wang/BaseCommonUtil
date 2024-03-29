package com.ngb.wyn.common.ui.card

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import java.lang.IllegalArgumentException

open class CardBaseRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var currentOp = CardBaseLayoutManager.TYPE_DEFAULT
    private var currentOpItemCount = 0

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
        layout.addOnItemChangeListener(object : CardBaseLayoutManager.OnItemChangeListener {
            override fun onChange(currentOp: String, positionStart: Int, itemCount: Int) {
                this@CardBaseRecyclerView.currentOp = currentOp
                this@CardBaseRecyclerView.currentOpItemCount = itemCount
            }
        })
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        var centerPos = getRecyclerViewLayoutManager().getChildDrawingOrderCenter()
        //这一步if是解决系统调用中的一个概率性bug，返回的childCount中i与默认的顺序不太一致的问题，导致在添加时，会有一瞬间 中间两侧的层级 错乱的问题
        if (currentOpItemCount == 1 && childCount != getRecyclerViewLayoutManager().childCount && currentOp == CardBaseLayoutManager.TYPE_ADD) {
            centerPos++
        }
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

    fun setCanScrollHorizontal(canScroll: Boolean) {
        getRecyclerViewLayoutManager().setCanScrollX(canScroll)
    }

    fun setCanScrollVertical(canScroll: Boolean) {
        getRecyclerViewLayoutManager().setCanScrollY(canScroll)
    }

    fun canScrollHorizontally() : Boolean {
        return getRecyclerViewLayoutManager().canScrollHorizontally()
    }

    fun canScrollVertically() : Boolean {
        return getRecyclerViewLayoutManager().canScrollVertically()
    }
}