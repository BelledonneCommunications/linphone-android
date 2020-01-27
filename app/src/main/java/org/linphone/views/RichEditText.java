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
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

@SuppressLint("AppCompatCustomView")
public class RichEditText extends EditText {
    public interface RichInputListener {
        boolean onCommitContent(
                InputContentInfoCompat inputContentInfo,
                int flags,
                Bundle opts,
                String[] contentMimeTypes);
    }

    private RichInputListener mListener;
    private String[] mSupportedMimeTypes;

    public RichEditText(Context context) {
        super(context);
    }

    public RichEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RichEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RichEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setListener(RichInputListener listener) {
        mListener = listener;
        mSupportedMimeTypes = new String[] {"image/png", "image/gif", "image/jpeg"};
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        final InputConnection ic = super.onCreateInputConnection(editorInfo);
        EditorInfoCompat.setContentMimeTypes(editorInfo, mSupportedMimeTypes);

        final InputConnectionCompat.OnCommitContentListener callback =
                new InputConnectionCompat.OnCommitContentListener() {
                    @Override
                    public boolean onCommitContent(
                            InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
                        if (mListener != null) {
                            return mListener.onCommitContent(
                                    inputContentInfo, flags, opts, mSupportedMimeTypes);
                        }
                        return false;
                    }
                };

        if (ic != null) {
            return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
        }
        return null;
    }
}
