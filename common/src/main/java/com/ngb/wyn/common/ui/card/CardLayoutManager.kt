package com.ngb.wyn.common.ui.card

import android.content.Context
import android.view.View
import kotlin.math.abs

class CardLayoutManager(
    private val context: Context,
    private val isLoopScroll: Boolean = false,
    private val isUseRollerEffect: Boolean = false
) : CardBaseLayoutManager(context, isLoopScroll, isUseRollerEffect) {

    override fun handleView(position: Int, view: View, moveDistanceToCenter: Int) {
        printLog("handleView, pos:$position, moveDistanceToCenter:$moveDistanceToCenter")
        val scaleRadio = computeScale(moveDistanceToCenter)
        view.scaleX = scaleRadio
        view.scaleY = scaleRadio
    }

    override fun scrollXRangeController(dx: Int, hasScrollX: Int): Int {
        var scrollDx = dx
        if (hasScrollX + dx < 0) {
            scrollDx = -hasScrollX //item向左滑动，则本次滑动距离为剩余已滑动的距离，不能再左移，即手指无法再往右滑
        } else if ((hasScrollX + dx) > getOffset()) {
            scrollDx = getOffset() - hasScrollX
        }
        return scrollDx
    }

    private fun computeScale(x: Int): Float {
        var scale = 1f - abs(x * 7.0f / (96f * getIntervalX()))
        if (scale < 0) scale = 0f
        if (scale > 1) scale = 1f
        return scale
    }

    private fun getOffset(): Int {
        return getIntervalX() * (itemCount - 1)
    }
}