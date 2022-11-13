package com.ngb.wyn.common.ui.card

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration

class CardRecyclerView(context: Context, attrs: AttributeSet? = null) :
    CardBaseRecyclerView(context, attrs) {

    private var isNeedLocation = false //是否需要松手自动定位

    init {
        val friction = ViewConfiguration.getScrollFriction() * 2.5F //摩擦系数为2.5
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE && isNeedLocation) {
            isNeedLocation = false //重置
            scrollToPositionCenterSmooth(400)
        }
    }

    override fun onTouchEvent(e: MotionEvent?): Boolean {
        if (e?.actionMasked == MotionEvent.ACTION_UP || e?.actionMasked == MotionEvent.ACTION_CANCEL) {
            isNeedLocation = true
        }
        return super.onTouchEvent(e)
    }
}