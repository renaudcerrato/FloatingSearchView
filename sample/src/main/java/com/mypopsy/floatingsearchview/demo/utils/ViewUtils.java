package com.mypopsy.floatingsearchview.demo.utils;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.AttrRes;
import android.util.DisplayMetrics;

/**
 * Created by renaud on 01/01/16.
 */
public class ViewUtils {

    static public int dpToPx(int dp){
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        return (int) (dp * metrics.density);
    }

    static public int getThemeAttrColor(Context context, @AttrRes int attr) {
        final int[] TEMP_ARRAY = new int[1];
        TEMP_ARRAY[0] = attr;
        TypedArray a = context.obtainStyledAttributes(null, TEMP_ARRAY);
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }
}
