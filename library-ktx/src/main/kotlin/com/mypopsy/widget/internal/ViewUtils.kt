package com.mypopsy.widget.internal

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

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat

object ViewUtils {
    private val TEMP_ARRAY = IntArray(1)

    fun showSoftKeyboardDelayed(editText: EditText, delay: Long) {
        editText.postDelayed({
            val inputMethodManager = editText.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(
                editText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }, delay)
    }

    fun closeSoftKeyboard(activity: Activity) {
        val currentFocusView = activity.currentFocus
        currentFocusView?.let {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    fun dpToPx(dp: Int): Int {
        val metrics = Resources.getSystem().displayMetrics
        return (dp * metrics.density).toInt()
    }

    fun pxToDp(px: Int): Int {
        val metrics = Resources.getSystem().displayMetrics
        return (px / metrics.density).toInt()
    }

    fun getThemeAttrColor(context: Context, @AttrRes attr: Int): Int {
        TEMP_ARRAY[0] = attr
        val a = context.obtainStyledAttributes(null, TEMP_ARRAY)
        return try {
            a.getColor(0, 0)
        } finally {
            a.recycle()
        }
    }

    fun getTinted(icon: Drawable, @ColorInt color: Int): Drawable {
        val icon1 = DrawableCompat.wrap(icon)
        DrawableCompat.setTint(icon1, color)
        return icon1
    }
}