package org.linphone.activities;

/*
ThemeableActivity.java
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

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public abstract class ThemeableActivity extends AppCompatActivity {
    private int mTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mTheme = R.style.LinphoneStyleLight;
        if (LinphonePreferences.instance().isDarkModeEnabled()) {
            mTheme = R.style.LinphoneStyleDark;
            setTheme(R.style.LinphoneStyleDark);
        }

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("Theme", mTheme);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mTheme = savedInstanceState.getInt("Theme");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (LinphonePreferences.instance().isDarkModeEnabled()) {
            if (mTheme != R.style.LinphoneStyleDark) {
                Log.w("[Themeable Activity] Recreate Activity cause theme doesn't match");
                recreate();
            }
        } else {
            if (mTheme != R.style.LinphoneStyleLight) {
                Log.w("[Themeable Activity] Recreate Activity cause theme doesn't match");
                recreate();
            }
        }
    }
}
