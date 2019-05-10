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
import org.linphone.LinphoneService;

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
}
