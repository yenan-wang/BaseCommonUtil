package com.ngb.wyn.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Build
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.recyclerview.widget.RecyclerView
import com.ngb.wyn.common.ui.card.CardBaseLayoutManager
import java.util.*

class RecyclerItemAnimator(private val layoutManager: CardBaseLayoutManager) :
    CustomBaseItemAnimator() {

    private val addHolderList = mutableListOf<Pair<RecyclerView.ViewHolder, Int>>()
    private var animatorFinishedListener: OnAnimationFinishedListener? = null
    private var currentOp = CardBaseLayoutManager.TYPE_DEFAULT

    init {
        layoutManager.addOnItemChangeListener(object : CardBaseLayoutManager.OnItemChangeListener {
            override fun onChange(currentOp: String, positionStart: Int, itemCount: Int) {
                this@RecyclerItemAnimator.currentOp = currentOp
            }
        })
    }

    override fun animateRemoveImpl(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val animation = view.animate()
        mRemoveAnimations.add(holder)
        animation.setDuration(removeDuration)
            .alpha(0f)
            .translationY(-60f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator?) {
                    dispatchRemoveStarting(holder)
                }

                override fun onAnimationEnd(animator: Animator?) {
                    resetRemoveAnimator(animation, holder)
                }

                override fun onAnimationCancel(animator: Animator?) {
                    //resetRemoveAnimator(animation, holder)
                    reset(holder)
                }
            }).start()
    }

    private fun resetRemoveAnimator(
        animation: ViewPropertyAnimator,
        holder: RecyclerView.ViewHolder
    ) {
        animation.setListener(null)
        clearView(holder.itemView)
        dispatchRemoveFinished(holder)
        mRemoveAnimations.remove(holder)
        dispatchFinishedWhenDone()
    }

    override fun animateAddImpl(holder: RecyclerView.ViewHolder) {
        val view = holder.itemView
        val animation = view.animate()
        mAddAnimations.add(holder)
        val index =
            addHolderList.find { it.first == holder }?.let { addHolderList.indexOf(it) } ?: 0
        val delayTime = index.takeIf { it > 0 }?.let { it * 100L } ?: 0L
        animation.setDuration(addDuration)
            .alpha(1f)
            .setStartDelay(delayTime)
            .translationY(0f)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator?) {
                    dispatchAddStarting(holder)
                }

                override fun onAnimationEnd(animator: Animator?) {
                    resetAddAnimator(animation, holder)
                }

                override fun onAnimationCancel(animator: Animator?) {
                    //resetAddAnimator(animation, holder)
                    reset(holder)
                }
            }).start()
    }

    fun resetAddAnimator(animation: ViewPropertyAnimator, holder: RecyclerView.ViewHolder) {
        animation.setListener(null)
        dispatchAddFinished(holder)
        mAddAnimations.remove(holder)
        dispatchFinishedWhenDone()
    }

    override fun preAnimateAddImpl(holder: RecyclerView.ViewHolder?) {
        holder?.let {
            it.itemView.translationY = 60f
            it.itemView.alpha = 0f
            addToHolderList(it)
        }
    }

    override fun onAddFinished(item: RecyclerView.ViewHolder?) {
        item?.let { reset(it) }
        currentOp = CardBaseLayoutManager.TYPE_DEFAULT
        animatorFinishedListener?.animationFinished(item)
    }

    fun setOnAnimationFinishedListener(listener: OnAnimationFinishedListener) {
        animatorFinishedListener = listener
    }

    fun getOnAnimationFinishedListener(): OnAnimationFinishedListener? {
        return animatorFinishedListener
    }

    private fun reset(holder: RecyclerView.ViewHolder) {
        currentOp = CardBaseLayoutManager.TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            addHolderList.removeIf { it.first == holder }
        } else {
            val each = addHolderList.iterator()
            while (each.hasNext()) {
                if (each.next().first == holder) {
                    each.remove()
                }
            }
        }
        clearView(holder.itemView)
    }

    private fun clearView(view: View) {
        view.alpha = 1f
        view.translationY = 0f
    }

    private fun addToHolderList(holder: RecyclerView.ViewHolder) {
        val pos = holder.adapterPosition//holder.absoluteAdapterPosition
        val centerPos = layoutManager.getCenterPosition()
        val itemCount = layoutManager.itemCount
        val isNeedReCount = centerPos + layoutManager.getVisibleCount() / 2 > itemCount - 1
        val addPair = if (isNeedReCount) {
            if (pos >= centerPos) {
                addHolderList.find {
                    it.second > pos || it.second < centerPos
                }
            } else {
                addHolderList.find {
                    it.second in (pos + 1) until centerPos
                }
            }
        } else {
            addHolderList.find {
                it.second > pos
            }
        }
        addPair?.let {
            val indexPos = addHolderList.indexOf(it)
            addHolderList.add(indexPos, Pair(holder, pos))
        } ?: addHolderList.add(Pair(holder, pos))

    }

    interface OnAnimationFinishedListener {
        fun animationFinished(holder: RecyclerView.ViewHolder?)
    }
}