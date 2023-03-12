package com.ngb.wyn.common.ui.card

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration

class CardRecyclerView(context: Context, attrs: AttributeSet? = null) :
    CardBaseRecyclerView(context, attrs) {

    private var isNeedLocation = false //是否需要松手自动定位
    private var isForbidScrollWhenItemLessThanVisible = true //小于visibleCount个数时，是否禁止滑动

    companion object {
        const val TAG = "CardRecyclerView"
        private const val POST_TIME = 400 //定位动画时间
    }

    init {
        val friction = ViewConfiguration.getScrollFriction() * 2.5F //摩擦系数为2.5
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE && isNeedLocation) {
            isNeedLocation = false //重置
            //使得当前C位的item定位到正中间
            scrollToPositionCenterSmooth(duration = POST_TIME)
        }
    }

    override fun onTouchEvent(e: MotionEvent?): Boolean {
        if (e?.actionMasked == MotionEvent.ACTION_UP || e?.actionMasked == MotionEvent.ACTION_CANCEL) {
            isNeedLocation = true
        }
        return super.onTouchEvent(e)
    }

    override fun stopScroll() {
        if (scrollState != SCROLL_STATE_IDLE) {
            //如果在移除时，还在滑动，需要停止滑动，并标记其不需要定位，标记要在stopScroll之前执行，否则就无效了，因为停止滑动还会再次回调SCROLL_STATE_IDLE
            isNeedLocation = false
            super.stopScroll()
        }
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)
        getRecyclerViewLayoutManager().setOnCompletedListener(object :
            CardBaseLayoutManager.OnCompleted {
            override fun completed(state: State?) {
                if (isForbidScrollWhenItemLessThanVisible) {
                    post {
                        val count = adapter?.itemCount ?: 0
                        if (count < 5) {
                            stopScroll()
                        }
                        if (getRecyclerViewLayoutManager().getOrientation() == HORIZONTAL) {
                            setCanScrollHorizontal(count >= 5)
                        } else {
                            setCanScrollVertical(count >= 5)
                        }
                    }
                } else {
                    if (getRecyclerViewLayoutManager().getOrientation() == HORIZONTAL) {
                        if (!canScrollHorizontally()) {
                            setCanScrollHorizontal(true)
                        }
                    } else {
                        if (!canScrollVertically()) {
                            setCanScrollVertical(true)
                        }
                    }
                }
            }
        })
    }

    //设置当小于visibleCount个数时，是否禁止滑动
    fun setIsForbidScrollWhenItemLessThanVisible(isForbid: Boolean) {
        isForbidScrollWhenItemLessThanVisible = isForbid
    }
}