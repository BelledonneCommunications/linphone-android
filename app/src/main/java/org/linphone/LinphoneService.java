package org.linphone;

/*
LinphoneService.java
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

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.view.WindowManager;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.GlobalState;
import org.linphone.core.LogLevel;
import org.linphone.core.LoggingService;
import org.linphone.core.LoggingServiceListener;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.notifications.NotificationsManager;
import org.linphone.receivers.BluetoothManager;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ActivityMonitor;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.LinphoneGL2JNIViewOverlay;
import org.linphone.views.LinphoneOverlay;
import org.linphone.views.LinphoneTextureViewOverlay;

/**
 * Linphone service, reacting to Incoming calls, ...<br>
 *
 * <p>Roles include:
 *
 * <ul>
 *   <li>Initializing LinphoneManager
 *   <li>Starting C libLinphone through LinphoneManager
 *   <li>Reacting to LinphoneManager state changes
 *   <li>Delegating GUI state change actions to GUI listener
 */
public final class LinphoneService extends Service {
    /* Listener needs to be implemented in the Service as it calls
     * setLatestEventInfo and startActivity() which needs a context.
     */
    private static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";

    private static LinphoneService sInstance;

    public final Handler handler = new Handler();

    private boolean mTestDelayElapsed = true;
    private CoreListenerStub mListener;
    private WindowManager mWindowManager;
    private LinphoneOverlay mOverlay;
    private Application.ActivityLifecycleCallbacks mActivityCallbacks;
    private NotificationsManager mNotificationManager;
    private String mIncomingReceivedActivityName;
    private Class<? extends Activity> mIncomingReceivedActivity = CallIncomingActivity.class;

    private final LoggingServiceListener mJavaLoggingService =
            new LoggingServiceListener() {
                @Override
                public void onLogMessageWritten(
                        LoggingService logService, String domain, LogLevel lev, String message) {
                    switch (lev) {
                        case Debug:
                            android.util.Log.d(domain, message);
                            break;
                        case Message:
                            android.util.Log.i(domain, message);
                            break;
                        case Warning:
                            android.util.Log.w(domain, message);
                            break;
                        case Error:
                            android.util.Log.e(domain, message);
                            break;
                        case Fatal:
                        default:
                            android.util.Log.wtf(domain, message);
                            break;
                    }
                }
            };

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();

        setupActivityMonitor();

        // Needed in order for the two next calls to succeed, libraries must have been loaded first
        LinphonePreferences.instance().setContext(getBaseContext());
        Factory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneUtils.configureLoggingService(isDebugEnabled, getString(R.string.app_name));
        // LinphoneService isn't ready yet so we have to manually set up the Java logging service
        if (LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().getLoggingService().addListener(mJavaLoggingService);
        }

        // Dump some debugging information to the logs
        Log.i(START_LINPHONE_LOGS);
        dumpDeviceInformation();
        dumpInstalledLinphoneInformation();

        mIncomingReceivedActivityName =
                LinphonePreferences.instance().getActivityToLaunchOnIncomingReceived();
        try {
            mIncomingReceivedActivity =
                    (Class<? extends Activity>) Class.forName(mIncomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core lc, Call call, Call.State state, String message) {
                        if (sInstance == null) {
                            Log.i(
                                    "[Service] Service not ready, discarding call state change to ",
                                    state.toString());
                            return;
                        }

                        if (getResources().getBoolean(R.bool.enable_call_notification)) {
                            mNotificationManager.displayCallNotification(call);
                        }

                        if (state == Call.State.IncomingReceived
                                || state == State.IncomingEarlyMedia) {
                            if (!LinphoneManager.getInstance().getCallGsmON()) onIncomingReceived();
                        } else if (state == State.OutgoingInit) {
                            onOutgoingStarted();
                        } else if (state == State.End
                                || state == State.Released
                                || state == State.Error) {
                            destroyOverlay();

                            if (state == State.Released
                                    && call.getCallLog().getStatus() == Call.Status.Missed) {
                                mNotificationManager.displayMissedCallNotification(call);
                            }
                        }
                    }

                    @Override
                    public void onGlobalStateChanged(Core lc, GlobalState state, String message) {}

                    @Override
                    public void onRegistrationStateChanged(
                            Core lc, ProxyConfig cfg, RegistrationState state, String smessage) {}
                };
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

