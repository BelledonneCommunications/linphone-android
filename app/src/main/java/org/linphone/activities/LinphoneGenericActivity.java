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
package org.linphone.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Surface;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;
import org.linphone.service.LinphoneService;

public abstract class LinphoneGenericActivity extends ThemeableActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensureServiceIsRunning();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ensureServiceIsRunning();

        if (LinphoneContext.isReady()) {
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

    private void ensureServiceIsRunning() {
        if (!LinphoneService.isReady()) {
            if (!LinphoneContext.isReady()) {
                new LinphoneContext(getApplicationContext());
                LinphoneContext.instance().start(false);
                Log.i("[Generic Activity] Context created & started");
            }

            Log.i("[Generic Activity] Starting Service");
            try {
                startService(new Intent().setClass(this, LinphoneService.class));
            } catch (IllegalStateException ise) {
                Log.e("[Generic Activity] Couldn't start service, exception: ", ise);
            }
        }
    }
}
