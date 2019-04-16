package org.linphone.settings.widget;

/*
SwitchSetting.java
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
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import androidx.annotation.Nullable;
import org.linphone.R;

public class SwitchSetting extends BasicSetting {
    protected Switch mSwitch;

    public SwitchSetting(Context context) {
        super(context);
    }

    public SwitchSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView = LayoutInflater.from(mContext).inflate(R.layout.settings_widget_switch, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mSwitch = mView.findViewById(R.id.setting_switch);
        mSwitch.setOnCheckedChangeListener(
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
                        if (mSwitch.isEnabled()) {
                            toggle();
                        }
                    }
                });
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mSwitch.setEnabled(enabled);
    }

    public void setChecked(boolean checked) {
        mSwitch.setChecked(checked);
    }

    public boolean isChecked() {
        return mSwitch.isChecked();
    }

    public void toggle() {
        mSwitch.toggle();
    }
}
