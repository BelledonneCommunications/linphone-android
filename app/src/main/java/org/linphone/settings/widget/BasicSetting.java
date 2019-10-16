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
package org.linphone.settings.widget;

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
    protected View mView;
    protected SettingListener mListener;

    private TextView mTitle;
    private TextView mSubtitle;

    public BasicSetting(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public BasicSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public BasicSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    BasicSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    void inflateView() {
        mView =
                LayoutInflater.from(getContext())
                        .inflate(R.layout.settings_widget_basic, this, true);
    }

    public void setListener(SettingListener listener) {
        mListener = listener;
    }

    void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
                    getContext()
                            .getTheme()
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
