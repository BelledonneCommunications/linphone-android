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
package org.linphone.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.annotation.Nullable;

/**
 * The purpose of this class is to have a TextView declared with wrap_content as width that won't
 * fill it's parent if it is multi line
 */
@SuppressLint("AppCompatCustomView")
public class MultiLineWrapContentWidthTextView extends TextView {

    public MultiLineWrapContentWidthTextView(Context context) {
        super(context);
    }

    public MultiLineWrapContentWidthTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiLineWrapContentWidthTextView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthMode = MeasureSpec.getMode(widthSpec);

        if (widthMode == MeasureSpec.AT_MOST) {
            Layout layout = getLayout();
            if (layout != null) {
                int maxWidth =
                        (int) Math.ceil(getMaxLineWidth(layout))
                                + getTotalPaddingLeft()
                                + getTotalPaddingRight();
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
        }

        super.onMeasure(widthSpec, heightSpec);
    }

    private float getMaxLineWidth(Layout layout) {
        float max_width = 0.0f;
        int lines = layout.getLineCount();
        for (int i = 0; i < lines; i++) {
            if (layout.getLineWidth(i) > max_width) {
                max_width = layout.getLineWidth(i);
            }
        }
        return max_width;
    }
}
