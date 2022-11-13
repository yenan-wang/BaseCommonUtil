package com.ngb.wyn.common.utils;

import android.content.Context;

public class DimenUtils {

    public static float getDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    public static float getScaledDensity(Context context) {
        return context.getResources().getDisplayMetrics().scaledDensity;
    }

    public static int dp2px(Context context, float dp) {
        final float scale = getDensity(context);
        return Math.round(dp * scale);
    }

    public static float px2dp(Context context, int px) {
        final float scale = getDensity(context);
        return px / scale;
    }

    public static int sp2px(Context context, float sp) {
        final float scale = getScaledDensity(context);
        return Math.round(sp * scale);
    }

    public static int px2sp(Context context, float px) {
        final float scale = getScaledDensity(context);
        return Math.round(px / scale);
    }
}
