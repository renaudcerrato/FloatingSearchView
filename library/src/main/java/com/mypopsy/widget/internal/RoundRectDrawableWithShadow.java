/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mypopsy.widget.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import android.util.Log;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * A rounded rectangle drawable which also includes a configurable shadow around.
 */
public class RoundRectDrawableWithShadow extends Drawable {

    private static final boolean DEBUG = false;

    private static final int SHADOW_COLOR_START = 0x37000000;
    private static final int SHADOW_COLOR_END = 0x03000000;
    private static final int SHADOW_INSET_DP = 1;

    @Retention(SOURCE)
    @IntDef(value = {LEFT, RIGHT, TOP, BOTTOM}, flag = true)
    public @interface Gravity {}

    public static final int LEFT = 1;
    public static final int TOP = 1 << 1;
    public static final int RIGHT = 1 << 2;
    public static final int BOTTOM = 1 << 3;

    // used to calculate content padding
    final static double COS_45 = Math.cos(Math.toRadians(45));

    final static float SHADOW_MULTIPLIER = 1.5f;

    final int mInsetShadow; // extra shadow to avoid gaps between card and shadow

    final RectF mCornerRect = new RectF();

    Paint mPaint;

    Paint mCornerShadowPaint;

    Paint mEdgeShadowPaint;

    Paint mBoundsPaint;

    final RectF mCardBounds;

    float mCornerRadius;

    Path mCornerShadowPath;

    // updated value with inset
    float mMaxShadowSize;

    // actual value set by developer
    float mRawMaxShadowSize;

    // multiplied value to account for shadow offset
    float mShadowSize;

    // actual value set by developer
    float mRawShadowSize;

    boolean topLeftCorner, bottomLeftCorner, topRightCorner, bottomRightCorner;
    boolean leftShadow, topShadow, rightShadow, bottomShadow;

    private boolean mDirty = true;

    private boolean mAddPaddingForCorners = true;

    /**
     * If shadow size is set to a value above max shadow, we print a warning
     */
    private boolean mPrintedShadowClipWarning = false;

