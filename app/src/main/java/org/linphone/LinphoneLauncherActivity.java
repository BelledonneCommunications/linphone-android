package org.linphone;

/*
LinphoneLauncherActivity.java
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

import static android.content.Intent.ACTION_MAIN;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import org.linphone.assistant.RemoteProvisioningActivity;
import org.linphone.settings.LinphonePreferences;

/** Launch Linphone main activity when Service is ready. */
public class LinphoneLauncherActivity extends Activity {

    private Handler mHandler;
    private ServiceWaitThread mServiceThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hack to avoid to draw twice LinphoneActivity on tablets
        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        if (getResources().getBoolean(R.bool.use_full_screen_image_splashscreen)) {
            setContentView(R.layout.launch_screen_full_image);
        } else {
            setContentView(R.layout.launch_screen);
        }

        mHandler = new Handler();

        if (LinphoneService.isReady()) {
            onServiceReady();
        } else {
            // start linphone as background
            startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
            mServiceThread = new ServiceWaitThread();
            mServiceThread.start();
        }
    }

    private void onServiceReady() {
        final Class<? extends Activity> classToStart;
        /*if (getResources().getBoolean(R.bool.show_tutorials_instead_of_app)) {
        	classToStart = TutorialLauncherActivity.class;
        } else */
        if (getResources().getBoolean(R.bool.display_sms_remote_provisioning_activity)
                && LinphonePreferences.instance().isFirstRemoteProvisioning()) {
            classToStart = RemoteProvisioningActivity.class;
        } else {
            classToStart = LinphoneActivity.class;
        }

        mHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(
                                getIntent().setClass(LinphoneLauncherActivity.this, classToStart));
                    }
                },
                500);

        LinphoneManager.getInstance().changeStatusToOnline();
    }

    private class ServiceWaitThread extends Thread {
        public void run() {
            while (!LinphoneService.isReady()) {
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            onServiceReady();
                        }
                    });
            mServiceThread = null;
        }
    }
}
