package org.linphone.views;

/*
LedPreference.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
import android.preference.Preference;
import android.view.View;
import android.widget.ImageView;
import org.linphone.R;

public class LedPreference extends Preference {
    private int mLedDrawable;

    public LedPreference(Context context) {
        super(context);
        mLedDrawable = R.drawable.led_disconnected;
        setWidgetLayoutResource(R.layout.preference_led);
    }

    @Override
    protected void onBindView(final View view) {
        super.onBindView(view);

        final ImageView imageView = view.findViewById(R.id.led);
        if (imageView != null) {
            imageView.setImageResource(mLedDrawable);
        }
    }

    public void setLed(int led) {
        mLedDrawable = led;
        notifyChanged();
    }
}
