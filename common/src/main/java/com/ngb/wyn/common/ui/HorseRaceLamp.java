package com.ngb.wyn.common.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import com.ngb.wyn.common.R;
import com.ngb.wyn.common.utils.LogUtil;

public class HorseRaceLamp extends View {

    public static final int DEFAULT_SPEED = 1000; //默认每千秒1000字
    public static final int DEFAULT_TEXT_SIZE = 50;
    public static final int DEFAULT_MIN_WIDTH = DEFAULT_TEXT_SIZE;
    public static final int DEFAULT_PADDING = 3;

    private Paint mPaint;
    private Scroller mScroller;
    private int mSpeed;
    private int mTextColor;
    private int mTextAlign;
    private int mTextSize;
    private int mTextPaddingTop;
    private int mTextPaddingBottom;
    private boolean mScrollDirect;
    private boolean mScrollRepeat;
    private boolean mIsAutoPlay;
    private String mTextContent;
    private boolean mIsFirstScroll = true;
    private boolean mIsPause = true;

    public HorseRaceLamp(Context context) {
        this(context, null);
    }

    public HorseRaceLamp(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorseRaceLamp(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HorseRaceLamp(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.HorseRaceLamp);
        mSpeed = array.getInt(R.styleable.HorseRaceLamp_HRScrollSpeed, DEFAULT_SPEED);
        mTextColor = array.getColor(R.styleable.HorseRaceLamp_HRTextColor, Color.BLACK);
        mTextAlign = array.getInt(R.styleable.HorseRaceLamp_HRTextAlign, 0);
        mTextSize = array.getDimensionPixelSize(R.styleable.HorseRaceLamp_HRTextSize, DEFAULT_TEXT_SIZE);
        mTextPaddingTop = array.getDimensionPixelSize(R.styleable.HorseRaceLamp_HRTextPaddingTop, DEFAULT_PADDING);
        mTextPaddingBottom = array.getDimensionPixelSize(R.styleable.HorseRaceLamp_HRTextPaddingBottom, DEFAULT_PADDING);
        mScrollDirect = array.getBoolean(R.styleable.HorseRaceLamp_HRScrollLeft, true);
        mScrollRepeat = array.getBoolean(R.styleable.HorseRaceLamp_HRScrollRepeat, true);
        mIsAutoPlay = array.getBoolean(R.styleable.HorseRaceLamp_HRScrollAutoPlay, true);
        String textContent = array.getString(R.styleable.HorseRaceLamp_HRTextContent);
        if (textContent == null) {
            mTextContent = "";
        } else {
            mTextContent = textContent;
        }
        array.recycle();
        init();
    }

    public int getSpeed() {
        return mSpeed;
    }

    public void setSpeed(int speed) {
        mSpeed = speed;
    }

    public int getTextColor() {
        return mTextColor;
    }

    public void setTextColor(int textColor) {
        mTextColor = textColor;
    }

    public int getTextAlign() {
        return mTextAlign;
    }

    public void setTextAlign(int textAlign) {
        mTextAlign = textAlign;
    }

    public int getTextSize() {
        return mTextSize;
    }

    public void setTextSize(int textSize) {
        mTextSize = textSize;
    }

    public boolean isScrollDirect() {
        return mScrollDirect;
    }

    public void setScrollDirect(boolean scrollDirect) {
        mScrollDirect = scrollDirect;
    }

    public boolean isScrollRepeat() {
        return mScrollRepeat;
    }

    public void setScrollRepeat(boolean scrollRepeat) {
        mScrollRepeat = scrollRepeat;
    }

    public String getTextContent() {
        return mTextContent;
    }

    public void setTextContent(String textContent) {
        if (textContent == null) {
            textContent = "";
        }
        mTextContent = textContent;
    }

    public boolean isAutoPlay() {
        return mIsAutoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        mIsAutoPlay = autoPlay;
    }

    public int getTextPaddingTop() {
        return mTextPaddingTop;
    }

    public void setTextPaddingTop(int textPaddingTop) {
        mTextPaddingTop = textPaddingTop;
    }

    public int getTextPaddingBottom() {
        return mTextPaddingBottom;
    }

    public void setTextPaddingBottom(int textPaddingBottom) {
        mTextPaddingBottom = textPaddingBottom;
    }

    public void startScroll() {
        startScroll(mScrollDirect, mSpeed, !mIsFirstScroll);
    }

    public void stopScroll() {
        mScroller.abortAnimation();
        mIsPause = true;
    }

    public void initScroll() {
        stopScroll();
        scrollTo(-getScrollX(), -getScrollY());
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
        } else {
            if (mScrollRepeat) {
                LogUtil.d("scrollX:" + getScrollX());
                scrollTo(-getScrollX(), -getScrollY());
                startScroll();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode == MeasureSpec.AT_MOST) {
            Rect rect = getTextContentLength(null);
            width = rect.width();
            int parentWidth = ((ViewGroup) getParent()).getMeasuredWidth();
            if (width > parentWidth) {
                width = parentWidth;
            } else if (width <= 0) {
                width = DEFAULT_MIN_WIDTH;
            }
        }
        if (heightMode == MeasureSpec.AT_MOST) {
            height = mTextSize + mTextPaddingTop + mTextPaddingBottom;
        }
        //height = height + mTextPaddingTop + mTextPaddingBottom;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint.FontMetrics fontMetrics = mPaint.getFontMetrics();
        float top = fontMetrics.top;
        float bottom = fontMetrics.bottom;
        int baseLineY = (int) (getMeasuredHeight() / 2 - (top + bottom) / 2);
        baseLineY = baseLineY + (mTextPaddingTop - mTextPaddingBottom) / 3;
        canvas.drawText(mTextContent, 0, baseLineY, mPaint);
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mTextColor);
        mPaint.setTextSize(mTextSize);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(convertTextAlign());
        mScroller = new Scroller(getContext(), new LinearInterpolator());
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (mIsAutoPlay) {
                startScroll();
            }
        });
    }

    private void startScroll(boolean isScrollLeft, int speed, boolean isAddSelfWidth) {
        if (mIsFirstScroll) {
            mIsFirstScroll = false;
        }
        if (speed <= 0) {
            speed = 2500;
        }
        mSpeed = speed;
        Rect rect = getTextContentLength(null);
        int distance = rect.width();
        /*if (isAddSelfWidth) {
            distance += getMeasuredWidth();
        }*/
        int duration = (int) (mTextContent.length() * (1.0F / (mSpeed / 1000.0F))) * 1000;
        if (!isScrollLeft) {
            distance = -distance;
        }
        LogUtil.d("distance:" + distance + ", duration: " + duration);
        scroll(distance, duration);
    }

    private void scroll(int distance, int duration) {
        mIsPause = false;
        mScroller.startScroll(getScrollX(), getScrollY(), distance, 0, duration);
        invalidate();
    }

    private Rect getTextContentLength(Rect rect) {
        if (rect == null) {
            rect = new Rect();
        }
        mPaint.getTextBounds(mTextContent, 0, mTextContent.length(), rect);
        return rect;
    }

    private Paint.Align convertTextAlign() {
        switch (mTextAlign) {
            case 0:
                return Paint.Align.LEFT;
            case 1:
                return Paint.Align.CENTER;
            default:
                return Paint.Align.RIGHT;
        }
    }
}
