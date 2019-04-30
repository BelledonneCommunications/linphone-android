package org.linphone.activities;

/*
SplashScreenActivity.java
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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import org.linphone.R;

/**
 * This activity does pretty much nothing except starting the linphone launcher that will be
 * starting the Service which takes time and will delay the splashscreen UI creation to later, and
 * until that time it will display the default windowBackground. This activity will load faster,
 * thus showing the splashscreen sooner.
 */
public class SplashScreenActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (!getResources().getBoolean(R.bool.use_full_screen_image_splashscreen)) {
            setContentView(R.layout.launch_screen);
        } // Otherwise use drawable/launch_screen layer list up until first activity starts
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start the linphone launcher asynchronously otherwise it will wait for launcher to be
        // loaded which will render this workaround useless.
        new Handler()
                .postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent();
                                intent.setClass(
                                        SplashScreenActivity.this, LinphoneLauncherActivity.class);
                                if (getIntent() != null && getIntent().getExtras() != null) {
                                    intent.putExtras(getIntent().getExtras());
                                }
                                intent.setAction(getIntent().getAction());
                                intent.setType(getIntent().getType());
                                startActivity(intent);
                            }
                        },
                        100);
    }
}
