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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import org.linphone.R;

public class LedSetting extends BasicSetting {
    public enum Color {
        GRAY,
        GREEN,
        ORANGE,
        RED
    }

    private ImageView mLed;

    public LedSetting(Context context) {
        super(context);
    }

    public LedSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LedSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LedSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView = LayoutInflater.from(getContext()).inflate(R.layout.settings_widget_led, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mLed = mView.findViewById(R.id.setting_led);
    }

    public void setColor(Color color) {
        switch (color) {
            case GRAY:
                mLed.setImageResource(R.drawable.led_disconnected);
                break;
            case GREEN:
                mLed.setImageResource(R.drawable.led_connected);
                break;
            case ORANGE:
                mLed.setImageResource(R.drawable.led_inprogress);
                break;
            case RED:
                mLed.setImageResource(R.drawable.led_error);
                break;
        }
    }
}
