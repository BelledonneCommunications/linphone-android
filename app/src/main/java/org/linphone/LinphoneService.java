package org.linphone;

/*
LinphoneService.java
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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.view.WindowManager;
import java.util.ArrayList;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.GlobalState;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.notifications.NotificationsManager;
import org.linphone.receivers.BluetoothManager;
import org.linphone.receivers.KeepAliveReceiver;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.LinphoneOverlay;

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
    private Class<? extends Activity> mIncomingReceivedActivity = LinphoneActivity.class;

    public static boolean isReady() {
        return sInstance != null && sInstance.mTestDelayElapsed;
    }

    public static LinphoneService instance() {
        if (isReady()) return sInstance;

        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    public NotificationsManager getNotificationManager() {
        return mNotificationManager;
    }

    public Class<? extends Activity> getIncomingReceivedActivity() {
        return mIncomingReceivedActivity;
    }

    public void setCurrentlyDisplayedChatRoom(String address) {
        if (address != null) {
            mNotificationManager.resetMessageNotifCount(address);
        }
    }

    private void onBackgroundMode() {
        Log.i("App has entered background mode");
        if (LinphonePreferences.instance() != null
                && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            if (LinphoneManager.isInstanciated())
                LinphoneManager.getInstance().subscribeFriendList(false);
        }
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            LinphoneManager.getLcIfManagerNotDestroyedOrNull().enterBackground();
        }
    }

    private void onForegroundMode() {
        Log.i("App has left background mode");
        if (LinphonePreferences.instance() != null
                && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            if (LinphoneManager.isInstanciated())
                LinphoneManager.getInstance().subscribeFriendList(true);
        }
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            LinphoneManager.getLcIfManagerNotDestroyedOrNull().enterForeground();
        }
    }

    private void setupActivityMonitor() {
        if (mActivityCallbacks != null) return;
        getApplication()
                .registerActivityLifecycleCallbacks(mActivityCallbacks = new ActivityMonitor());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent.getBooleanExtra("PushNotification", false)) {
            Log.i("[Push Notification] LinphoneService started because of a push");
        }

        if (sInstance != null) {
            Log.w("Attempt to start the LinphoneService but it is already running !");
            return START_REDELIVER_INTENT;
        }

        LinphoneManager.createAndStart(this);

        sInstance = this; // sInstance is ready once linphone manager has been created
        mNotificationManager = new NotificationsManager(this);
        LinphoneManager.getLc()
                .addListener(
                        mListener =
                                new CoreListenerStub() {
                                    @Override
                                    public void onCallStateChanged(
                                            Core lc, Call call, Call.State state, String message) {
                                        if (sInstance == null) {
                                            Log.i(
                                                    "Service not ready, discarding call state change to ",
                                                    state.toString());
                                            return;
                                        }

                                        if (getResources()
                                                .getBoolean(R.bool.enable_call_notification)) {
                                            mNotificationManager.displayCallNotification(call);
                                        }

                                        if (state == Call.State.IncomingReceived) {
                                            if (!LinphoneManager.getInstance().getCallGsmON())
                                                onIncomingReceived();
                                        }

                                        if (state == State.End
                                                || state == State.Released
                                                || state == State.Error) {
                                            destroyOverlay();
                                        }

                                        if (state == State.Released
                                                && call.getCallLog().getStatus()
                                                        == Call.Status.Missed) {
                                            mNotificationManager.displayMissedCallNotification(
                                                    call);
                                        }
                                    }

                                    @Override
                                    public void onGlobalStateChanged(
                                            Core lc, GlobalState state, String message) {
                                        // TODO global state if ON
                                    }

                                    @Override
                                    public void onRegistrationStateChanged(
                                            Core lc,
                                            ProxyConfig cfg,
                                            RegistrationState state,
                                            String smessage) {
                                        // TODO registration status
                                    }
                                });

        if (Version.sdkAboveOrEqual(Version.API26_O_80)
                && intent.getBooleanExtra("ForceStartForeground", false)) {
            mNotificationManager.startForeground();
        }

        if (!Version.sdkAboveOrEqual(Version.API26_O_80)
                || (ContactsManager.getInstance() != null
                        && ContactsManager.getInstance().hasContactsAccess())) {
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

        // make sure the application will at least wakes up every 10 mn
        if (LinphonePreferences.instance().isBackgroundModeEnabled()
                && (!LinphonePreferences.instance().isPushNotificationEnabled()
                        || !LinphoneManager.getInstance().hasLinphoneAccount())) {
            Intent keepAliveIntent = new Intent(this, KeepAliveReceiver.class);
            PendingIntent keepAlivePendingIntent =
                    PendingIntent.getBroadcast(
                            this, 0, keepAliveIntent, PendingIntent.FLAG_ONE_SHOT);
            AlarmManager alarmManager =
                    ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE));
            alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 600000,
                    keepAlivePendingIntent);
        }

        BluetoothManager.getInstance().initBluetooth();

        return START_REDELIVER_INTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();

        setupActivityMonitor();

        // Needed in order for the two next calls to succeed, libraries must have been loaded first
        LinphonePreferences.instance().setContext(getBaseContext());
        Factory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneUtils.initLoggingService(isDebugEnabled, getString(R.string.app_name));

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
    }

    public void createOverlay() {
        if (mOverlay != null) destroyOverlay();

        Call call = LinphoneManager.getLc().getCurrentCall();
        if (call == null || !call.getCurrentParams().videoEnabled()) return;

        mOverlay = new LinphoneOverlay(this);
        WindowManager.LayoutParams params = mOverlay.getWindowManagerLayoutParams();
        params.x = 0;
        params.y = 0;
        mWindowManager.addView(mOverlay, params);
    }

    public void destroyOverlay() {
        if (mOverlay != null) {
            mWindowManager.removeViewImmediate(mOverlay);
            mOverlay.destroy();
        }
        mOverlay = null;
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
            Log.i("Linphone version is ", info.versionName + " (" + info.versionCode + ")");
        } else {
            Log.i("Linphone version is unknown");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                lc.terminateAllCalls();
            }

            Log.d("Task removed, stop service");

            // If push is enabled, don't unregister account, otherwise do unregister
            if (LinphonePreferences.instance().isPushNotificationEnabled()) {
                if (lc != null) lc.setNetworkReachable(false);
            }
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public synchronized void onDestroy() {
        if (mActivityCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(mActivityCallbacks);
            mActivityCallbacks = null;
        }

        destroyOverlay();
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        sInstance = null;
        LinphoneManager.destroy();

        // Make sure our notification is gone.
        mNotificationManager.destroy();

        // This will prevent the app from crashing if the service gets killed in background mode
        if (LinphoneActivity.isInstanciated()) {
            Log.w("Service is getting destroyed, finish LinphoneActivity");
            LinphoneActivity.instance().finish();
        }

        super.onDestroy();
    }

    @SuppressWarnings("unchecked")
    public void setActivityToLaunchOnIncomingReceived(String activityName) {
        try {
            mIncomingReceivedActivity = (Class<? extends Activity>) Class.forName(activityName);
            mIncomingReceivedActivityName = activityName;
            LinphonePreferences.instance()
                    .setActivityToLaunchOnIncomingReceived(mIncomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }
    }

    private void onIncomingReceived() {
        // wakeup linphone
        startActivity(
                new Intent()
                        .setClass(this, mIncomingReceivedActivity)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /*Believe me or not, but knowing the application visibility state on Android is a nightmare.
    After two days of hard work I ended with the following class, that does the job more or less reliabily.
    */
    class ActivityMonitor implements Application.ActivityLifecycleCallbacks {
        private final ArrayList<Activity> activities = new ArrayList<>();
        private boolean mActive = false;
        private int mRunningActivities = 0;
        private InactivityChecker mLastChecker;

        @Override
        public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            Log.i("Activity created:" + activity);
            if (!activities.contains(activity)) activities.add(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            Log.i("Activity started:" + activity);
        }

        @Override
        public synchronized void onActivityResumed(Activity activity) {
            Log.i("Activity resumed:" + activity);
            if (activities.contains(activity)) {
                mRunningActivities++;
                Log.i("runningActivities=" + mRunningActivities);
                checkActivity();
            }
        }

        @Override
        public synchronized void onActivityPaused(Activity activity) {
            Log.i("Activity paused:" + activity);
            if (activities.contains(activity)) {
                mRunningActivities--;
                Log.i("runningActivities=" + mRunningActivities);
                checkActivity();
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
            Log.i("Activity stopped:" + activity);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public synchronized void onActivityDestroyed(Activity activity) {
            Log.i("Activity destroyed:" + activity);
            activities.remove(activity);
        }

        void startInactivityChecker() {
            if (mLastChecker != null) mLastChecker.cancel();
            LinphoneService.this.handler.postDelayed(
                    (mLastChecker = new InactivityChecker()), 2000);
        }

        void checkActivity() {
            if (mRunningActivities == 0) {
                if (mActive) startInactivityChecker();
            } else if (mRunningActivities > 0) {
                if (!mActive) {
                    mActive = true;
                    LinphoneService.this.onForegroundMode();
                }
                if (mLastChecker != null) {
                    mLastChecker.cancel();
                    mLastChecker = null;
                }
            }
        }

        class InactivityChecker implements Runnable {
            private boolean isCanceled;

            void cancel() {
                isCanceled = true;
            }

            @Override
            public void run() {
                synchronized (LinphoneService.this) {
                    if (!isCanceled) {
                        if (ActivityMonitor.this.mRunningActivities == 0 && mActive) {
                            mActive = false;
                            LinphoneService.this.onBackgroundMode();
                        }
                    }
                }
            }
        }
    }
}
