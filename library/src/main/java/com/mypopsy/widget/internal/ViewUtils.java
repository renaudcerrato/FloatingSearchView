package com.mypopsy.widget.internal;

/*
 * Copyright (C) 2015 Arlib
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.core.graphics.drawable.DrawableCompat;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class ViewUtils {
    private static final int[] TEMP_ARRAY = new int[1];

    public static void showSoftKeyboardDelayed(final EditText editText, long delay){
        editText.postDelayed(() -> {
            InputMethodManager inputMethodManager = (InputMethodManager) editText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }, delay);
    }

    public static void closeSoftKeyboard(Activity activity){
        View currentFocusView = activity.getCurrentFocus();
        if (currentFocusView != null) {
            InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
        }
    }

    public static int dpToPx(int dp){
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        return (int) (dp * metrics.density);
    }

    public static int pxToDp(int px){
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        return (int) (px /metrics.density);
    }

    public static int getThemeAttrColor(Context context, @AttrRes int attr) {
        TEMP_ARRAY[0] = attr;
        TypedArray a = context.obtainStyledAttributes(null, TEMP_ARRAY);
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    public static Drawable getTinted(Drawable icon, @ColorInt int color){
        if(icon == null) return null;
        icon = DrawableCompat.wrap(icon);
        DrawableCompat.setTint(icon, color);
        return icon;
    }
}
