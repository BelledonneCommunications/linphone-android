package org.linphone.activities;

/*
LinphoneGenericActivity.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.view.Surface;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public abstract class LinphoneGenericActivity extends ThemeableActivity {
    protected boolean mAbortCreation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAbortCreation = false;
        super.onCreate(savedInstanceState);
        // After a crash, Android restart the last Activity so we need to check
        // if all dependencies are loaded
        if (!LinphoneService.isReady()) {
            startActivity(getIntent().setClass(this, LinphoneLauncherActivity.class));
            mAbortCreation = true;
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (LinphoneService.isReady()) {
            int degrees = 270;
            int orientation = getWindowManager().getDefaultDisplay().getRotation();
            switch (orientation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 270;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 90;
                    break;
            }

            Log.i(
                    "[Generic Activity] Device orientation is "
                            + degrees
                            + " (raw value is "
                            + orientation
                            + ")");

            int rotation = (360 - degrees) % 360;
            Core core = LinphoneManager.getCore();
            if (core != null) {
                core.setDeviceRotation(rotation);
            }
        }
    }
}
