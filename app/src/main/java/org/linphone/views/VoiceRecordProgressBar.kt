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
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import org.linphone.R

class VoiceRecordProgressBar : View {
    companion object {
        const val MAX_LEVEL = 10000
    }

    private var minWidth = 0
    private var maxWidth = 0
    private var minHeight = 0
    private var maxHeight = 0

    private var progress = 0
    private var secondaryProgress = 0
    private var max = 0
    private var progressDrawable: Drawable? = null

    private var primaryLeftMargin: Float = 0f
    private var primaryRightMargin: Float = 0f

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        max = 100
        progress = 0
        secondaryProgress = 0
        minWidth = 24
        maxWidth = 48
        minHeight = 24
        maxHeight = 48

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.VoiceRecordProgressBar,
            0, 0
        ).apply {

            try {
                val drawable = getDrawable(R.styleable.VoiceRecordProgressBar_progressDrawable)
                if (drawable != null) {
                    setProgressDrawable(drawable)
                }
                setPrimaryLeftMargin(getDimension(R.styleable.VoiceRecordProgressBar_primaryLeftMargin, 0f))
                setPrimaryRightMargin(getDimension(R.styleable.VoiceRecordProgressBar_primaryRightMargin, 0f))
                setMax(getInteger(R.styleable.VoiceRecordProgressBar_max, 100))
            } finally {
                recycle()
            }
        }

        setProgress(0)
        setSecondaryProgress(0)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState: Parcelable? = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.max = max
        savedState.progress = progress
        savedState.secondaryProgress = secondaryProgress
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        setMax(savedState.max)
        setProgress(savedState.progress)
        setSecondaryProgress(savedState.secondaryProgress)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateDrawableState()
    }

    override fun invalidateDrawable(drawable: Drawable) {
        if (verifyDrawable(drawable)) {
            val dirty: Rect = drawable.bounds
            val scrollX: Int = scrollX + paddingLeft
            val scrollY: Int = scrollY + paddingTop
            invalidate(
                dirty.left + scrollX, dirty.top + scrollY,
                dirty.right + scrollX, dirty.bottom + scrollY
            )
        } else {
            super.invalidateDrawable(drawable)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val drawable: Drawable? = progressDrawable
        var dw = 0
        var dh = 0

        if (drawable != null) {
            dw = minWidth.coerceAtLeast(maxWidth.coerceAtMost(drawable.intrinsicWidth))
            dh = minHeight.coerceAtLeast(maxHeight.coerceAtMost(drawable.intrinsicHeight))
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
        return who === progressDrawable || super.verifyDrawable(who)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        progressDrawable?.jumpToCurrentState()
    }

    private fun setPrimaryLeftMargin(margin: Float) {
        primaryLeftMargin = margin
    }

    private fun setPrimaryRightMargin(margin: Float) {
        primaryRightMargin = margin
    }

    fun setProgress(p: Int) {
        var progress = p
        if (progress < 0) {
            progress = 0
        }
        if (progress > max) {
            progress = max
        }

        if (progress != this.progress) {
            this.progress = progress
            refreshProgress(android.R.id.progress, this.progress)
        }
    }

    fun setSecondaryProgress(sp: Int) {
        var secondaryProgress = sp
        if (secondaryProgress < 0) {
            secondaryProgress = 0
        }
        if (secondaryProgress > max) {
            secondaryProgress = max
        }

        if (secondaryProgress != this.secondaryProgress) {
            this.secondaryProgress = secondaryProgress
            refreshProgress(android.R.id.secondaryProgress, this.secondaryProgress)
        }
    }

    fun setMax(m: Int) {
        var max = m
        if (max < 0) {
            max = 0
        }
        if (max != this.max) {
            this.max = max
            postInvalidate()
            if (progress > max) {
                progress = max
            }
            refreshProgress(android.R.id.progress, progress)
        }
    }

    fun setSecondaryProgressTint(color: Int) {
        val drawable = progressDrawable
        if (drawable != null) {
            if (drawable is LayerDrawable) {
                val secondaryProgressDrawable = drawable.findDrawableByLayerId(android.R.id.secondaryProgress)
                secondaryProgressDrawable?.setTint(color)
            }
        }
    }

    private fun setProgressDrawable(drawable: Drawable) {
        val needUpdate: Boolean = if (progressDrawable != null && drawable !== progressDrawable) {
            progressDrawable?.callback = null
            true
        } else {
            false
        }

        drawable.callback = this
        // Make sure the ProgressBar is always tall enough
        val drawableHeight = drawable.minimumHeight
        if (maxHeight < drawableHeight) {
            maxHeight = drawableHeight
            requestLayout()
        }

        progressDrawable = drawable
        postInvalidate()

        if (needUpdate) {
            updateDrawableBounds(width, height)
            updateDrawableState()

            refreshProgress(android.R.id.progress, progress)
            refreshProgress(android.R.id.secondaryProgress, secondaryProgress)
        }
    }

    private fun refreshProgress(id: Int, progress: Int) {
        var scale: Float = if (max > 0) (progress.toFloat() / max) else 0f

        if (id == android.R.id.progress && scale > 0) {
            if (width > 0) {
                // Wait for secondaryProgress to have reached primaryLeftMargin to start primaryProgress at 0
                val leftOffset = primaryLeftMargin / width
                if (scale < leftOffset) return

                // Prevent primaryProgress to go further than (width - rightMargin)
                val rightOffset = primaryRightMargin / width
                if (scale > 1 - rightOffset) {
                    scale = 1 - rightOffset
                }

                // Remove left margin from primary progress
                scale -= leftOffset

                // Since we use setBounds() to apply margins to the Bitmaps,
                // the width of the bitmap is reduced so we have to adapt the level
                val widthScale = width - (primaryLeftMargin + primaryRightMargin)
                scale = ((scale * width) / widthScale)
            }
        }

        val drawable: Drawable? = progressDrawable
        if (drawable != null) {
            var progressDrawable: Drawable? = null
            if (drawable is LayerDrawable) {
                progressDrawable = drawable.findDrawableByLayerId(id)
            }
            (progressDrawable ?: drawable).level = (scale * MAX_LEVEL).toInt()
        } else {
            invalidate()
        }
    }

    private fun updateDrawableState() {
        val state = drawableState
        if (progressDrawable != null && progressDrawable?.isStateful == true) {
            progressDrawable?.state = state
        }
    }

    private fun updateDrawableBounds(w: Int, h: Int) {
        val right: Int = w - paddingRight - paddingLeft
        val bottom: Int = h - paddingBottom - paddingTop
        progressDrawable?.setBounds(0, 0, right, bottom)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = progressDrawable as? LayerDrawable

        if (drawable != null) {
            canvas.save()
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

            for (i in 0 until drawable.numberOfLayers) {
                val drawableLayer = drawable.getDrawable(i)
                if (i != 1) {
                    canvas.translate(primaryLeftMargin, 0f)
                    drawableLayer.draw(canvas)
                    drawableLayer.setBounds(0, 0, width - primaryRightMargin.toInt() - primaryLeftMargin.toInt(), height)
                    canvas.translate(-primaryLeftMargin, 0f)
                } else {
                    drawableLayer.draw(canvas)
                }
            }

            canvas.restore()
        }
    }

    internal class SavedState(superState: Parcelable?) : BaseSavedState(superState) {
        var max = 0
        var progress = 0
        var secondaryProgress = 0

        override fun writeToParcel(output: Parcel, flags: Int) {
            super.writeToParcel(output, flags)

            output.writeInt(max)
            output.writeInt(progress)
            output.writeInt(secondaryProgress)
        }
    }
}