        LinphoneManager.createAndStart(this, isPush);

        sInstance = this; // sInstance is ready once linphone manager has been created
        mNotificationManager = new NotificationsManager(this);
        LinphoneManager.getLc().addListener(mListener);

        if (Version.sdkAboveOrEqual(Version.API26_O_80)
                && intent != null
                && intent.getBooleanExtra("ForceStartForeground", false)) {
            mNotificationManager.startForeground();
        }

        if (!Version.sdkAboveOrEqual(Version.API26_O_80)
                || (ContactsManager.getInstance() != null
                        && ContactsManager.getInstance().hasReadContactsAccess())) {
            getContentResolver()
                    .registerContentObserver(
                            ContactsContract.Contacts.CONTENT_URI,
                            true,
                            ContactsManager.getInstance());
        }

        if (!mTestDelayElapsed) {
            // Only used when testing. Simulates a 5 seconds delay for launching service
            handler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            mTestDelayElapsed = true;
                        }
                    },
                    5000);
        }

        BluetoothManager.getInstance().initBluetooth();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean serviceNotif = LinphonePreferences.instance().getServiceNotificationVisibility();
        if (serviceNotif) {
            Log.i("[Service] Service is running in foreground, don't stop it");
        } else if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
            Log.i("[Service] Task removed, stop service");
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                lc.terminateAllCalls();
            }

            // If push is enabled, don't unregister account, otherwise do unregister
            if (LinphonePreferences.instance().isPushNotificationEnabled()) {
                if (lc != null) lc.setNetworkReachable(false);
            }
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @SuppressWarnings("UnusedAssignment")
    @Override
    public synchronized void onDestroy() {
        if (mActivityCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mActivityCallbacks);
            mActivityCallbacks = null;
        }
        destroyOverlay();

        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core != null) {
            core.removeListener(mListener);
            core = null; // To allow the gc calls below to free the Core
        }

        sInstance = null;
        LinphoneManager.destroy();

        // Make sure our notification is gone.
        if (mNotificationManager != null) {
            mNotificationManager.destroy();
        }

        if (LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().getLoggingService().removeListener(mJavaLoggingService);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isReady() {
        return sInstance != null && sInstance.mTestDelayElapsed;
    }

    public static LinphoneService instance() {
        if (isReady()) return sInstance;

        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    public LoggingServiceListener getJavaLoggingService() {
        return mJavaLoggingService;
    }

    public NotificationsManager getNotificationManager() {
        return mNotificationManager;
    }

    public void setCurrentlyDisplayedChatRoom(String address) {
        if (address != null) {
            mNotificationManager.resetMessageNotifCount(address);
        }
    }

    public void createOverlay() {
        if (mOverlay != null) destroyOverlay();

        Core core = LinphoneManager.getLc();
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

    private void dumpDeviceInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE=").append(Build.DEVICE).append("\n");
        sb.append("MODEL=").append(Build.MODEL).append("\n");
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Supported ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi).append(", ");
        }
        sb.append("\n");
        Log.i(sb.toString());
    }

    private void dumpInstalledLinphoneInformation() {
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException nnfe) {
            Log.e(nnfe);
        }

        if (info != null) {
            Log.i(
                    "[Service] Linphone version is ",
                    info.versionName + " (" + info.versionCode + ")");
        } else {
            Log.i("[Service] Linphone version is unknown");
        }
    }

    private void onIncomingReceived() {
        Intent intent = new Intent().setClass(this, mIncomingReceivedActivity);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void onOutgoingStarted() {
        Intent intent = new Intent(LinphoneService.this, CallOutgoingActivity.class);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
