package com.ngb.wyn.common.ui.card

import android.content.Context
import android.graphics.Rect
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.core.util.isEmpty
import androidx.core.util.set
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.ngb.wyn.common.BuildConfig
import com.ngb.wyn.common.utils.DimenUtils
import com.ngb.wyn.common.utils.LogUtil
import kotlin.math.abs

abstract class CardBaseLayoutManager @JvmOverloads constructor(
    private val context: Context,
    private val isLoopScroll: Boolean,      //是否循环滚动
    private val isUseRollerEffect: Boolean, //是否使用滚筒效果
    private var isUseFlipDelayEffect: Boolean, //是否使用延迟翻转特效
    @RecyclerView.Orientation private val orientation: Int = RecyclerView.HORIZONTAL
) : RecyclerView.LayoutManager() {

    //双方向公共参数
    //记录item的原始位置
    private val itemOriginalLocation = SparseArray<Rect>()

    //记录已出现的item是否是attach到屏幕上， true表示attach到屏幕上，作用：用于优化
    private val hasAttachedItem = SparseBooleanArray()
    private var onCompletedListener: OnCompleted? = null

    //每次回调onLayoutChild时是否需要从第0个位置开始
    private var isNeedStartFromZero = false
    private var defaultVisibleCount = 0
    private var maxFlipDelaySpeed: Int = 0 //最大步进距离，用于限制延迟翻转特效下，快速滑动的速度，0表示不限制，仅延迟翻转特效开启时有用
    private var lastDirection = DEFAULT_DIRECTION
    private var isChangeDirection = false

    //一次最多可以看见几个item
    private var visibleCount = 0  //这里默认为0，实际使用的时候，一定要大于1
    private var itemWidth = 0     //item的宽
    private var itemHeight = 0    //item的高
    private var logSwitch = false //日志开关，本地调试时可以打开

    //水平方向参数
    private var intervalX = 0   //水平方向item堆叠间距
    private var hasScrollX = 0  //水平方向已经滑动了距离
    private var newHasScrollX = 0  //循环滚动时，新块水平方向已经滑动了距离
    private var flipDelayLeftHasScrollX = 0 //翻转动效下左侧第二层级移动的距离
    private var flipDelayRightHasScrollX = 0 //翻转动效下右侧第二层级移动的距离
    private var flipDelayLeftView: View? = null //翻转动效下左侧第二层级的view
    private var flipDelayRightView: View? = null //翻转动效下右侧第二层级的view
    private var maxAngle = DEFAULT_MAX_ANGLE //翻转最大角度
    private var minAngle = DEFAULT_MIN_ANGLE //翻转最小角度
    private var currentDx = 0   //当前滑动的步进长度
    private var isNeedExecuteRotation = false   //是否需要执行翻转动画
    private var startX = 0       //水平方向起始摆放位置，距离父容器左侧的距离
    private var totalWidth = 0  //item的总宽度
    private var canScrollX = true //是否可以水平滑动

    //垂直方向参数
    private var intervalY = 0   //垂直方向item堆叠间距
    private var hasScrollY = 0  //垂直方向已经滑动了距离
    private var newHasScrollY = 0  //循环滚动时，新块水平方向已经滑动了距离
    private var startY = 0       //垂直方向起始摆放位置，距离父容器顶部的距离
    private var totalHeight = 0 //item的总高度
    private var canScrollY = true //是否可以垂直滑动


    companion object {
        const val TAG = "CardLayoutManager"

        protected const val IN_NO_AREA = 0
        protected const val IN_FIRST_AREA = 1
        protected const val IN_SECOND_AREA = 2

        private const val DEFAULT_DIRECTION = 0 //初始状态
        private const val RIGHT_DIRECTION = 1   //手指右滑
        private const val LEFT_DIRECTION = 2    //手指左滑
        private const val DEFAULT_MAX_ANGLE = 23f //默认最大旋转角度
        private const val DEFAULT_MIN_ANGLE = 0f //默认最小旋转角度
        private const val COEFFICIENT_ANGLE_VARIATION_RANGE = 2    //角度变化范围系数
        private const val COEFFICIENT_ANGLE_RECOVERY = 4    //角度恢复系数
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        printLog("onLayoutChildren")
        recycler?.let { _recycler ->
            printLog("onLayoutChildren, childCount:$childCount, itemCount:$itemCount, state:$state")
            if (state?.isPreLayout == true) {
                //无须执行动画，所以preLayout时，不需要走下面的逻辑
                printLog("onLayoutChildren, is preLayout.")
                return
            }
            //列表为空，则直接剥离
            if (itemCount == 0) {
                detachAndScrapAttachedViews(_recycler) //剥离
                printLog("onLayoutChildren, item is empty, no need layout.")
                return
            }

            printLog("onLayoutChildren, isNeedStartFromZero:$isNeedStartFromZero")
            val firstPos = if (isNeedStartFromZero) {
                isNeedStartFromZero = false  //使用完需要重新置为false
                hasScrollX = 0
                //当开启循环时，由于默认将第一位摆在正中间，所以需要将左侧空白处填满，且必须在第0个填充之前添加，否则getChild时，获取第一个可见view的位置就不对了。
                if (isLoopScroll) {
                    0 - visibleCount / 2
                } else {
                    0
                }
            } else {
                //必须在剥离前获取，否则一旦剥离，childCount就变成0了
                val firstView = getChildAt(0)
                printLog("onLayoutChildren, firstView:$firstView")
                firstView?.let { getPosition(it) } ?: 0
            }
            printLog("onLayoutChildren, firstPos:$firstPos")
            detachAndScrapAttachedViews(_recycler) //剥离

            //各种缓存记录清除，防止异常
            itemOriginalLocation.clear()
            hasAttachedItem.clear()

            //首先要测量item的大小， 因为item大小都是一样的，所以取第0个进行测量
            val measureView = _recycler.getViewForPosition(0)
            //必须先测量
            measureChildWithMargins(measureView, 0, 0)
            //才能获取到宽和高
            itemWidth = getDecoratedMeasuredWidth(measureView)
            itemHeight = getDecoratedMeasuredHeight(measureView)
            //确保默认情况下，首张的起始位置放置于ViewGroup容器的中间位置
            startX = width / 2 - itemWidth / 2
            //非异形屏计算间距
            intervalX = (getHorizontalSpace() - DimenUtils.dp2px(context, 82.67f)) / 4
            //计算列表可见item数量，visibleCount即初始化时父容器里最多摆放item的数量
            //计算方式是 可用宽度，除以间距（即(getHorizontalSpace() / intervalX)）
            visibleCount = getHorizontalSpace() / intervalX
            //checkVisibleCountHorizontal()
            printLog(
                "onLayoutChildren, startX:$startX, width:$width, itemWidth:$itemWidth, " +
                        "intervalX:$intervalX, visibleCount:$visibleCount"
            )

            //开始依次记录摆放计算
            if (orientation == RecyclerView.HORIZONTAL) {
                //1、首先记录每个item的初始位置
                var offsetX = 0
                for (i in 0 until itemCount) {
                    val rect = Rect(startX + offsetX, 0, startX + itemWidth + offsetX, itemHeight)
                    itemOriginalLocation[i] = rect
                    hasAttachedItem[i] = false    //默认初始都是未添加到屏幕上
                    offsetX += if (i != itemCount - 1) {  //最后一项要加上item的宽度，不然计算总长度时会缺少一部分
                        intervalX
                    } else {
                        itemWidth
                    }
                }
                //取offsetX和getHorizontalSpace()较大的一个，即当item没有填满列表时，以列表宽度为准
                totalWidth = offsetX.coerceAtLeast(getHorizontalSpace())

                //2、摆放item
                //给endPos加一个判定范围
                val endPos = if (isLoopScroll) {
                    firstPos + itemCount   //循环时，往后循环itemCount次就好了
                } else {
                    itemCount
                }
                printLog("onLayoutChildren, firstPos:$firstPos, endPos:$endPos")
                //从屏幕内第一个item的位置开始添加
                for (i in firstPos until endPos) {
                    insertView(i, _recycler)
                }
            } else if (orientation == RecyclerView.VERTICAL) {
                printLog("学着自己写。")
                //跟水平方向类似。
            }
        } ?: printLog("onLayoutChildren, recycler is null and meaningless.")
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        printLog("scrollHorizontallyBy.")
        if (childCount <= 0 || recycler == null) {
            printLog("scrollHorizontallyBy, return. childCount:$childCount, recycler:$recycler")
            return dx
        }

        //本次移动的距离， 默认是dx。注意，这里是手指向右滑，item列表会滑动到左侧，dx是负数，手指向左滑，item列表会滑动到右侧，dx是负数
        currentDx = dx
        //记录用户滑动途中是否有更改方向，dx大于零，手指向左滑动，列表右滑；反之相反
        if (currentDx > 0) {
            if (lastDirection == RIGHT_DIRECTION) {
                isChangeDirection = true
            }
            lastDirection = LEFT_DIRECTION
        } else if (currentDx < 0) {
            if (lastDirection == LEFT_DIRECTION) {
                isChangeDirection = true
            }
            lastDirection = RIGHT_DIRECTION
        }

        val consumeDx = scrollXRangeController(dx, hasScrollX)
        val scrollDx = if (isUseFlipDelayEffect
            && maxFlipDelaySpeed != 0
            && (abs(consumeDx) > maxFlipDelaySpeed)
        ) {
            maxFlipDelaySpeed * (consumeDx / abs(consumeDx))
        } else {
            consumeDx
        }
        //先把此次移动的距离加上
        hasScrollX += scrollDx

        //然后做两个步骤，回收已滑出屏幕可见区域外的，补充滑到屏幕可见区域内却没有添加到该区域内的view
        //1、回收不可见区域内的item
        //这里倒序，是因为每回收一个(removeAndRecycleView())，childCount会减少一个，为了能准确获取对应位置上的view，所以倒序
        for (i in childCount - 1 downTo 0) {
            val view = getChildAt(i)
            view?.let {
                val pos = getPosition(it)
                printLog("scrollHorizontallyBy, pos:$pos")
                val viewOriginalLocation = itemOriginalLocation[pos]
                //如果当前该view的位置不在当前可见区域内，则需要回收
                val visibleAreaPair = isInVisibleArea(viewOriginalLocation)
                if (!visibleAreaPair.first) {
                    removeAndRecycleView(it, recycler) //告诉recycler，回收该view
                    hasAttachedItem.put(pos, false)    //标记为detach的，即未被attached
                } else {
                    putInList(it, pos, viewOriginalLocation, visibleAreaPair)
                }
            }
        }

        //2、填充空白区域
        //手指左滑，item列表右移
        if (scrollDx >= 0) {
            //那么就从屏幕可见区域内第一个位置开始，直到最后一个item，依次插入
            val firstView = getChildAt(0)
            val minPos = firstView?.let { getPosition(it) } ?: findFirstPosInArea()
            //循环滚动时，往后找itemCount个view，否则找到底就可以了
            val endPos = if (isLoopScroll) {
                minPos + itemCount
            } else {
                itemCount
            }
            for (i in minPos until endPos) {
                insertView(i, recycler)
            }
        }
        //手指右滑，item列表左移
        else {
            //那么从屏幕可见区域内最后一个位置开始，直到第0个，依次插入
            val lastView = getChildAt(childCount - 1)
            val maxPos = lastView?.let { getPosition(it) } ?: findLastPosInArea()
            //循环滚动时，往前找itemCount个view，否则找到头就可以了
            val endPos = if (isLoopScroll) {
                maxPos - itemCount
            } else {
                0
            }
            for (i in maxPos downTo endPos) {
                insertView(i, recycler, true)
            }
        }
        return consumeDx
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        when (state) {
            RecyclerView.SCROLL_STATE_IDLE -> {
                //某些时候，停在某些尴尬的位置，导致无法在停止时，角度无法恢复至最小角度，所以这里加个兜底处理
                if (!isNeedExecuteRotation) {
                    rotationAngle()
                }
                isNeedExecuteRotation = false
                isChangeDirection = false
                lastDirection = DEFAULT_DIRECTION
            }
            RecyclerView.SCROLL_STATE_DRAGGING -> {
                isNeedExecuteRotation = true
            }
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ): Int {
        //todo for support vertical
        return super.scrollVerticallyBy(dy, recycler, state)
    }

    override fun canScrollHorizontally(): Boolean {
        return orientation == RecyclerView.HORIZONTAL && canScrollX
    }

    override fun canScrollVertically(): Boolean {
        return orientation == RecyclerView.VERTICAL && canScrollY
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView?,
        state: RecyclerView.State?,
        position: Int
    ) {
        recyclerView?.apply {
            val linearSmoothScroller = LinearSmoothScroller(context)
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        }
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        onCompletedListener?.completed(state)
    }

    /**
     * 设置本类中的日志开关
     * 只有debug环境配置生效，非debug环境无法打开
     */
    fun setLogSwitch(isOpen: Boolean) {
        logSwitch = if (BuildConfig.DEBUG) {
            isOpen
        } else {
            false
        }
    }

    /**
     * intervalX是有可能作为除数的，除数不能为0， 所以限制从1开始
     */
    //暂时设置无效
    fun setIntervalX(@IntRange(from = 1) intervalX: Int) {
        this.intervalX = intervalX
    }

    /**
     * intervalY是有可能作为除数的，除数不能为0， 所以限制从1开始
     */
    //暂时设置无效
    fun setIntervalY(@IntRange(from = 1) intervalY: Int) {
        this.intervalY = intervalY
    }

    /**
     * 最少也得有一个可见，否则，列表显示无意义
     * 注意，这个visibleCount在真正使用时，会进行判断，当父容器显示的长度允许能显示的长度大于visibleCount时，才生效
     * 否则计算会以父容器一次最多显示的个数为visibleCount， 超出的个数在屏幕外，意义
     */
    //暂时设置无效
    fun setVisibleCount(@IntRange(from = 1) visibleCount: Int) {
        this.visibleCount = visibleCount
    }

    //设置使用延迟翻转动效
    fun setIsUseFlipDelayEffect(isUseFlipDelayEffect: Boolean) {
        this.isUseFlipDelayEffect = isUseFlipDelayEffect
    }

    //是否使用延迟翻转动效
    fun canUseFlipDelayEffect(): Boolean {
        return isUseFlipDelayEffect
    }

    //最大步进距离，用于限制延迟翻转特效下，快速滑动的速度，0表示不限制，仅延迟翻转特效开启时有用
    fun setMaxFlipDelaySpeed(maxFlipDelaySpeed: Int) {
        this.maxFlipDelaySpeed = maxFlipDelaySpeed
    }

    @JvmOverloads
    fun setAngelRange(
        @FloatRange(from = 1.0, to = 360.0) maxAngle: Float,
        @FloatRange(from = 0.0) minAngle: Float = DEFAULT_MIN_ANGLE
    ) {
        if (maxAngle < minAngle) {
            return
        }
        this.maxAngle = maxAngle
        this.minAngle = minAngle
    }

    //当列表全部重新update时，需要设置为true
    fun setNeedStartFromZero() {
        isNeedStartFromZero = true
    }

    fun setOnCompletedListener(listener: OnCompleted) {
        this.onCompletedListener = listener
    }

    fun getOrientation(): Int {
        return orientation
    }

    fun setCanScrollX(canScroll: Boolean) {
        canScrollX = canScroll
    }

    fun setCanScrollY(canScroll: Boolean) {
        canScrollY = canScroll
    }

    /**
     * 让targetPosition位置滑动到中间位置，平滑滑动
     * @param recyclerView   列表
     * @param targetPosition 需要滑动到中间位置的item的位置
     * @param duration       滑动时长
     * @param interpolator   滑动插值器
     */
    @JvmOverloads
    fun scrollToPositionCenterSmooth(
        recyclerView: RecyclerView,
        targetPosition: Int,
        duration: Int,
        interpolator: Interpolator = AccelerateInterpolator()
    ) {
        printLog("scrollToPositionCenterSmooth, targetPosition:$targetPosition")
        val distance = getDistanceFromTargetToCenter(targetPosition)

        if (distance != 0) {
            printLog("scrollToPositionCenterSmooth, distance:$distance, duration:$duration")
            if (orientation == RecyclerView.HORIZONTAL) {
                recyclerView.smoothScrollBy(distance, 0, interpolator, duration)
            } else if (orientation == RecyclerView.VERTICAL) {
                recyclerView.smoothScrollBy(0, distance, interpolator, duration)
            }
        }
    }

    /**
     * 让targetPosition位置滑动到中间位置，瞬间滑动
     * @param recyclerView   列表
     * @param targetPosition 需要滑动到中间位置的item的位置
     */
    fun scrollToPositionCenter(
        recyclerView: RecyclerView,
        targetPosition: Int
    ) {
        printLog("scrollToPositionCenter, targetPosition:$targetPosition")
        val distance = getDistanceFromTargetToCenter(targetPosition)

        if (distance != 0) {
            printLog("scrollToPositionCenter, distance:$distance")
            if (orientation == RecyclerView.HORIZONTAL) {
                recyclerView.scrollBy(distance, 0)
            } else if (orientation == RecyclerView.VERTICAL) {
                recyclerView.scrollBy(0, distance)
            }
        }
    }

    fun getFirstVisiblePosition(): Int {
        if (childCount <= 0) {
            return 0
        }
        val view = getChildAt(0)
        return view?.let { getPosition(it) } ?: 0
    }

    fun getLastVisiblePosition(): Int {
        if (childCount <= 0) {
            return 0
        }
        val view = getChildAt(childCount - 1)
        return view?.let { getPosition(it) } ?: (itemCount - 1)
    }

    /**
     * 获取view绘制层级顺序
     */
    fun getChildDrawingOrderCenter(): Int {
        val centerPos = getCenterPosition()
        val firstPos = getFirstVisiblePosition()
        printLog("getChildDrawingOrderCenter, centerPos:$centerPos, firstPos:$firstPos, itemCount:$itemCount")
        return (centerPos - firstPos).takeIf { it >= 0 } ?: let { centerPos + itemCount - firstPos }
    }

    fun getCenterPosition(): Int {
        if (orientation == RecyclerView.HORIZONTAL) {
            if (intervalX < 1) {
                return 0
            }
            var pos = hasScrollX / intervalX
            val more = hasScrollX % intervalX
            if (more > intervalX * 0.5f) {
                pos++
            } else if (more < intervalX * -0.5f) {
                pos--
            }
            return dealPos(pos)
        } else {
            if (intervalY < 1) {
                return 0
            }
            var pos = hasScrollY / intervalY
            val more = hasScrollY % intervalY
            if (more > intervalY * 0.5f) {
                pos++
            } else if (more < intervalY * -0.5f) {
                pos--
            }
            return dealPos(pos)
        }
    }

    protected fun printLog(logContent: String) {
        if (logSwitch) {
            LogUtil.d(TAG, logContent)
        }
    }

    protected fun getIntervalX(): Int {
        return intervalX
    }

    protected fun getTotalWidth(): Int {
        return totalWidth
    }

    protected fun getHasScrollX(): Int {
        return hasScrollX
    }

    protected fun getItemOriginalLocationByPos(position: Int): Rect? {
        if (position < 0 || position > itemOriginalLocation.size() - 1) {
            return null
        }
        return itemOriginalLocation[position]
    }

    //是否使用循环滚动效果
    protected fun canLoopScroll(): Boolean {
        return isLoopScroll
    }

    //是否使用滚筒效果
    protected fun canUseRollerEffect(): Boolean {
        return isUseRollerEffect
    }

    //处理循环滚动时，超出0 ~ itemCount - 1范围外的position
    protected fun dealPos(position: Int): Int {
        if (itemCount == 0) { //一般不会进入此条件
            return 0
        }
        val dealPos = if (position >= 0) {
            position % itemCount
        } else {
            val absPos = ((-position - 1) % itemCount)
            itemCount - 1 - absPos
        }
        printLog("dealPos:$dealPos")
        return dealPos
    }

    //获取水平方向可以绘制view区域的宽度
    protected fun getHorizontalSpace(): Int {
        return width - paddingLeft - paddingRight
    }

    //获取垂直方向可以绘制view区域的高度
    protected fun getVerticalSpace(): Int {
        return height - paddingTop - paddingBottom
    }

    /**
     * 水平滑动时，获取当前可见区域
     *
     * @explain 说明一下，怎么算的
     * 整个计算过程，你可以看成列表不动，屏幕在左右滑动，屏幕框柱的区域就是当前可见的范围
     * 于是有：
     * 在不考虑padding的情况下，初始可见区域就是(0,0,width,height)，即父容器区域，width和height为父容器宽高，
     * 当有滑动时，滑动距离为hasScrollX，那么此时可见区域为(hasScrollX,0,width + hasScrollX,height)，
     * 即初始可见区域 加上 水平方向上滑动的距离，就是当前可见的区域；
     *
     * 如果考虑padding，那么左侧要加上paddingLeft， 右侧要减去paddingRight，上侧加上paddingTop，下侧减去paddingBottom，
     * 即再去掉四周的padding所占的空间。
     *
     * 计算完毕！
     */
    protected fun getVisibleAreaHorizontal(): Array<Rect> {
        val rectArray = Array(2) {
            Rect(
                paddingLeft + hasScrollX,
                paddingTop,
                width - paddingRight + hasScrollX,
                height - paddingBottom
            )
        }

        val firstArray = rectArray[0]
        val secondArray = rectArray[1]

        return if (isLoopScroll) {
            val headLeftBaseLine = -((visibleCount / 2) + 1) * intervalX
            val headRightBaseLine = (visibleCount / 2) * intervalX

            val bottomLeftBaseLine = (itemCount - ((visibleCount / 2) + 1)) * intervalX
            val bottomRightBaseLine = (itemCount + (visibleCount / 2)) * intervalX
            printLog("getVisibleAreaHorizontal, hasScrollX:$hasScrollX, headLeftBaseLine:$headLeftBaseLine, headRightBaseLine:$headRightBaseLine, bottomLeftBaseLine:$bottomLeftBaseLine, bottomRightBaseLine:$bottomRightBaseLine")
            if (hasScrollX <= headLeftBaseLine) {
                printLog("getVisibleAreaHorizontal, 1")
                hasScrollX = (itemCount - 1 - (visibleCount / 2)) * intervalX
                newHasScrollX = 0
                firstArray.left = paddingLeft + startX + hasScrollX
                firstArray.right = width - paddingRight + hasScrollX
                secondArray.setEmpty()
            } else if (hasScrollX in (headLeftBaseLine + 1) until headRightBaseLine) {
                printLog("getVisibleAreaHorizontal, 2")
                val more = hasScrollX - headRightBaseLine  //这里more一定是负数
                newHasScrollX =
                    more + ((itemCount - 1) * intervalX) + ((visibleCount / 2 + 1) * intervalX)
                firstArray.left =
                    paddingLeft + startX    //等同于 firstItemLocation.left，即第一个item originalLocation的left值
                secondArray.left = paddingLeft + startX + totalWidth + more
                secondArray.right = paddingLeft + startX + totalWidth
            } else if (hasScrollX in headRightBaseLine..bottomLeftBaseLine) {
                printLog("getVisibleAreaHorizontal, 3")
                secondArray.setEmpty()
            } else if (hasScrollX in (bottomLeftBaseLine + 1) until bottomRightBaseLine) {
                printLog("getVisibleAreaHorizontal, 4")
                val more = hasScrollX - bottomLeftBaseLine  //这里的more一定为负数
                newHasScrollX = more - (visibleCount / 2 + 1) * intervalX
                firstArray.right =
                    paddingLeft + startX + totalWidth  //等同于 lastItemLocation.right，即最后一个item originalLocation的right值
                secondArray.left = paddingLeft + startX
                secondArray.right = paddingLeft + startX + more
            } else if (hasScrollX >= bottomRightBaseLine) {
                printLog("getVisibleAreaHorizontal, 5")
                hasScrollX = (visibleCount / 2) * intervalX
                newHasScrollX = 0
                firstArray.left = paddingLeft + hasScrollX
                firstArray.right = width - paddingRight + hasScrollX
                secondArray.setEmpty()
            }
            printLog("getVisibleAreaHorizontal, rectArray[0]: ${rectArray[0]}, rectArray[1]:${rectArray[1]}")
            rectArray
        } else {
            secondArray.setEmpty()
            rectArray
        }
    }

    protected fun getVisibleAreaVertical(): Array<Rect> {
        val rectArray = Array(2) {
            Rect(
                paddingLeft,
                paddingTop + hasScrollY,
                width - paddingRight,
                height - paddingBottom + hasScrollY
            )
        }
        //todo to support vertical
        return rectArray
    }

    /**
     * 添加item到父容器中，添加后，就可以在屏幕上看到它们
     * @param position     位置，这个位置是item在全部itemList中的位置
     * @param visibleArea  当前可见区域，{@link #getVisibleArea()}
     * @param recycler     不多解释
     * @param isAddFirst   是否添加到头部
     */
    private fun insertView(
        position: Int,
        recycler: RecyclerView.Recycler,
        isAddFirst: Boolean = false
    ) {
        //转换pos
        val pos = dealPos(position)
        //获取item原始位置
        val originalLocation = itemOriginalLocation[pos]
        //如果item的位置刚好在屏幕可见区域内，且未attach的，就添加上
        val visibleAreaPair = isInVisibleArea(originalLocation)
        printLog("insertView, position:$position, pos:$pos, visibleAreaPair:$visibleAreaPair")
        if (visibleAreaPair.first && !hasAttachedItem.get(pos)) {
            //从recycler中申请该位置的view
            val view = recycler.getViewForPosition(pos)
            //将该位置的view添加到容器中
            if (isAddFirst) {
                addView(view, 0)
            } else {
                addView(view)
            }
            //同样，要先测量该view
            measureChildWithMargins(view, 0, 0)
            //然后才能知道该将它放在什么位置，即放在原位置移动了hasScrollX距离的位置
            putInList(view, pos, originalLocation, visibleAreaPair)
        }
    }

    private fun putInList(
        view: View,
        pos: Int,
        viewOriginalLocation: Rect,
        visibleAreaPair: Pair<Boolean, Int>
    ) {
        if (orientation == RecyclerView.HORIZONTAL) {
            putInListHorizontal(view, pos, viewOriginalLocation, visibleAreaPair)
        } else {
            putInListVertical(view, pos, viewOriginalLocation, visibleAreaPair)
        }
    }

    private fun putInListHorizontal(
        view: View,
        pos: Int,
        viewOriginalLocation: Rect,
        visibleAreaPair: Pair<Boolean, Int>
    ) {

        val hasScroll = if (isLoopScroll && visibleAreaPair.second == IN_SECOND_AREA) {
            newHasScrollX   //只在开启循环滚动，且处于second区域内可见的view使用newHasScrollX，否则仍旧使用hasScrollX
        } else {
            hasScrollX
        }

        val delayScrollX = if (isUseFlipDelayEffect) {
            if (visibleCount < 5) {
                //可见数量小于5时，不支持翻转延迟效果，因为小于5时，滚筒动效和翻转延迟动效会有冲突
                this.isUseFlipDelayEffect = false
                printLog("putInListHorizontal, visibleCount:$visibleCount, not support while visible count smaller than five.")
                hasScroll
            } else {
                //此时距离中心itemRect.left的距离
                val distanceToCenter = viewOriginalLocation.left - hasScroll - startX
                printLog("putInListHorizontal, pos:$pos, distanceToCenter:$distanceToCenter, intervalX:$intervalX")
                //记录移动到距离中心点距离为-intervalX的view，是中心左侧的第二层级的view
                if (distanceToCenter in -intervalX until 0) {
                    if (flipDelayLeftView != view) {
                        flipDelayLeftView = view
                        flipDelayLeftHasScrollX = (pos + 1) * intervalX //记录当前的位置
                        printLog("putInListHorizontal, pos left:$pos, flipDelayLeftHasScrollX:$flipDelayLeftHasScrollX")
                    }
                }
                if (distanceToCenter in 1..intervalX) {
                    if (flipDelayRightView != view) {
                        flipDelayRightView = view
                        flipDelayRightHasScrollX = (pos - 1) * intervalX //记录当前的位置
                        printLog("putInListHorizontal, pos right:$pos, flipDelayRightHasScrollX:$flipDelayRightHasScrollX")
                    }
                }
                //临界值
                val criticalValue = (intervalX / 2 + (intervalX - itemWidth / 2))
                //val criticalValue = ((intervalX / 2 +(intervalX - itemWidth / 2)) * (89f / 96f)).toInt() // 在不使用旋转角度时使用
                printLog("putInListHorizontal, criticalValue:$criticalValue, pos:$pos, view:${view.hashCode()}, flipDelayLeftView:${flipDelayLeftView.hashCode()}, flipDelayRightView:${flipDelayRightView.hashCode()}")
                //只有位于中心两侧第二层级上的view，才需要延迟位移
                if (flipDelayRightView == view) {
                    //在 criticalValue ~ intervalX 范围内，静止不动
                    if (distanceToCenter in criticalValue until intervalX) {
                        printLog("putInListHorizontal, f1 pos:$pos")
                        flipDelayRightHasScrollX
                    }
                    //在 0 ~ criticalValue 范围内加速赶上
                    else if (distanceToCenter in 1 until criticalValue) {
                        val newFlipDelayRightHasScrollX =
                            flipDelayRightHasScrollX + (criticalValue - distanceToCenter) * 2 //加速赶上
                        printLog(
                            "putInListHorizontal, f2 pos:$pos, distanceToCenter:$distanceToCenter, flipDelayRightHasScrollX:$flipDelayRightHasScrollX, " +
                                    "newFlipDelayRightHasScrollX:$newFlipDelayRightHasScrollX, hasScroll:$hasScroll"
                        )
                        //如果还没赶上，就使用加速的距离，否则就使用原速距离
                        if (newFlipDelayRightHasScrollX < hasScroll) {
                            newFlipDelayRightHasScrollX
                        } else {
                            hasScroll
                        }
                    } else {
                        printLog("putInListHorizontal, f3 pos:$pos")
                        hasScroll
                    }
                } else if (flipDelayLeftView == view) {
                    //在 -intervalX ~ criticalValue 范围内静止不动
                    if (distanceToCenter in (-intervalX + 1)..-criticalValue) {
                        printLog("putInListHorizontal, f4 pos:$pos")
                        flipDelayLeftHasScrollX
                    }
                    //在 -criticalValue ~ 0 范围内加速赶上
                    else if (distanceToCenter in -criticalValue + 1 until 0) {
                        val newFlipDelayLeftHasScrollX =
                            flipDelayLeftHasScrollX + (-criticalValue - distanceToCenter) * 2 //加速赶上
                        printLog(
                            "putInListHorizontal, f5 pos:$pos, distanceToCenter:$distanceToCenter, flipDelayLeftHasScrollX:$flipDelayLeftHasScrollX, " +
                                    "newFlipDelayLeftHasScrollX:$newFlipDelayLeftHasScrollX, hasScroll:$hasScroll"
                        )
                        //如果还没赶上，就使用加速的距离，否则就使用原速距离
                        if (newFlipDelayLeftHasScrollX > hasScroll) {
                            newFlipDelayLeftHasScrollX
                        } else {
                            hasScroll
                        }
                    } else {
                        printLog("putInListHorizontal, f6 pos:$pos")
                        hasScroll
                    }
                } else {
                    printLog("putInListHorizontal, f7 pos:$pos")
                    hasScroll
                }
            }
        } else {
            hasScroll
        }
        val scrollX = if (isUseRollerEffect) {
            val leftBaseLine = -visibleCount / 2 * intervalX
            val rightBaseLine = visibleCount / 2 * intervalX
            printLog("putInListHorizontal, leftBaseLine:$leftBaseLine, rightBaseLine:$rightBaseLine")
            val distanceToCenter = viewOriginalLocation.left - hasScroll - startX
            if (distanceToCenter < leftBaseLine) {
                hasScroll + 2 * (distanceToCenter - leftBaseLine)
            } else if (distanceToCenter > rightBaseLine) {
                hasScroll + 2 * (distanceToCenter - rightBaseLine)
            } else {
                delayScrollX
            }
        } else {
            delayScrollX
        }
        printLog("putInListHorizontal, scrollX:$scrollX")

        //不需要回收的，直接摆放到最新的位置上
        layoutDecoratedWithMargins(
            view,
            viewOriginalLocation.left - scrollX,
            viewOriginalLocation.top,
            viewOriginalLocation.right - scrollX,
            viewOriginalLocation.bottom
        )
        //标记已经attached
        hasAttachedItem.put(pos, true)
        handleView(pos, view, viewOriginalLocation.left - hasScroll - startX, hasScrollX)
    }

    private fun putInListVertical(
        view: View,
        pos: Int,
        viewOriginalLocation: Rect,
        visibleAreaPair: Pair<Boolean, Int>
    ) {
        //TODO for support vertical
    }

    private fun getDistanceFromTargetToCenter(targetPosition: Int): Int {
        if (itemCount == 0 || itemOriginalLocation.isEmpty()) {
            printLog("getDistanceFromTargetToCenter, return.")
            return 0
        }

        //if (isLoopScroll) {
        val targetPos = dealPos(targetPosition)
        val currentCenterPos = getCenterPosition()
        val positiveDuration = targetPos - currentCenterPos
        val negativeDuration = targetPos - (currentCenterPos + itemCount)
        val minDuration = if (abs(positiveDuration) < abs(negativeDuration)) {
            positiveDuration
        } else {
            negativeDuration
        }
        val distance = if (orientation == RecyclerView.HORIZONTAL) {
            var more = hasScrollX % intervalX
            if (more > intervalX / 2) {
                more -= intervalX
            } else if (more < -intervalX / 2) {
                more += intervalX
            }
            minDuration * intervalX - more
        } else {
            var more = hasScrollY % intervalY
            if (more > intervalY / 2) {
                more -= intervalY
            } else if (more < -intervalY / 2) {
                more += intervalY
            }
            minDuration * intervalY - more
        }
        printLog("getDistanceFromTargetToCenter, targetPos:$targetPos, currentCenterPos:$currentCenterPos, minDuration:$minDuration, distance：$distance.")
        return distance
        //}

        /*//兜底pos，防止越界
        val pos = when {
            targetPosition < 0 -> {
                0
            }
            targetPosition > itemCount - 1 -> {
                itemCount - 1
            }
            else -> {
                targetPosition
            }
        }
        printLog("getDistanceFromTargetToCenter, pos:$pos")

        val target = itemOriginalLocation.get(pos)?.left
        val first = itemOriginalLocation.get(0)?.left
        *//*
        val currentCenterPos = getCenterPositionHorizontal()
        val current = itemOriginalLocation.get(currentCenterPos)
        *//*
        val distance = if (target != null && first != null) {
            -hasScrollX + target - first // target - current - (hasScrollX % (interval / 2))也行
        } else {
            0
        }
        printLog("getDistanceFromTargetToCenter, horizontal distance:$distance")
        return distance*/
    }

    private fun findFirstPosInArea(): Int {
        val rectArray = getVisibleAreaHorizontal()
        val firstArray = rectArray[0]
        val secondArray = rectArray[1]
        when {
            //列表右滑到队尾时，first是当前可见范围的队尾，second是进入可见范围的队头区域，从第一区域内找第一个
            firstArray.right > secondArray.left -> {
                for (i in 0 until itemOriginalLocation.size()) {
                    if (isRectInArea(firstArray, itemOriginalLocation[i])) {
                        return i
                    }
                }
                return 0
            }
            //列表左滑到队头时，first是当前可见范围的队头，second是进入可见范围的队尾区域，从第二区域内找第一个
            firstArray.right < secondArray.left -> {
                for (i in 0 until itemOriginalLocation.size()) {
                    if (isRectInArea(secondArray, itemOriginalLocation[i])) {
                        return i
                    }
                }
                return 0
            }
            else -> {
                return 0
            }
        }
    }

    private fun findLastPosInArea(): Int {
        val rectArray = getVisibleAreaHorizontal()
        val firstArray = rectArray[0]
        val secondArray = rectArray[1]
        when {
            //列表右滑到队尾时，first是当前可见范围的队尾，second是进入可见范围的队头区域，从第二区域内找最后一个
            firstArray.right > secondArray.left -> {
                for (i in itemOriginalLocation.size() - 1 downTo 0) {
                    if (isRectInArea(secondArray, itemOriginalLocation[i])) {
                        return i
                    }
                }
                return itemCount - 1
            }
            //列表左滑到队头时，first是当前可见范围的队头，second是进入可见范围的队尾区域，从第二区域内找第一个
            firstArray.right < secondArray.left -> {
                for (i in itemOriginalLocation.size() - 1 downTo 0) {
                    if (isRectInArea(firstArray, itemOriginalLocation[i])) {
                        return i
                    }
                }
                return itemCount - 1
            }
            else -> {
                return itemCount - 1
            }
        }
    }

    private fun checkVisibleCountHorizontal() {
        defaultVisibleCount = getHorizontalSpace() / (if (orientation == RecyclerView.HORIZONTAL) {
            intervalX
        } else {
            intervalY
        })
        printLog("checkVisibleCountHorizontal, visibleCount:$visibleCount, defaultVisibleCount:$defaultVisibleCount")
        //取两者较小的那一个
        visibleCount = if (visibleCount > 0) {
            defaultVisibleCount.coerceAtMost(visibleCount)
        } else {
            defaultVisibleCount
        }
        printLog("checkVisibleCountHorizontal, visibleCount:$visibleCount")
    }

    private fun checkVisibleCountVertical() {
        //todo for support vertical
    }

    private fun isInVisibleArea(viewOriginalLocation: Rect): Pair<Boolean, Int> {
        val visibleAreas = getVisibleAreaHorizontal()
        val firstVisibleArea = visibleAreas[0]
        val secondVisibleArea = visibleAreas[1]
        val isIntersect = if (isLoopScroll) {
            val isInSecondArea = !secondVisibleArea.isEmpty && isRectInArea(
                secondVisibleArea,
                viewOriginalLocation
            )
            if (isInSecondArea) {
                printLog("isInVisibleArea, loopScroll, second.")
                Pair(true, IN_SECOND_AREA)
            } else if (isRectInArea(firstVisibleArea, viewOriginalLocation)) {
                printLog("isInVisibleArea, loopScroll, first.")
                Pair(true, IN_FIRST_AREA)
            } else {
                printLog("isInVisibleArea, loopScroll, no.")
                Pair(false, IN_NO_AREA)
            }
        } else {
            val isInVisibleArea = isRectInArea(firstVisibleArea, viewOriginalLocation)
            if (isInVisibleArea) {
                printLog("isInVisibleArea, no loopScroll, first.")
                Pair(true, IN_FIRST_AREA)
            } else {
                printLog("isInVisibleArea, no loopScroll, no.")
                Pair(false, IN_NO_AREA)
            }
        }
        printLog("isInVisibleArea, isIntersect:$isIntersect")
        return isIntersect
    }

    private fun isRectInArea(
        rangeRect: Rect,
        compareRect: Rect,
        isNeedJudgeCenter: Boolean = true
    ): Boolean {
        val isIntersect = Rect.intersects(rangeRect, compareRect)
        printLog("isRectInArea, isIntersect:$isIntersect, rangeRect:$rangeRect, compareRect:$compareRect")
        return isIntersect
        /*
        return if (isNeedJudgeCenter) {
            val midPointCoordinate = (compareRect.left + compareRect.right) / 2
            val isInRange =
                (midPointCoordinate >= rangeRect.left) && (midPointCoordinate <= rangeRect.right)
            printLog("isRectInArea, isIntersect:$isIntersect, midPointCoordinate:$midPointCoordinate, isInRange:$isInRange")
            isIntersect && isInRange
        } else {
            isIntersect
        }
        */
    }

    private fun rotationAngle() {
        for (i in 0 until childCount) {
            getChildAt(i)?.let {
                if (it.rotationY != minAngle) {
                    it.rotationY = minAngle
                }
            }
        }
    }

    private fun getAngularRate(): Int {
        return COEFFICIENT_ANGLE_RECOVERY * currentDx
    }

    protected open fun computeRotation(currentRotationY: Float, x: Int, hasScroll: Int): Float {
        val modDouble = (abs(hasScroll) % (2 * intervalX))
        val modSingle = (abs(hasScroll) % intervalX)
        val modContinuity = if (modSingle == modDouble) {
            modSingle
        } else {
            2 * intervalX - modDouble
        }
        //iss: 0 ~ (maxAngle * 2) ~ 0 ~ (maxAngle * 2) ~ 0
        val iss = modSingle * (maxAngle / (intervalX / COEFFICIENT_ANGLE_VARIATION_RANGE))
        printLog("computeRotation, currentRotationY:$currentRotationY, isChangeDirection:$isChangeDirection, currentDx:$currentDx, iss:$iss")
        val newAngle = if (isNeedExecuteRotation) {
            val coverRotation = if (currentDx > 0f) {
                if (currentRotationY < maxAngle) {
                    if (isChangeDirection) {
                        (currentRotationY + getAngularRate()).coerceAtMost(maxAngle)
                    } else {
                        iss.coerceAtMost(maxAngle)
                    }
                } else {
                    maxAngle
                }
            } else {
                if (currentRotationY > -maxAngle) {
                    if (isChangeDirection) {
                        (currentRotationY + getAngularRate()).coerceAtLeast(-maxAngle)
                    } else {
                        (iss - COEFFICIENT_ANGLE_VARIATION_RANGE * maxAngle).coerceAtLeast(-maxAngle)
                    }
                } else {
                    -maxAngle
                }
            }
            if (abs(coverRotation) == maxAngle) {
                isChangeDirection = false
            }
            coverRotation
        } else {
            val recoverRotation = if (currentDx > 0) {
                if (currentRotationY > minAngle) {
                    (currentRotationY - getAngularRate()).coerceAtLeast(minAngle)
                } else if (currentRotationY < minAngle) {
                    (currentRotationY + getAngularRate()).coerceAtMost(minAngle)
                } else {
                    minAngle
                }
            } else {
                if (currentRotationY > minAngle) {
                    (currentRotationY + getAngularRate()).coerceAtLeast(minAngle)
                } else if (currentRotationY < minAngle) {
                    (currentRotationY - getAngularRate()).coerceAtMost(minAngle)
                } else {
                    minAngle
                }
            }
            if (recoverRotation == minAngle) {
                isChangeDirection = false
            }
            recoverRotation
        }
        return newAngle
    }

    /**
     * 对view进行视觉上的处理，如缩放，透明度等
     * @param position             当前itemView在所有itemList中的位置
     * @param view                 要进行变换的view
     * @param moveDistanceToCenter 该距离是当前该view的左侧距离原始位置第0个位置view的左侧之间的距离
     * @param hasScroll            当前已经滑动过的总距离
     */
    protected open fun handleView(
        position: Int,
        view: View,
        moveDistanceToCenter: Int,
        hasScroll: Int
    ) {
        if (isUseFlipDelayEffect) {
            val rotation = computeRotation(view.rotationY, moveDistanceToCenter, hasScroll)
            printLog("handleView, pos:$position, rotation:$rotation")
            view.rotationY = rotation
        }
    }

    //如果横向滑动，你需要对该函数进行覆写，以实现列表左右两侧可滑动的范围，默认dx
    protected open fun scrollXRangeController(dx: Int, hasScrollX: Int): Int {
        return dx
    }

    //如果纵向滑动，你需要对该函数进行覆写，以实现列表上下两侧可滑动的范围， 默认dy
    protected open fun scrollYRangeController(dy: Int, hasScrollY: Int): Int {
        return dy
    }

    interface OnCompleted {
        fun completed(state: RecyclerView.State?)
    }
}