/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
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
package org.linphone.dialer.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.EditText;
import org.linphone.R;

@SuppressLint("AppCompatCustomView")
public class AddressText extends EditText implements AddressType {
    private String mDisplayedName;
    private final Paint mTestPaint;
    private AddressChangedListener mAddressListener;

    public AddressText(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTestPaint = new Paint();
        mTestPaint.set(this.getPaint());
        mAddressListener = null;
    }

    private void clearDisplayedName() {
        mDisplayedName = null;
    }

    public String getDisplayedName() {
        return mDisplayedName;
    }

    public void setDisplayedName(String displayedName) {
        this.mDisplayedName = displayedName;
    }

    private String getHintText() {
        String resizedText = getContext().getString(R.string.address_bar_hint);
        if (getHint() != null) {
            resizedText = getHint().toString();
        }
        return resizedText;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        clearDisplayedName();

        refitText(getWidth(), getHeight());

        if (mAddressListener != null) {
            mAddressListener.onAddressChanged();
        }

        super.onTextChanged(text, start, before, after);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width != oldWidth) {
            refitText(getWidth(), getHeight());
        }
    }

    private float getOptimizedTextSize(String text, int textWidth, int textHeight) {
        int targetWidth = textWidth - getPaddingLeft() - getPaddingRight();
        int targetHeight = textHeight - getPaddingTop() - getPaddingBottom();
        float hi = 90;
        float lo = 2;
        final float threshold = 0.5f;

        mTestPaint.set(getPaint());

        while ((hi - lo) > threshold) {
            float size = (hi + lo) / 2;
            mTestPaint.setTextSize(size);
            if (mTestPaint.measureText(text) >= targetWidth || size >= targetHeight) {
                hi = size;
            } else {
                lo = size;
            }
        }

        return lo;
    }

    private void refitText(int textWidth, int textHeight) {
        if (textWidth <= 0) {
            return;
        }

        float size = getOptimizedTextSize(getHintText(), textWidth, textHeight);
        float entrySize = getOptimizedTextSize(getText().toString(), textWidth, textHeight);
        if (entrySize < size) size = entrySize;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int height = getMeasuredHeight();

        refitText(parentWidth, height);
        setMeasuredDimension(parentWidth, height);
    }

    public void setAddressListener(AddressChangedListener listener) {
        mAddressListener = listener;
    }

    public interface AddressChangedListener {
        void onAddressChanged();
    }
}
