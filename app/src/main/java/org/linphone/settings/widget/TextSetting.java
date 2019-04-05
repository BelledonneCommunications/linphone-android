package org.linphone.settings.widget;

/*
TextSetting.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import androidx.annotation.Nullable;
import org.linphone.R;

public class TextSetting extends BasicSetting implements TextWatcher {
    protected EditText mInput;

    public TextSetting(Context context) {
        super(context);
    }

    public TextSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TextSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TextSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView = LayoutInflater.from(mContext).inflate(R.layout.settings_widget_text, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mInput = mView.findViewById(R.id.setting_input);

        if (attrs != null) {
            TypedArray a =
                    mContext.getTheme()
                            .obtainStyledAttributes(
                                    attrs, R.styleable.Settings, defStyleAttr, defStyleRes);
            try {
                String hint = a.getString(R.styleable.Settings_hint);
                mInput.setHint(hint);
            } finally {
                a.recycle();
            }
        }

        mInput.addTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (mListener != null) {
            mListener.onTextValueChanged(mInput.getText().toString());
        }
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mInput.setEnabled(enabled);
    }

    public void setInputType(int inputType) {
        mInput.setInputType(inputType);
    }

    public void setValue(String value) {
        mInput.setText(value);
    }

    public void setValue(int value) {
        setValue(String.valueOf(value));
    }

    public void setValue(float value) {
        setValue(String.valueOf(value));
    }

    public void setValue(double value) {
        setValue(String.valueOf(value));
    }

    public String getValue() {
        return mInput.getText().toString();
    }
}
