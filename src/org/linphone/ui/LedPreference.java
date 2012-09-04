package org.linphone.ui;

/*
LedPreference.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import org.linphone.R;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.ImageView;

/**
 * @author Sylvain Berfini
 */
public class LedPreference extends Preference
{
	private int ledDrawable;
	
	public LedPreference(Context context) {
        super(context);
        ledDrawable = R.drawable.led_disconnected;
        this.setWidgetLayoutResource(R.layout.preference_led);
    }

    @Override
    protected void onBindView(final View view) {
        super.onBindView(view);

        final ImageView imageView = (ImageView) view.findViewById(R.id.led);
        if (imageView != null) {
            imageView.setImageResource(ledDrawable);
        }
    }

    public void setLed(int led) {
    	ledDrawable = led;
        notifyChanged();
    }
}
