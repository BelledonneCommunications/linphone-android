/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.*
import android.util.AttributeSet
import android.view.View
import org.linphone.R
import org.linphone.utils.AppUtils

class VoiceRecordProgressBar(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val MAX_LEVEL = 10000
    var mMinWidth = 0
    var mMaxWidth = 0
    var mMinHeight = 0
    var mMaxHeight = 0
    private var mProgress = 0
    private var mSecondaryProgress = 0
    private var mMax = 0
    private var mProgressDrawable: Drawable? = null
    private var mCurrentDrawable: Drawable? = null
    private val dp40 = AppUtils.dpToPixels(context, 40f)
    private val dp40i = dp40.toInt()
    private var dp40Ratio = 0f
    private val dp50 = AppUtils.dpToPixels(context, 50f)
    private val dp50i = dp50.toInt()

    init {
        initProgressBar()

        var drawable = context.getDrawable(R.drawable.chat_message_audio_record_progress)
        if (drawable != null) {
            drawable = createTiles(drawable, false)
            // Calling this method can set mMaxHeight, make sure the corresponding
            // XML attribute for mMaxHeight is read after calling this method
            setProgressDrawable(drawable!!)
        }

        setMax(100)
        setProgress(0)
        setSecondaryProgress(0)
    }

    /*override fun onSaveInstanceState(): Parcelable? {
        // Force our ancestor class to save its state
        val superState: Parcelable? = super.onSaveInstanceState()
        val ss = ProgressBar.SavedState(superState)
        ss.progress = mProgress
        ss.secondaryProgress = mSecondaryProgress
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as ProgressBar.SavedState
        super.onRestoreInstanceState(ss.superState)
        setProgress(ss.progress)
        setSecondaryProgress(ss.secondaryProgress)
    }*/

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateDrawableState()
    }

    override fun invalidateDrawable(dr: Drawable) {
        if (verifyDrawable(dr)) {
            val dirty: Rect = dr.bounds
            val scrollX: Int = scrollX + paddingLeft
            val scrollY: Int = scrollY + paddingTop
            invalidate(
                dirty.left + scrollX, dirty.top + scrollY,
                dirty.right + scrollX, dirty.bottom + scrollY
            )
        } else {
            super.invalidateDrawable(dr)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d: Drawable? = mCurrentDrawable
        var dw = 0
        var dh = 0
        if (d != null) {
            dw = mMinWidth.coerceAtLeast(mMaxWidth.coerceAtMost(d.intrinsicWidth))
            dh = mMinHeight.coerceAtLeast(mMaxHeight.coerceAtMost(d.intrinsicHeight))
        }
        updateDrawableState()
        dw += paddingRight + paddingLeft
        dh += paddingBottom + paddingTop
        setMeasuredDimension(
            resolveSizeAndState(dw, widthMeasureSpec, 0),
            resolveSizeAndState(dh, heightMeasureSpec, 0)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateDrawableBounds(w, h)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who === mProgressDrawable || super.verifyDrawable(who)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        if (mProgressDrawable != null) mProgressDrawable!!.jumpToCurrentState()
    }

    private fun initProgressBar() {
        mMax = 100
        mProgress = 0
        mSecondaryProgress = 0
        mMinWidth = 24
        mMaxWidth = 48
        mMinHeight = 24
        mMaxHeight = 48
    }

    fun setProgress(p: Int) {
        var progress = p
        if (progress < 0) {
            progress = 0
        }
        if (progress > mMax) {
            progress = mMax
        }
        if (progress != mProgress) {
            mProgress = progress
            refreshProgress(android.R.id.progress, mProgress)
        }
    }

    fun setSecondaryProgress(sp: Int) {
        var secondaryProgress = sp
        if (secondaryProgress < 0) {
            secondaryProgress = 0
        }
        if (secondaryProgress > mMax) {
            secondaryProgress = mMax
        }
        if (secondaryProgress != mSecondaryProgress) {
            mSecondaryProgress = secondaryProgress
            refreshProgress(android.R.id.secondaryProgress, mSecondaryProgress)
        }
    }

    fun setMax(m: Int) {
        var max = m
        if (max < 0) {
            max = 0
        }
        if (max != mMax) {
            mMax = max
            postInvalidate()
            if (mProgress > max) {
                mProgress = max
            }
            refreshProgress(android.R.id.progress, mProgress)
        }
    }

    fun setProgressDrawable(d: Drawable) {
        val needUpdate: Boolean = if (mProgressDrawable != null && d !== mProgressDrawable) {
            mProgressDrawable?.callback = null
            true
        } else {
            false
        }

        d.callback = this
        // Make sure the ProgressBar is always tall enough
        val drawableHeight = d.minimumHeight
        if (mMaxHeight < drawableHeight) {
            mMaxHeight = drawableHeight
            requestLayout()
        }

        mProgressDrawable = d
        mCurrentDrawable = d
        postInvalidate()
        if (needUpdate) {
            updateDrawableBounds(width, height)
            updateDrawableState()

            refreshProgress(android.R.id.progress, mProgress)
            refreshProgress(android.R.id.secondaryProgress, mSecondaryProgress)
        }
    }

    private fun refreshProgress(id: Int, progress: Int) {
        var scale: Float = if (mMax > 0) (progress.toFloat() / mMax) else 0f
        if (id == android.R.id.progress && scale > 0) {
            if (width > 0) {
                // Wait for secondaryProgress to have reached 40dp to start primaryProgress at 0
                if (dp40Ratio == 0f) {
                    dp40Ratio = (dp40 / width)
                }
                if (scale < dp40Ratio) return
                scale -= dp40Ratio
            }
        }

        val d: Drawable? = mCurrentDrawable
        if (d != null) {
            var progressDrawable: Drawable? = null
            if (d is LayerDrawable) {
                progressDrawable = d.findDrawableByLayerId(id)
            }
            val level = (scale * MAX_LEVEL).toInt()
            (progressDrawable ?: d).level = level
        } else {
            invalidate()
        }
    }

    private fun updateDrawableState() {
        val state = drawableState
        if (mProgressDrawable != null && mProgressDrawable?.isStateful == true) {
            mProgressDrawable?.state = state
        }
    }

    private fun updateDrawableBounds(w: Int, h: Int) {
        // onDraw will translate the canvas so we draw starting at 0,0
        var right: Int = w - paddingRight - paddingLeft
        var bottom: Int = h - paddingBottom - paddingTop
        if (mProgressDrawable != null) {
            mProgressDrawable?.setBounds(0, 0, right, bottom)
        }
    }

    private fun createTiles(drawable: Drawable, clip: Boolean = false): Drawable {
        if (drawable is LayerDrawable) {
            val layersCount = drawable.numberOfLayers
            val outDrawables = arrayOfNulls<Drawable>(layersCount)
            for (i in 0 until layersCount) {
                val id = drawable.getId(i)
                outDrawables[i] = createTiles(
                    drawable.getDrawable(i),
                    id == android.R.id.progress
                )
            }
            val newBg = LayerDrawable(outDrawables)
            for (i in 0 until layersCount) {
                newBg.setId(i, drawable.getId(i))
            }
            return newBg
        } /*else if (drawable is BitmapDrawable) {
            /*val tileBitmap = drawable.bitmap
            val shapeDrawable = ShapeDrawable(getDrawableShape())
            val bitmapShader = BitmapShader(
                tileBitmap,
                Shader.TileMode.REPEAT, Shader.TileMode.CLAMP
            )
            shapeDrawable.paint.shader = bitmapShader
            shapeDrawable.setTint(resources.getColor(R.color.white_color))
            */
            return if (clip) ClipDrawable(
                drawable, Gravity.LEFT,
                ClipDrawable.HORIZONTAL
            ) else drawable
        }*/
        return drawable
    }

    /*fun getDrawableShape(): Shape {
        val roundedCorners = floatArrayOf(5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f)
        return RoundRectShape(roundedCorners, null, null)
    }*/

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = mCurrentDrawable as? LayerDrawable

        if (d != null) {
            canvas.save()
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

            for (i in 0 until d.numberOfLayers) {
                val drawable = d.getDrawable(i)
                if (i != 1) {
                    drawable.setBounds(dp40i, 0, width - dp50i, height)
                }
                drawable.draw(canvas)
            }

            canvas.restore()
        }
    }
}
