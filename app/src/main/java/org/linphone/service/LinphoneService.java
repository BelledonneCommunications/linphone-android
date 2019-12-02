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
package org.linphone.service;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.WindowManager;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.views.LinphoneGL2JNIViewOverlay;
import org.linphone.call.views.LinphoneOverlay;
import org.linphone.call.views.LinphoneTextureViewOverlay;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.settings.LinphonePreferences;

public final class LinphoneService extends Service {
    private static LinphoneService sInstance;

    private LinphoneOverlay mOverlay;
    private WindowManager mWindowManager;
    private Application.ActivityLifecycleCallbacks mActivityCallbacks;
    private boolean misLinphoneContextOwned;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();

        setupActivityMonitor();

        misLinphoneContextOwned = false;
        if (!LinphoneContext.isReady()) {
            new LinphoneContext(getApplicationContext());
            misLinphoneContextOwned = true;
        }
        Log.i("[Service] Created");

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        boolean isPush = false;
        if (intent != null && intent.getBooleanExtra("PushNotification", false)) {
            Log.i("[Service] [Push Notification] LinphoneService started because of a push");
            isPush = true;
        }

        if (sInstance != null) {
            Log.w("[Service] Attempt to start the LinphoneService but it is already running !");
            return START_STICKY;
        }
        sInstance = this;

        if (LinphonePreferences.instance().getServiceNotificationVisibility()
                || (Version.sdkAboveOrEqual(Version.API26_O_80)
                        && intent != null
                        && intent.getBooleanExtra("ForceStartForeground", false))) {
            Log.i("[Service] Background service mode enabled, displaying notification");
            // We need to call this asap after the Service can be accessed through it's singleton
            LinphoneContext.instance().getNotificationManager().startForeground();
        }

        if (misLinphoneContextOwned) {
            LinphoneContext.instance().start(isPush);
        } else {
            LinphoneContext.instance().updateContext(this);
        }

        Log.i("[Service] Started");
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean serviceNotif = LinphonePreferences.instance().getServiceNotificationVisibility();
        if (serviceNotif) {
            Log.i("[Service] Service is running in foreground, don't stop it");
        } else if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
            Log.i("[Service] Task removed, stop service");
            Core core = LinphoneManager.getCore();
            if (core != null) {
                core.terminateAllCalls();
            }

            // If push is enabled, don't unregister account, otherwise do unregister
            if (LinphonePreferences.instance().isPushNotificationEnabled()) {
                if (core != null) core.setNetworkReachable(false);
            }
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public synchronized void onDestroy() {
        Log.i("[Service] Destroying");

        if (mActivityCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mActivityCallbacks);
            mActivityCallbacks = null;
        }
        destroyOverlay();

        LinphoneContext.instance().destroy();
        sInstance = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinphoneService instance() {
        if (isReady()) return sInstance;

        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    /* Managers accessors */

    public void createOverlay() {
        Log.i("[Service] Creating video overlay");
        if (mOverlay != null) destroyOverlay();

        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null || !call.getCurrentParams().videoEnabled()) return;

        if ("MSAndroidOpenGLDisplay".equals(core.getVideoDisplayFilter())) {
            mOverlay = new LinphoneGL2JNIViewOverlay(this);
        } else {
            mOverlay = new LinphoneTextureViewOverlay(this);
        }
        WindowManager.LayoutParams params = mOverlay.getWindowManagerLayoutParams();
        params.x = 0;
        params.y = 0;
        mOverlay.addToWindowManager(mWindowManager, params);
    }

    public void destroyOverlay() {
        Log.i("[Service] Destroying video overlay");
        if (mOverlay != null) {
            mOverlay.removeFromWindowManager(mWindowManager);
            mOverlay.destroy();
        }
        mOverlay = null;
    }

    private void setupActivityMonitor() {
        if (mActivityCallbacks != null) return;
        getApplication()
                .registerActivityLifecycleCallbacks(mActivityCallbacks = new ActivityMonitor());
    }
}
