package org.linphone.settings;

/*
BasicSetting.java
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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.R;

public class BasicSetting extends LinearLayout {
    protected int mLayout = R.layout.settings_basic_preference;
    protected Context mContext;
    protected View mView;
    protected TextView mTitle, mSubtitle;
    protected SettingListener mListener;

    public BasicSetting(Context context) {
        super(context);
        mContext = context;
        init(null, 0, 0);
    }

    public BasicSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init(attrs, 0, 0);
    }

    public BasicSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init(attrs, defStyleAttr, 0);
    }

    public BasicSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        init(attrs, defStyleAttr, defStyleRes);
    }

    public void setListener(SettingListener listener) {
        mListener = listener;
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mView = LayoutInflater.from(mContext).inflate(mLayout, this, false);

        mTitle = mView.findViewById(R.id.setting_title);
        mSubtitle = mView.findViewById(R.id.setting_subtitle);

        RelativeLayout rlayout = mView.findViewById(R.id.setting_layout);
        rlayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mListener != null) {
                            mListener.onSettingClicked();
                        }
                    }
                });

        if (attrs != null) {
            TypedArray a =
                    mContext.getTheme()
                            .obtainStyledAttributes(
                                    attrs, R.styleable.Settings, defStyleAttr, defStyleRes);
            try {
                String title = a.getString(R.styleable.Settings_title);
                if (title != null) {
                    mTitle.setText(title);
                } else {
                    mTitle.setVisibility(GONE);
                }

                String subtitle = a.getString(R.styleable.Settings_subtitle);
                if (subtitle != null) {
                    mSubtitle.setText(subtitle);
                } else {
                    mSubtitle.setVisibility(GONE);
                }
            } finally {
                a.recycle();
            }
        }

        addView(mView);
    }
}
