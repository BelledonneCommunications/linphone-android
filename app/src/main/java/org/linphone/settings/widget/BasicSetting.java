package org.linphone.settings.widget;

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

    protected void inflateView() {
        mView = LayoutInflater.from(mContext).inflate(R.layout.settings_widget_basic, this, true);
    }

    public void setListener(SettingListener listener) {
        mListener = listener;
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflateView();

        mTitle = mView.findViewById(R.id.setting_title);
        mSubtitle = mView.findViewById(R.id.setting_subtitle);

        RelativeLayout rlayout = mView.findViewById(R.id.setting_layout);
        rlayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mTitle.isEnabled() && mListener != null) {
                            mListener.onClicked();
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
    }

    public void setTitle(String title) {
        mTitle.setText(title);
        mTitle.setVisibility(title == null || title.isEmpty() ? GONE : VISIBLE);
    }

    public void setSubtitle(String subtitle) {
        mSubtitle.setText(subtitle);
        mSubtitle.setVisibility(subtitle == null || subtitle.isEmpty() ? GONE : VISIBLE);
    }

    public void setEnabled(boolean enabled) {
        mTitle.setEnabled(enabled);
        mSubtitle.setEnabled(enabled);
    }
}
