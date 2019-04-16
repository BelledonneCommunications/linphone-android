package org.linphone.settings.widget;

/*
CheckBoxSetting.java
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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import org.linphone.R;

public class CheckBoxSetting extends BasicSetting {
    protected CheckBox mCheckBox;

    public CheckBoxSetting(Context context) {
        super(context);
    }

    public CheckBoxSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckBoxSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckBoxSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView =
                LayoutInflater.from(mContext)
                        .inflate(R.layout.settings_widget_checkbox, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mCheckBox = mView.findViewById(R.id.setting_checkbox);
        mCheckBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (mListener != null) {
                            mListener.onBoolValueChanged(isChecked);
                        }
                    }
                });

        RelativeLayout rlayout = mView.findViewById(R.id.setting_layout);
        rlayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCheckBox.isEnabled()) {
                            toggle();
                        }
                    }
                });
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mCheckBox.setEnabled(enabled);
    }

    public void setChecked(boolean checked) {
        mCheckBox.setChecked(checked);
    }

    public boolean isChecked() {
        return mCheckBox.isChecked();
    }

    public void toggle() {
        mCheckBox.toggle();
    }
}
