package com.mypopsy.widget.internal

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.IntDef
import kotlin.math.ceil
import kotlin.math.cos

open class RoundRectDrawableWithShadow(
    backgroundColor: Int, radius: Float,
    shadowSize: Float, maxShadowSize: Float
    ): Drawable() {

    companion object {
        private const val DEBUG = false
        private const val SHADOW_COLOR_START = 0x37000000
        private const val SHADOW_COLOR_END = 0x03000000
        private const val SHADOW_INSET_DP = 1

        const val LEFT = 1
        const val TOP = 1 shl 1
        const val RIGHT = 1 shl 2
        const val BOTTOM = 1 shl 3

        // used to calculate content padding
        val COS_45 = cos(Math.toRadians(45.0))

        const val SHADOW_MULTIPLIER = 1.5f

        fun calculateVerticalPadding(
            maxShadowSize: Float, cornerRadius: Float,
            addPaddingForCorners: Boolean
        ): Float {
            return if (addPaddingForCorners) {
                (maxShadowSize * SHADOW_MULTIPLIER + (1 - COS_45) * cornerRadius).toFloat()
            } else {
                maxShadowSize * SHADOW_MULTIPLIER
            }
        }

        fun calculateHorizontalPadding(
            maxShadowSize: Float, cornerRadius: Float,
            addPaddingForCorners: Boolean
        ): Float {
            return if (addPaddingForCorners) {
                (maxShadowSize + (1 - COS_45) * cornerRadius).toFloat()
            } else {
                maxShadowSize
            }
        }

    }

    private var mInsetShadow = 0 // extra shadow to avoid gaps between card and shadow

    private val mCornerRect = RectF()

    private val mPaint: Paint

    private val mCornerShadowPaint: Paint

    private val mEdgeShadowPaint: Paint

    private var mBoundsPaint: Paint? = null

    private val mCardBounds: RectF

    var cornerRadius = 0f
        set(value) {
            val radius = (value + .5f).toInt().toFloat()
            if (field == radius) {
                return
            }
            field = radius
            mDirty = true
            invalidateSelf()
        }

    private var mCornerShadowPath: Path? = null

    val color: Int
        get() = mPaint.color

    // updated value with inset
    var mMaxShadowSize = 0f

    // actual value set by developer
    var maxShadowSize = 0f
        private set

    // multiplied value to account for shadow offset
    var shadowSize = 0f
        private set

    // actual value set by developer
    var mRawShadowSize = 0f

    var topLeftCorner = false
    var bottomLeftCorner = false
    var topRightCorner = false
    var bottomRightCorner = false

    var leftShadow = false
    var topShadow = false
    var rightShadow = false
    var bottomShadow = false

    private var mDirty = true

    var addPaddingForCorners = true
        set(value) {
            field = value
            invalidateSelf()
        }

    /**
     * If shadow size is set to a value above max shadow, we print a warning
     */
    private var mPrintedShadowClipWarning = false

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        value = [LEFT, RIGHT, TOP, BOTTOM],
        flag = true
    )
    annotation class Gravity

    init {
        if (DEBUG) Log.d(
            javaClass.simpleName,
            "RoundRectDrawableWithShadow($radius,$shadowSize,$maxShadowSize)"
        )
        mInsetShadow = ViewUtils.dpToPx(SHADOW_INSET_DP)
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).also { it.color = backgroundColor }
        mCornerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).also { it.style = Paint.Style.FILL }
        cornerRadius = (radius + .5f).toInt().toFloat()
        mCardBounds = RectF()
        mEdgeShadowPaint = Paint(mCornerShadowPaint).also { it.isAntiAlias = false }
        if (DEBUG) {
            mBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            mBoundsPaint?.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            mPaint.apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            mCornerShadowPaint.strokeWidth = 1f
        }
        setShadow(TOP or LEFT or RIGHT or BOTTOM)
        setShadowSize(shadowSize, maxShadowSize)
    }

    fun setShadow(@Gravity flags: Int) {
        topLeftCorner = flags and (TOP or LEFT) == LEFT or TOP
        bottomLeftCorner = flags and (BOTTOM or LEFT) == LEFT or BOTTOM
        topRightCorner = flags and (TOP or RIGHT) == TOP or RIGHT
        bottomRightCorner = flags and (BOTTOM or RIGHT) == BOTTOM or RIGHT
        leftShadow = flags and LEFT != 0
        topShadow = flags and TOP != 0
        rightShadow = flags and RIGHT != 0
        bottomShadow = flags and BOTTOM != 0
        mDirty = true
        invalidateSelf()
    }

    override fun mutate(): RoundRectDrawableWithShadow {
        return RoundRectDrawableWithShadow(mPaint.color, cornerRadius, shadowSize, maxShadowSize)
    }

    /**
     * Casts the value to an even integer.
     */
    private fun toEven(value: Float): Int {
        val i = (value + .5f).toInt()
        return if (i % 2 == 1) {
            i - 1
        } else i
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
        mCornerShadowPaint.alpha = alpha
        mEdgeShadowPaint.alpha = alpha
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (DEBUG) Log.d(
            javaClass.simpleName,
            "onBoundsChange@" + hashCode() + ": " +
                    bounds + " (" + bounds.width() + "," + bounds.height() + ")"
        )
        mDirty = true
    }

    fun setShadowSize(shadowSize: Float, maxShadowSize: Float) {
        require(!(shadowSize < 0 || maxShadowSize < 0)) { "invalid shadow size" }
        val shadowSize1 = toEven(shadowSize).toFloat()
        val maxShadowSize1 = toEven(maxShadowSize).toFloat()
        if (shadowSize1 > maxShadowSize1) {
            if (!mPrintedShadowClipWarning) {
                mPrintedShadowClipWarning = true
            }
        }
        if (mRawShadowSize == shadowSize && maxShadowSize == maxShadowSize) {
            return
        }
        mRawShadowSize = shadowSize
        this.maxShadowSize = maxShadowSize
        this.shadowSize = (shadowSize * SHADOW_MULTIPLIER + mInsetShadow + .5f).toInt().toFloat()
        mMaxShadowSize = maxShadowSize + mInsetShadow
        mDirty = true
        invalidateSelf()
    }

    override fun getPadding(padding: Rect): Boolean {
        val vOffset = ceil(
            calculateVerticalPadding(
                maxShadowSize, cornerRadius,
                addPaddingForCorners
            ).toDouble()
        ).toInt()
        val hOffset = ceil(
            calculateHorizontalPadding(
                maxShadowSize, cornerRadius,
                addPaddingForCorners
            ).toDouble()
        ).toInt()
        padding[if (leftShadow) hOffset else 0, if (topShadow) vOffset else 0, if (rightShadow) hOffset else 0] =
            if (bottomShadow) vOffset else 0
        return true
    }

    fun calculateVerticalPadding(
        maxShadowSize: Float, cornerRadius: Float,
        addPaddingForCorners: Boolean
    ): Float {
        return if (addPaddingForCorners) {
            (maxShadowSize * SHADOW_MULTIPLIER + (1 - COS_45) * cornerRadius).toFloat()
        } else {
            maxShadowSize * SHADOW_MULTIPLIER
        }
    }

    fun calculateHorizontalPadding(
        maxShadowSize: Float, cornerRadius: Float,
        addPaddingForCorners: Boolean
    ): Float {
        return if (addPaddingForCorners) {
            (maxShadowSize + (1 - COS_45) * cornerRadius).toFloat()
        } else {
            maxShadowSize
        }
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.colorFilter = cf
        mCornerShadowPaint.colorFilter = cf
        mEdgeShadowPaint.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun draw(canvas: Canvas) {
        if (mDirty) {
            buildComponents(bounds)
            mDirty = false
        }
        canvas.translate(0f, mRawShadowSize / 2)
        drawShadow(canvas)
        canvas.translate(0f, -mRawShadowSize / 2)
        drawBody(canvas, mCardBounds, cornerRadius, mPaint)
        if (DEBUG) {
            val bounds = bounds
            canvas.drawRect(bounds, mBoundsPaint!!)
        }
    }

    private fun drawShadow(canvas: Canvas) {
        val edgeShadowTop = -cornerRadius - shadowSize
        val inset: Float = cornerRadius + mInsetShadow + mRawShadowSize / 2
        val drawHorizontalEdges = mCardBounds.width() - 2 * inset > 0
        val drawVerticalEdges = mCardBounds.height() - 2 * inset > 0
        var saved: Int

        // LT
        if (topLeftCorner || topShadow) {
            saved = canvas.save()
            canvas.translate(mCardBounds.left + inset, mCardBounds.top + inset)
            if (topLeftCorner) canvas.drawPath(mCornerShadowPath!!, mCornerShadowPaint)
            if (drawHorizontalEdges && topShadow) {
                canvas.drawRect(
                    if (topLeftCorner) 0F else -inset,
                    edgeShadowTop,
                    mCardBounds.width() - if (topRightCorner) 2 * inset else 0F,
                    -cornerRadius,
                    mEdgeShadowPaint
                )
            }
            canvas.restoreToCount(saved)
        }

        // RB
        if (bottomRightCorner || bottomShadow) {
            saved = canvas.save()
            canvas.translate(mCardBounds.right - inset, mCardBounds.bottom - inset)
            canvas.rotate(180f)
            if (bottomRightCorner) canvas.drawPath(mCornerShadowPath!!, mCornerShadowPaint)
            if (drawHorizontalEdges && bottomShadow) {
                canvas.drawRect(
                    if (bottomLeftCorner) 0F else -inset, edgeShadowTop,
                    mCardBounds.width() - if (bottomRightCorner) 2F * inset else 0F,
                    -cornerRadius + shadowSize,
                    mEdgeShadowPaint
                )
            }
            canvas.restoreToCount(saved)
        }

        // LB
        if (bottomLeftCorner || leftShadow) {
            saved = canvas.save()
            canvas.translate(mCardBounds.left + inset, mCardBounds.bottom - inset)
            canvas.rotate(270f)
            if (bottomLeftCorner) canvas.drawPath(mCornerShadowPath!!, mCornerShadowPaint)
            if (drawVerticalEdges && leftShadow) {
                canvas.drawRect(
                    if (bottomLeftCorner) 0F else -(inset - mInsetShadow),
                    edgeShadowTop,
                    mCardBounds.height() - if (topLeftCorner) 2 * inset else inset - mInsetShadow,
                    -cornerRadius,
                    mEdgeShadowPaint
                )
            }
            canvas.restoreToCount(saved)
        }

        // RT
        if (topRightCorner || rightShadow) {
            saved = canvas.save()
            canvas.translate(mCardBounds.right - inset, mCardBounds.top + inset)
            canvas.rotate(90f)
            if (topRightCorner) canvas.drawPath(mCornerShadowPath!!, mCornerShadowPaint)
            if (drawVerticalEdges && rightShadow) {
                canvas.drawRect(
                    if (topRightCorner) 0F else -(inset + mInsetShadow),
                    edgeShadowTop,
                    mCardBounds.height() - if (bottomRightCorner) 2 * inset else inset + mInsetShadow,
                    -cornerRadius,
                    mEdgeShadowPaint
                )
            }
            canvas.restoreToCount(saved)
        }
    }

    fun drawBody(
        canvas: Canvas, bounds: RectF, cornerRadius: Float,
        paint: Paint
    ) {
        var cornerRadius1 = cornerRadius
        val twoRadius = cornerRadius1 * 2
        val innerWidth = bounds.width() - twoRadius - 1
        val innerHeight = bounds.height() - twoRadius - 1

        // increment it to account for half pixels.
        if (cornerRadius1 >= 1f) {
            cornerRadius1 += .5f
            if (topLeftCorner || topRightCorner || bottomRightCorner || bottomLeftCorner) {
                mCornerRect[-cornerRadius1, -cornerRadius1, cornerRadius1] = cornerRadius1
                val saved = canvas.save()
                canvas.translate(bounds.left + cornerRadius1, bounds.top + cornerRadius1)
                if (topLeftCorner) canvas.drawArc(mCornerRect, 180f, 90f, true, paint)
                canvas.translate(innerWidth, 0f)
                canvas.rotate(90f)
                if (topRightCorner) canvas.drawArc(mCornerRect, 180f, 90f, true, paint)
                canvas.translate(innerHeight, 0f)
                canvas.rotate(90f)
                if (bottomRightCorner) canvas.drawArc(mCornerRect, 180f, 90f, true, paint)
                canvas.translate(innerWidth, 0f)
                canvas.rotate(90f)
                if (bottomLeftCorner) canvas.drawArc(mCornerRect, 180f, 90f, true, paint)
                canvas.restoreToCount(saved)
            }
            //draw top and bottom pieces
            if (topShadow) canvas.drawRect(
                bounds.left + if (topLeftCorner) cornerRadius1 - 1F else 0F,
                bounds.top,
                bounds.right - if (topRightCorner) cornerRadius1 - 1F else 0F,
                bounds.top + cornerRadius1,
                paint
            )
            if (bottomShadow) canvas.drawRect(
                bounds.left + if (bottomLeftCorner) cornerRadius - 1F else 0F,
                bounds.bottom - cornerRadius + 1f,
                bounds.right - if (bottomRightCorner) cornerRadius - 1F else 0F,
                bounds.bottom, paint
            )
        }
        ////                center
        if (DEBUG) Log.d(
            javaClass.simpleName, "drawBody:" + RectF(
                bounds.left,
                bounds.top + if (topShadow) 0f.coerceAtLeast(cornerRadius - 1) else 0F,
                bounds.right,
                bounds.bottom - if (bottomShadow) cornerRadius - 1F else 0F
            )
        )
        canvas.drawRect(
            bounds.left,
            bounds.top + if (topShadow) 0f.coerceAtLeast(cornerRadius - 1) else 0F,
            bounds.right,
            bounds.bottom - if (bottomShadow) cornerRadius - 1F else 0F, paint
        )
    }

    private fun buildShadowCorners() {
        val innerBounds = RectF(-cornerRadius, -cornerRadius, cornerRadius, cornerRadius)
        val outerBounds = RectF(innerBounds)
        outerBounds.inset(-shadowSize, -shadowSize)
         if (mCornerShadowPath == null) {
            mCornerShadowPath = Path()
        } else {
            mCornerShadowPath!!.reset()
        }
        mCornerShadowPath!!.apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(-cornerRadius, 0f)
            rLineTo(-shadowSize, 0f)
            // outer arc
            arcTo(outerBounds, 180f, 90f, false)
            // inner arc
            arcTo(innerBounds, 270f, -90f, false)
            close()
            val startRatio: Float = cornerRadius / (cornerRadius + shadowSize)
            mCornerShadowPaint.shader =
                RadialGradient(0F, 0F,
                    cornerRadius + shadowSize,
                    intArrayOf(SHADOW_COLOR_START, SHADOW_COLOR_START, SHADOW_COLOR_END),
                    floatArrayOf(0f, startRatio, 1f),
                    Shader.TileMode.CLAMP
                )

            // we offset the content shadowSize/2 pixels up to make it more realistic.
            // this is why edge shadow shader has some extra space
            // When drawing bottom edge shadow, we use that extra space.
            mEdgeShadowPaint.shader = LinearGradient(0F,
                -cornerRadius + shadowSize,
                0F,
                -cornerRadius - shadowSize,
                intArrayOf(SHADOW_COLOR_START, SHADOW_COLOR_START, SHADOW_COLOR_END),
                floatArrayOf(0f, .5f, 1f),
                Shader.TileMode.CLAMP
            )

        }
        mEdgeShadowPaint.isAntiAlias = false
    }

    private fun buildComponents(bounds: Rect) {
        // Card is offset SHADOW_MULTIPLIER * maxShadowSize to account for the shadow shift.
        // We could have different top-bottom offsets to avoid extra gap above but in that case
        // center aligning Views inside the CardView would be problematic.
        val verticalOffset = maxShadowSize * SHADOW_MULTIPLIER
        mCardBounds[bounds.left + (if (leftShadow) maxShadowSize else 0F), bounds.top + (if (topShadow) verticalOffset else 0F)
                , bounds.right - (if (rightShadow) maxShadowSize else 0F)] =
            bounds.bottom - if (bottomShadow) verticalOffset else 0F
        buildShadowCorners()
    }

    fun getMaxShadowAndCornerPadding(into: Rect) {
        getPadding(into)
    }

    fun setShadowSize(size: Float) {
        setShadowSize(size, maxShadowSize)
    }

    fun setMaxShadowSize(size: Float) {
        setShadowSize(mRawShadowSize, size)
    }

    fun getMinWidth(): Float {
        val content: Float = 2 *
                maxShadowSize.coerceAtLeast(cornerRadius + mInsetShadow + maxShadowSize / 2)
        return content + (maxShadowSize + mInsetShadow) * 2
    }

    fun getMinHeight(): Float {
        val content: Float = 2 * maxShadowSize.coerceAtLeast(
            cornerRadius + mInsetShadow
                    + maxShadowSize * SHADOW_MULTIPLIER / 2
        )
        return content + (maxShadowSize * SHADOW_MULTIPLIER + mInsetShadow) * 2
    }

    fun setColor(color: Int) {
        mPaint.color = color
        invalidateSelf()
    }
}