    public RoundRectDrawableWithShadow(int backgroundColor, float radius,
                                       float shadowSize, float maxShadowSize) {
        if(DEBUG) Log.d(getClass().getSimpleName(), "RoundRectDrawableWithShadow("+radius+","+shadowSize+","+maxShadowSize+")");

        mInsetShadow = ViewUtils.dpToPx(SHADOW_INSET_DP);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setColor(backgroundColor);
        mCornerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mCornerShadowPaint.setStyle(Paint.Style.FILL);
        mCornerRadius = (int) (radius + .5f);
        mCardBounds = new RectF();
        mEdgeShadowPaint = new Paint(mCornerShadowPaint);
        mEdgeShadowPaint.setAntiAlias(false);
        if(DEBUG) {
            mBoundsPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            mBoundsPaint.setColor(Color.BLACK);
            mBoundsPaint.setStyle(Paint.Style.STROKE);
            mBoundsPaint.setStrokeWidth(1);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1);
            mCornerShadowPaint.setStrokeWidth(1);
        }
        setShadow(TOP|LEFT|RIGHT|BOTTOM);
        setShadowSize(shadowSize, maxShadowSize);
    }

    public int getColor() {
        return mPaint.getColor();
    }

    public void setShadow(@Gravity int flags) {
        topLeftCorner = (flags & (TOP|LEFT)) == (LEFT|TOP);
        bottomLeftCorner = (flags & (BOTTOM|LEFT)) == (LEFT|BOTTOM);
        topRightCorner = (flags & (TOP|RIGHT)) == (TOP|RIGHT);
        bottomRightCorner = (flags & (BOTTOM|RIGHT)) == (BOTTOM|RIGHT);

        leftShadow = (flags & LEFT) != 0;
        topShadow = (flags & TOP) != 0;
        rightShadow = (flags & RIGHT) != 0;
        bottomShadow = (flags & BOTTOM) != 0;
        mDirty = true;
        invalidateSelf();
    }

    @Override
    public RoundRectDrawableWithShadow mutate() {
        return new RoundRectDrawableWithShadow(
                mPaint.getColor(), getCornerRadius(),
                getShadowSize(), getMaxShadowSize());
    }

    /**
     * Casts the value to an even integer.
     */
    private int toEven(float value) {
        int i = (int) (value + .5f);
        if (i % 2 == 1) {
            return i - 1;
        }
        return i;
    }

    public void setAddPaddingForCorners(boolean addPaddingForCorners) {
        mAddPaddingForCorners = addPaddingForCorners;
        invalidateSelf();
    }

    public boolean getAddPaddingForCorners() {
        return mAddPaddingForCorners;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        mCornerShadowPaint.setAlpha(alpha);
        mEdgeShadowPaint.setAlpha(alpha);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if(DEBUG) Log.d(getClass().getSimpleName(), "onBoundsChange@"+hashCode()+": "+
                bounds+" ("+bounds.width()+","+bounds.height()+")");
        mDirty = true;
    }

    public void setShadowSize(float shadowSize, float maxShadowSize) {
        if (shadowSize < 0 || maxShadowSize < 0) {
            throw new IllegalArgumentException("invalid shadow size");
        }
        shadowSize = toEven(shadowSize);
        maxShadowSize = toEven(maxShadowSize);
        if (shadowSize > maxShadowSize) {
            shadowSize = maxShadowSize;
            if (!mPrintedShadowClipWarning) {
                mPrintedShadowClipWarning = true;
            }
        }
        if (mRawShadowSize == shadowSize && mRawMaxShadowSize == maxShadowSize) {
            return;
        }
        mRawShadowSize = shadowSize;
        mRawMaxShadowSize = maxShadowSize;
        mShadowSize = (int)(shadowSize * SHADOW_MULTIPLIER + mInsetShadow + .5f);
        mMaxShadowSize = maxShadowSize + mInsetShadow;
        mDirty = true;
        invalidateSelf();
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        int vOffset = (int) Math.ceil(calculateVerticalPadding(mRawMaxShadowSize, mCornerRadius,
                mAddPaddingForCorners));
        int hOffset = (int) Math.ceil(calculateHorizontalPadding(mRawMaxShadowSize, mCornerRadius,
                mAddPaddingForCorners));
        padding.set(
                leftShadow ? hOffset : 0,
                topShadow ? vOffset : 0,
                rightShadow ? hOffset : 0,
                bottomShadow ? vOffset : 0);
        return true;
    }

    public static float calculateVerticalPadding(float maxShadowSize, float cornerRadius,
            boolean addPaddingForCorners) {
        if (addPaddingForCorners) {
            return (float) (maxShadowSize * SHADOW_MULTIPLIER + (1 - COS_45) * cornerRadius);
        } else {
            return maxShadowSize * SHADOW_MULTIPLIER;
        }
    }

    public static float calculateHorizontalPadding(float maxShadowSize, float cornerRadius,
            boolean addPaddingForCorners) {
        if (addPaddingForCorners) {
            return (float) (maxShadowSize + (1 - COS_45) * cornerRadius);
        } else {
            return maxShadowSize;
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        mCornerShadowPaint.setColorFilter(cf);
        mEdgeShadowPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setCornerRadius(float radius) {
        radius = (int) (radius + .5f);
        if (mCornerRadius == radius) {
            return;
        }
        mCornerRadius = radius;
        mDirty = true;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mDirty) {
            buildComponents(getBounds());
            mDirty = false;
        }

        canvas.translate(0, mRawShadowSize / 2);
        drawShadow(canvas);
        canvas.translate(0, -mRawShadowSize / 2);
        drawBody(canvas, mCardBounds, mCornerRadius, mPaint);

        if(DEBUG) {
            Rect bounds = getBounds();
            canvas.drawRect(bounds, mBoundsPaint);
        }
    }

    private void drawShadow(Canvas canvas) {
        final float edgeShadowTop = -mCornerRadius - mShadowSize;
        final float inset = mCornerRadius + mInsetShadow + mRawShadowSize / 2;
        final boolean drawHorizontalEdges = mCardBounds.width() - 2 * inset > 0;
        final boolean drawVerticalEdges = mCardBounds.height() - 2 * inset > 0;
        int saved;

        // LT
        if(topLeftCorner || topShadow) {
            saved = canvas.save();
            canvas.translate(mCardBounds.left + inset, mCardBounds.top + inset);
            if (topLeftCorner) canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
            if (drawHorizontalEdges && topShadow) {
                canvas.drawRect(topLeftCorner ? 0 : -inset,
                        edgeShadowTop,
                        mCardBounds.width() - (topRightCorner ?  2 * inset : 0),
                        -mCornerRadius,
                        mEdgeShadowPaint);
            }
            canvas.restoreToCount(saved);
        }

        // RB
        if(bottomRightCorner || bottomShadow) {
            saved = canvas.save();
            canvas.translate(mCardBounds.right - inset, mCardBounds.bottom - inset);
            canvas.rotate(180f);
            if (bottomRightCorner) canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
            if (drawHorizontalEdges && bottomShadow) {
                canvas.drawRect(bottomLeftCorner ? 0 : -inset, edgeShadowTop,
                        mCardBounds.width() - (bottomRightCorner ? 2*inset : 0),
                        -mCornerRadius + mShadowSize,
                        mEdgeShadowPaint);
            }
            canvas.restoreToCount(saved);
        }

        // LB
        if(bottomLeftCorner || leftShadow) {
            saved = canvas.save();
            canvas.translate(mCardBounds.left + inset, mCardBounds.bottom - inset);
            canvas.rotate(270f);
            if (bottomLeftCorner) canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
            if (drawVerticalEdges && leftShadow) {
                canvas.drawRect(bottomLeftCorner ? 0 : -(inset-mInsetShadow),
                        edgeShadowTop,
                        mCardBounds.height() - (topLeftCorner ? 2*inset : inset-mInsetShadow),
                        -mCornerRadius,
                        mEdgeShadowPaint);
            }
            canvas.restoreToCount(saved);
        }

        // RT
        if(topRightCorner || rightShadow) {
            saved = canvas.save();
            canvas.translate(mCardBounds.right - inset, mCardBounds.top + inset);
            canvas.rotate(90f);
            if (topRightCorner) canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
            if (drawVerticalEdges && rightShadow) {
                canvas.drawRect(topRightCorner ? 0 : -(inset+mInsetShadow),
                        edgeShadowTop,
                        mCardBounds.height() - (bottomRightCorner ? 2*inset : inset+mInsetShadow),
                        -mCornerRadius,
                        mEdgeShadowPaint);
            }
            canvas.restoreToCount(saved);
        }
    }

    protected void drawBody(Canvas canvas, RectF bounds, float cornerRadius,
                            Paint paint) {
        final float twoRadius = cornerRadius * 2;
        final float innerWidth = bounds.width() - twoRadius - 1;
        final float innerHeight = bounds.height() - twoRadius - 1;

        // increment it to account for half pixels.
        if (cornerRadius >= 1f) {
            cornerRadius += .5f;
            if(topLeftCorner || topRightCorner || bottomRightCorner || bottomLeftCorner) {
                mCornerRect.set(-cornerRadius, -cornerRadius, cornerRadius, cornerRadius);
                int saved = canvas.save();
                canvas.translate(bounds.left + cornerRadius, bounds.top + cornerRadius);
                if (topLeftCorner) canvas.drawArc(mCornerRect, 180, 90, true, paint);
                canvas.translate(innerWidth, 0);
                canvas.rotate(90);
                if (topRightCorner) canvas.drawArc(mCornerRect, 180, 90, true, paint);
                canvas.translate(innerHeight, 0);
                canvas.rotate(90);
                if (bottomRightCorner) canvas.drawArc(mCornerRect, 180, 90, true, paint);
                canvas.translate(innerWidth, 0);
                canvas.rotate(90);
                if (bottomLeftCorner) canvas.drawArc(mCornerRect, 180, 90, true, paint);
                canvas.restoreToCount(saved);
            }
            //draw top and bottom pieces
            if(topShadow)
                canvas.drawRect(
                        bounds.left + (topLeftCorner ? cornerRadius - 1 : 0),
                        bounds.top,
                        bounds.right - (topRightCorner ? cornerRadius - 1 : 0),
                        bounds.top + cornerRadius,
                        paint);
            if(bottomShadow)
                canvas.drawRect(
                        bounds.left + (bottomLeftCorner ? cornerRadius - 1 : 0),
                        bounds.bottom - cornerRadius + 1f,
                        bounds.right - (bottomRightCorner ? cornerRadius - 1: 0),
                        bounds.bottom, paint);
        }
////                center
        if(DEBUG) Log.d(getClass().getSimpleName(), "drawBody:" + new RectF(bounds.left,
                bounds.top + (topShadow ? Math.max(0, cornerRadius - 1) : 0),
                bounds.right,
                bounds.bottom - (bottomShadow ? cornerRadius - 1 : 0)));

        canvas.drawRect(bounds.left,
                bounds.top + (topShadow ? Math.max(0, cornerRadius - 1) : 0),
                bounds.right,
                bounds.bottom - (bottomShadow ? cornerRadius - 1 : 0)
                , paint);
    }

    private void buildShadowCorners() {
        RectF innerBounds = new RectF(-mCornerRadius, -mCornerRadius, mCornerRadius, mCornerRadius);
        RectF outerBounds = new RectF(innerBounds);
        outerBounds.inset(-mShadowSize, -mShadowSize);

        if (mCornerShadowPath == null) {
            mCornerShadowPath = new Path();
        } else {
            mCornerShadowPath.reset();
        }
        mCornerShadowPath.setFillType(Path.FillType.EVEN_ODD);
        mCornerShadowPath.moveTo(-mCornerRadius, 0);
        mCornerShadowPath.rLineTo(-mShadowSize, 0);
        // outer arc
        mCornerShadowPath.arcTo(outerBounds, 180f, 90f, false);
        // inner arc
        mCornerShadowPath.arcTo(innerBounds, 270f, -90f, false);
        mCornerShadowPath.close();
        float startRatio = mCornerRadius / (mCornerRadius + mShadowSize);
        mCornerShadowPaint.setShader(new RadialGradient(0, 0, mCornerRadius + mShadowSize,
                new int[]{SHADOW_COLOR_START, SHADOW_COLOR_START, SHADOW_COLOR_END},
                new float[]{0f, startRatio, 1f}
                , Shader.TileMode.CLAMP));

        // we offset the content shadowSize/2 pixels up to make it more realistic.
        // this is why edge shadow shader has some extra space
        // When drawing bottom edge shadow, we use that extra space.
        mEdgeShadowPaint.setShader(new LinearGradient(0, -mCornerRadius + mShadowSize, 0,
                -mCornerRadius - mShadowSize,
                new int[]{SHADOW_COLOR_START, SHADOW_COLOR_START, SHADOW_COLOR_END},
                new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));
        mEdgeShadowPaint.setAntiAlias(false);
    }

    private void buildComponents(Rect bounds) {
        // Card is offset SHADOW_MULTIPLIER * maxShadowSize to account for the shadow shift.
        // We could have different top-bottom offsets to avoid extra gap above but in that case
        // center aligning Views inside the CardView would be problematic.
        final float verticalOffset = mRawMaxShadowSize * SHADOW_MULTIPLIER;
        mCardBounds.set(
                bounds.left + (leftShadow ? mRawMaxShadowSize : 0),
                bounds.top + (topShadow ? verticalOffset : 0),
                bounds.right - (rightShadow ? mRawMaxShadowSize : 0),
                bounds.bottom - (bottomShadow ? verticalOffset : 0));
        buildShadowCorners();
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public void getMaxShadowAndCornerPadding(Rect into) {
        getPadding(into);
    }

    public void setShadowSize(float size) {
        setShadowSize(size, mRawMaxShadowSize);
    }

    public void setMaxShadowSize(float size) {
        setShadowSize(mRawShadowSize, size);
    }

    public float getShadowSize() {
        return mRawShadowSize;
    }

    public float getMaxShadowSize() {
        return mRawMaxShadowSize;
    }

    public float getMinWidth() {
        final float content = 2 *
                Math.max(mRawMaxShadowSize, mCornerRadius + mInsetShadow + mRawMaxShadowSize / 2);
        return content + (mRawMaxShadowSize + mInsetShadow) * 2;
    }

    public float getMinHeight() {
        final float content = 2 * Math.max(mRawMaxShadowSize, mCornerRadius + mInsetShadow
                        + mRawMaxShadowSize * SHADOW_MULTIPLIER / 2);
        return content + (mRawMaxShadowSize * SHADOW_MULTIPLIER + mInsetShadow) * 2;
    }

    public void setColor(int color) {
        mPaint.setColor(color);
        invalidateSelf();
    }
}
