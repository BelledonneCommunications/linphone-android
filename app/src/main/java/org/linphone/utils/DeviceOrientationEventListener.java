package org.linphone.utils;

/*
DeviceOrientationEventListener.java
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
import android.view.OrientationEventListener;
import org.linphone.LinphoneManager;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public class DeviceOrientationEventListener extends OrientationEventListener {
    private int mAlwaysChangingPhoneAngle;

    public DeviceOrientationEventListener(Context context) {
        super(context);
        mAlwaysChangingPhoneAngle = -1;
    }

    @Override
    public void onOrientationChanged(final int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }

        int degrees = 270;
        if (orientation < 45 || orientation > 315) degrees = 0;
        else if (orientation < 135) degrees = 90;
        else if (orientation < 225) degrees = 180;

        if (mAlwaysChangingPhoneAngle == degrees) {
            return;
        }
        mAlwaysChangingPhoneAngle = degrees;
        Log.i("[Orientation Helper] Device orientation changed to " + degrees);

        int rotation = (360 - degrees) % 360;
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.setDeviceRotation(rotation);
        }
    }
}
