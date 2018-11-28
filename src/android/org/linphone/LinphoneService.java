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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.WindowManager;

import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
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
import org.linphone.receivers.BluetoothManager;
import org.linphone.receivers.KeepAliveReceiver;
import org.linphone.ui.LinphoneOverlay;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Linphone service, reacting to Incoming calls, ...<br />
 * <p>
 * Roles include:<ul>
 * <li>Initializing LinphoneManager</li>
 * <li>Starting C libLinphone through LinphoneManager</li>
 * <li>Reacting to LinphoneManager state changes</li>
 * <li>Delegating GUI state change actions to GUI listener</li>
 */
public final class LinphoneService extends Service {
    /* Listener needs to be implemented in the Service as it calls
     * setLatestEventInfo and startActivity() which needs a context.
     */
    public static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
    public static final int IC_LEVEL_ORANGE = 0;
	/*private static final int IC_LEVEL_GREEN=1;
	private static final int IC_LEVEL_RED=2;*/
    //public static final int IC_LEVEL_OFFLINE=3;

    private static LinphoneService instance;

    private final static int NOTIF_ID = 1;
    private final static int INCALL_NOTIF_ID = 2;
    private final static int CUSTOM_NOTIF_ID = 4;
    private final static int MISSED_NOTIF_ID = 5;
    private final static int SAS_NOTIF_ID = 6;

    public static boolean isReady() {
        return instance != null && instance.mTestDelayElapsed;
    }

    /**
     * @throws RuntimeException service not instantiated
     */
    public static LinphoneService instance() {
        if (isReady()) return instance;

        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    public Handler mHandler = new Handler();

    //	private boolean mTestDelayElapsed; // add a timer for testing
    private boolean mTestDelayElapsed = true; // no timer
    private NotificationManager mNM;

    private Notification mNotif;
    private Notification mIncallNotif;
    private Notification mCustomNotif;
    private Notification mSasNotif;
    private PendingIntent mNotifContentIntent;
    private String mNotificationTitle;
    private boolean mDisableRegistrationStatus;
    private CoreListenerStub mListener;
    public static int notifcationsPriority = (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41) ? Notification.PRIORITY_MIN : 0);
    private WindowManager mWindowManager;
    private LinphoneOverlay mOverlay;
    private Application.ActivityLifecycleCallbacks activityCallbacks;

    private class Notified {
        int notificationId;
        int numberOfUnreadMessage;
    }

    private HashMap<String, Notified> mChatNotifMap;
    private int mLastNotificationId;

    public void setCurrentlyDisplayedChatRoom(String address) {
        if (address != null) {
            resetMessageNotifCount(address);
        }
    }

    private void resetMessageNotifCount(String address) {
        Notified notif = mChatNotifMap.get(address);
        if (notif != null) {
            notif.numberOfUnreadMessage = 0;
            mNM.cancel(notif.notificationId);
        }
    }

    /*Believe me or not, but knowing the application visibility state on Android is a nightmare.
    After two days of hard work I ended with the following class, that does the job more or less reliabily.
    */
    class ActivityMonitor implements Application.ActivityLifecycleCallbacks {
        private ArrayList<Activity> activities = new ArrayList<>();
        private boolean mActive = false;
        private int mRunningActivities = 0;

        class InactivityChecker implements Runnable {
            private boolean isCanceled;

            public void cancel() {
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

        ;

        private InactivityChecker mLastChecker;

        @Override
        public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            Log.i("Activity created:" + activity);
            if (!activities.contains(activity))
                activities.add(activity);
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
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public synchronized void onActivityDestroyed(Activity activity) {
            Log.i("Activity destroyed:" + activity);
            if (activities.contains(activity)) {
                activities.remove(activity);
            }
        }

        void startInactivityChecker() {
            if (mLastChecker != null) mLastChecker.cancel();
            LinphoneService.this.mHandler.postDelayed(
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
    }

    protected void onBackgroundMode() {
        Log.i("App has entered background mode");
        if (LinphonePreferences.instance() != null && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            if (LinphoneManager.isInstanciated())
                LinphoneManager.getInstance().subscribeFriendList(false);
        }
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            LinphoneManager.getLcIfManagerNotDestroyedOrNull().enterBackground();
        }
    }

    protected void onForegroundMode() {
        Log.i("App has left background mode");
        if (LinphonePreferences.instance() != null && LinphonePreferences.instance().isFriendlistsubscriptionEnabled()) {
            if (LinphoneManager.isInstanciated())
                LinphoneManager.getInstance().subscribeFriendList(true);
        }
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            LinphoneManager.getLcIfManagerNotDestroyedOrNull().enterForeground();
        }
    }

    private void setupActivityMonitor() {
        if (activityCallbacks != null) return;
        getApplication().registerActivityLifecycleCallbacks(activityCallbacks = new ActivityMonitor());
    }

    public boolean displayServiceNotification() {
        return LinphonePreferences.instance().getServiceNotificationVisibility();
    }

    public void showServiceNotification() {
        startForegroundCompat(NOTIF_ID, mNotif);

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;
        ProxyConfig lpc = lc.getDefaultProxyConfig();
        if (lpc != null) {
            if (lpc.getState() == RegistrationState.Ok) {
                sendNotification(IC_LEVEL_ORANGE, R.string.notification_registered);
            } else {
                sendNotification(IC_LEVEL_ORANGE, R.string.notification_register_failure);
            }
        } else {
            sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
        }
    }

    public void hideServiceNotification() {
        stopForegroundCompat(NOTIF_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent.getBooleanExtra("PushNotification", false)) {
            Log.i("[Push Notification] LinphoneService started because of a push");
        }

        if (instance != null) {
            Log.w("Attempt to start the LinphoneService but it is already running !");
            return START_REDELIVER_INTENT;
        }

        LinphoneManager.createAndStart(LinphoneService.this);

        instance = this; // instance is ready once linphone manager has been created
        LinphoneManager.getLc().addListener(mListener = new CoreListenerStub() {
            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (instance == null) {
                    Log.i("Service not ready, discarding call state change to ", state.toString());
                    return;
                }

                if (state == Call.State.IncomingReceived) {
                    if (!LinphoneManager.getInstance().getCallGsmON())
                        onIncomingReceived();
                }

                if (state == State.End || state == State.Released || state == State.Error) {
                    if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null && LinphoneManager.getLc().getCallsNb() == 0) {
                        if (LinphoneActivity.isInstanciated() && LinphoneActivity.instance().getStatusFragment() != null) {
                            removeSasNotification();
                            LinphoneActivity.instance().getStatusFragment().setisZrtpAsk(false);
                        }
                    }
                    destroyOverlay();
                }

                if (state == State.End && call.getCallLog().getStatus() == Call.Status.Missed) {
                    int missedCallCount = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getMissedCallsCount();
                    String body;
                    if (missedCallCount > 1) {
                        body = getString(R.string.missed_calls_notif_body).replace("%i", String.valueOf(missedCallCount));
                    } else {
                        Address address = call.getRemoteAddress();
                        LinphoneContact c = ContactsManager.getInstance().findContactFromAddress(address);
                        if (c != null) {
                            body = c.getFullName();
                        } else {
                            body = address.getDisplayName();
                            if (body == null) {
                                body = address.asStringUriOnly();
                            }
                        }
                    }


                    Intent missedCallNotifIntent = new Intent(LinphoneService.this, incomingReceivedActivity);
                    missedCallNotifIntent.putExtra("GoToHistory", true);
                    PendingIntent intent = PendingIntent.getActivity(LinphoneService.this, 0, missedCallNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification notif = Compatibility.createMissedCallNotification(instance, getString(R.string.missed_calls_notif_title), body, intent);
                    notifyWrapper(MISSED_NOTIF_ID, notif);
                }

                if (state == State.StreamsRunning) {
                    // Workaround bug current call seems to be updated after state changed to streams running
                    if (getResources().getBoolean(R.bool.enable_call_notification))
                        refreshIncallIcon(call);
                } else {
                    if (getResources().getBoolean(R.bool.enable_call_notification))
                        refreshIncallIcon(LinphoneManager.getLc().getCurrentCall());
                }
            }

            @Override
            public void onGlobalStateChanged(Core lc, GlobalState state, String message) {
                if (!mDisableRegistrationStatus && state == GlobalState.On && displayServiceNotification()) {
                    sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
                }
            }

            @Override
            public void onRegistrationStateChanged(Core lc, ProxyConfig cfg, RegistrationState state, String smessage) {
//				if (instance == null) {
//					Log.i("Service not ready, discarding registration state change to ",state.toString());
//					return;
//				}
                if (!mDisableRegistrationStatus) {
                    if (displayServiceNotification() && state == RegistrationState.Ok && LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphoneManager.getLc().getDefaultProxyConfig().getState() == RegistrationState.Ok) {
                        sendNotification(IC_LEVEL_ORANGE, R.string.notification_registered);
                    }

                    if (displayServiceNotification() && (state == RegistrationState.Failed || state == RegistrationState.Cleared) && (LinphoneManager.getLc().getDefaultProxyConfig() == null || !(LinphoneManager.getLc().getDefaultProxyConfig().getState() == RegistrationState.Ok))) {
                        sendNotification(IC_LEVEL_ORANGE, R.string.notification_register_failure);
                    }

                    if (displayServiceNotification() && state == RegistrationState.None) {
                        sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
                    }
                }
            }
        });

        if (displayServiceNotification() || (Version.sdkAboveOrEqual(Version.API26_O_80) && intent.getBooleanExtra("ForceStartForeground", false))) {
            startForegroundCompat(NOTIF_ID, mNotif);
        }

        if (!Version.sdkAboveOrEqual(Version.API26_O_80)
                || (ContactsManager.getInstance() != null && ContactsManager.getInstance().hasContactsAccess())) {
            getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, ContactsManager.getInstance());
        }

        if (!mTestDelayElapsed) {
            // Only used when testing. Simulates a 5 seconds delay for launching service
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mTestDelayElapsed = true;
                }
            }, 5000);
        }

        //make sure the application will at least wakes up every 10 mn
        if (LinphonePreferences.instance().isBackgroundModeEnabled() &&
                (!LinphonePreferences.instance().isPushNotificationEnabled() || !LinphoneManager.getInstance().hasLinphoneAccount())) {
            Intent keepAliveIntent = new Intent(this, KeepAliveReceiver.class);
            PendingIntent keepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, keepAliveIntent, PendingIntent.FLAG_ONE_SHOT);
            AlarmManager alarmManager = ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE));
            Compatibility.scheduleAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, keepAlivePendingIntent);
        }

        BluetoothManager.getInstance().initBluetooth();

        return START_REDELIVER_INTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();
        mLastNotificationId = 8; // To not interfere with other notifs ids
        mChatNotifMap = new HashMap<>();

        setupActivityMonitor();
        // In case restart after a crash. Main in LinphoneActivity
        mNotificationTitle = getString(R.string.service_name);

        // Needed in order for the two next calls to succeed, libraries must have been loaded first
        LinphonePreferences.instance().setContext(getBaseContext());
        Factory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneUtils.initLoggingService(isDebugEnabled, getString(R.string.app_name));

        // Dump some debugging information to the logs
        Log.i(START_LINPHONE_LOGS);
        dumpDeviceInformation();
        dumpInstalledLinphoneInformation();

        //Disable service notification for Android O
        if ((Version.sdkAboveOrEqual(Version.API26_O_80))) {
            LinphonePreferences.instance().setServiceNotificationVisibility(false);
            mDisableRegistrationStatus = true;
        }

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNM.cancel(INCALL_NOTIF_ID); // in case of crash the icon is not removed
        Compatibility.createNotificationChannels(this);

        Intent notifIntent = new Intent(this, incomingReceivedActivity);
        notifIntent.putExtra("Notification", true);
        mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        } catch (Exception e) {
        }
        mNotif = Compatibility.createNotification(this, mNotificationTitle, "", R.drawable.linphone_notification_icon, R.mipmap.ic_launcher, bm, mNotifContentIntent, true, notifcationsPriority);

        incomingReceivedActivityName = LinphonePreferences.instance().getActivityToLaunchOnIncomingReceived();
        try {
            incomingReceivedActivity = (Class<? extends Activity>) Class.forName(incomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }

        try {
            mStartForeground = getClass().getMethod("startForeground", mStartFgSign);
            mStopForeground = getClass().getMethod("stopForeground", mStopFgSign);
        } catch (NoSuchMethodException e) {
            Log.e(e, "Couldn't find startForeground or stopForeground");
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

    private enum IncallIconState {INCALL, PAUSE, VIDEO, IDLE}

    private IncallIconState mCurrentIncallIconState = IncallIconState.IDLE;

    private synchronized void setIncallIcon(IncallIconState state) {
        if (state == mCurrentIncallIconState) return;
        mCurrentIncallIconState = state;

        int notificationTextId = 0;
        int inconId = 0;

        switch (state) {
            case IDLE:
                if (!displayServiceNotification()) {
                    stopForegroundCompat(INCALL_NOTIF_ID);
                } else {
                    mNM.cancel(INCALL_NOTIF_ID);
                }
                return;
            case INCALL:
                inconId = R.drawable.topbar_call_notification;
                notificationTextId = R.string.incall_notif_active;
                break;
            case PAUSE:
                inconId = R.drawable.topbar_call_notification;
                notificationTextId = R.string.incall_notif_paused;
                break;
            case VIDEO:
                inconId = R.drawable.topbar_videocall_notification;
                notificationTextId = R.string.incall_notif_video;
                break;
            default:
                throw new IllegalArgumentException("Unknown state " + state);
        }

        if (LinphoneManager.getLc().getCallsNb() == 0) {
            return;
        }

        Call call = LinphoneManager.getLc().getCalls()[0];
        Address address = call.getRemoteAddress();

        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        Uri pictureUri = contact != null ? contact.getPhotoUri() : null;
        Bitmap bm = null;
        try {
            bm = MediaStore.Images.Media.getBitmap(getContentResolver(), pictureUri);
        } catch (Exception e) {
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.avatar);
        }
        String name = address.getDisplayName() == null ? address.getUsername() : address.getDisplayName();
        Intent notifIntent = new Intent(this, incomingReceivedActivity);
        notifIntent.putExtra("Notification", true);
        mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mIncallNotif = Compatibility.createInCallNotification(getApplicationContext(), mNotificationTitle, getString(notificationTextId), inconId, bm, name, mNotifContentIntent);

        if (!displayServiceNotification()) {
            startForegroundCompat(INCALL_NOTIF_ID, mIncallNotif);
        } else {
            notifyWrapper(INCALL_NOTIF_ID, mIncallNotif);
        }
    }

    public void refreshIncallIcon(Call currentCall) {
        Core lc = LinphoneManager.getLc();
        if (currentCall != null) {
            if (currentCall.getCurrentParams().videoEnabled() && currentCall.cameraEnabled()) {
                // checking first current params is mandatory
                setIncallIcon(IncallIconState.VIDEO);
            } else {
                setIncallIcon(IncallIconState.INCALL);
            }
        } else if (lc.getCallsNb() == 0) {
            setIncallIcon(IncallIconState.IDLE);
        } else if (lc.getConference() != null) {
            setIncallIcon(IncallIconState.INCALL);
        } else {
            setIncallIcon(IncallIconState.PAUSE);
        }
    }

    @Deprecated
    public void addNotification(Intent onClickIntent, int iconResourceID, String title, String message) {
        addCustomNotification(onClickIntent, iconResourceID, title, message, true);
    }

    public void addCustomNotification(Intent onClickIntent, int iconResourceID, String title, String message, boolean isOngoingEvent) {
        PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        } catch (Exception e) {
        }
        mCustomNotif = Compatibility.createNotification(this, title, message, iconResourceID, 0, bm, notifContentIntent, isOngoingEvent, notifcationsPriority);

        mCustomNotif.defaults |= Notification.DEFAULT_VIBRATE;
        mCustomNotif.defaults |= Notification.DEFAULT_SOUND;
        mCustomNotif.defaults |= Notification.DEFAULT_LIGHTS;

        notifyWrapper(CUSTOM_NOTIF_ID, mCustomNotif);
    }

    public void displayGroupChatMessageNotification(String subject, String conferenceAddress, String fromName, Uri fromPictureUri, String message, String localIdentity) {
        Intent notifIntent = new Intent(this, LinphoneActivity.class);
        notifIntent.putExtra("GoToChat", true);
        notifIntent.putExtra("ChatContactSipUri", conferenceAddress);
        notifIntent.putExtra("LocalIdentity", localIdentity);

        PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notified notif = mChatNotifMap.get(conferenceAddress);
        if (notif != null) {
            notif.numberOfUnreadMessage += 1;
        } else {
            notif = new Notified();
            notif.numberOfUnreadMessage = 1;
            notif.notificationId = mLastNotificationId;
            mLastNotificationId += 1;
            mChatNotifMap.put(conferenceAddress, notif);
        }

        Bitmap bm = null;
        if (fromPictureUri != null) {
            try {
                bm = MediaStore.Images.Media.getBitmap(getContentResolver(), fromPictureUri);
            } catch (Exception e) {
                bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
            }
        } else {
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
        }
        Notification notification = Compatibility.createMessageNotification(getApplicationContext(), notif.numberOfUnreadMessage, subject,
                getString(R.string.group_chat_notif).replace("%1", fromName).replace("%2", message), bm, notifContentIntent);

        notifyWrapper(notif.notificationId, notification);
    }

    public void displayMessageNotification(String fromSipUri, String fromName, Uri fromPictureUri, String message, String localIdentity) {
        Intent notifIntent = new Intent(this, LinphoneActivity.class);
        notifIntent.putExtra("GoToChat", true);
        notifIntent.putExtra("ChatContactSipUri", fromSipUri);
        notifIntent.putExtra("LocalIdentity", localIdentity);
        PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (fromName == null) {
            fromName = fromSipUri;
        }

        Notified notif = mChatNotifMap.get(fromSipUri);
        if (notif != null) {
            notif.numberOfUnreadMessage += 1;
        } else {
            notif = new Notified();
            notif.numberOfUnreadMessage = 1;
            notif.notificationId = mLastNotificationId;
            mLastNotificationId += 1;
            mChatNotifMap.put(fromSipUri, notif);
        }

        Bitmap bm = null;
        if (fromPictureUri != null) {
            try {
                bm = MediaStore.Images.Media.getBitmap(getContentResolver(), fromPictureUri);
            } catch (Exception e) {
                bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
            }
        } else {
            bm = BitmapFactory.decodeResource(getResources(), R.drawable.topbar_avatar);
        }
        Notification notification = Compatibility.createMessageNotification(getApplicationContext(), notif.numberOfUnreadMessage, fromName, message, bm, notifContentIntent);

        notifyWrapper(notif.notificationId, notification);
    }

    public void displayInappNotification(String message) {
        Intent notifIntent = new Intent(this, LinphoneActivity.class);
        notifIntent.putExtra("GoToInapp", true);

        PendingIntent notifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mNotif = Compatibility.createSimpleNotification(getApplicationContext(), getString(R.string.inapp_notification_title), message, notifContentIntent);

        notifyWrapper(NOTIF_ID, mNotif);
    }

    public void displaySasNotification(String sas) {
        mSasNotif = Compatibility.createSimpleNotification(getApplicationContext(),
                getString(R.string.zrtp_notification_title),
                sas + " " + getString(R.string.zrtp_notification_message),
                null);

        notifyWrapper(SAS_NOTIF_ID, mSasNotif);
    }

    public void removeSasNotification() {
        mNM.cancel(SAS_NOTIF_ID);
    }

    private static final Class<?>[] mSetFgSign = new Class[]{boolean.class};
    private static final Class<?>[] mStartFgSign = new Class[]{
            int.class, Notification.class};
    private static final Class<?>[] mStopFgSign = new Class[]{boolean.class};

    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];
    private String incomingReceivedActivityName;
    private Class<? extends Activity> incomingReceivedActivity = LinphoneActivity.class;

    void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // Should not happen.
            Log.w(e, "Unable to invoke method");
        } catch (IllegalAccessException e) {
            // Should not happen.
            Log.w(e, "Unable to invoke method");
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }

        notifyWrapper(id, notification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mStopForeground, mStopForegroundArgs);
            return;
        }

        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
    }

    private void dumpDeviceInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE=").append(Build.DEVICE).append("\n");
        sb.append("MODEL=").append(Build.MODEL).append("\n");
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Supported ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi + ", ");
        }
        sb.append("\n");
        Log.i(sb.toString());
    }

    private void dumpInstalledLinphoneInformation() {
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException nnfe) {
        }

        if (info != null) {
            Log.i("Linphone version is ", info.versionName + " (" + info.versionCode + ")");
        } else {
            Log.i("Linphone version is unknown");
        }
    }

    private synchronized void sendNotification(int level, int textId) {
        String text = getString(textId);
        if (text.contains("%s") && LinphoneManager.getLc() != null) {
            // Test for null lc is to avoid a NPE when Android mess up badly with the String resources.
            ProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
            String id = lpc != null ? lpc.getIdentityAddress().asString() : "";
            text = String.format(text, id);
        }

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        } catch (Exception e) {
        }
        mNotif = Compatibility.createNotification(this, mNotificationTitle, text, R.drawable.status_level, 0, bm, mNotifContentIntent, true, notifcationsPriority);
        notifyWrapper(NOTIF_ID, mNotif);
    }

    /**
     * Wrap notifier to avoid setting the linphone icons while the service
     * is stopping. When the (rare) bug is triggered, the linphone icon is
     * present despite the service is not running. To trigger it one could
     * stop linphone as soon as it is started. Transport configured with TLS.
     */
    private synchronized void notifyWrapper(int id, Notification notification) {
        if (instance != null && notification != null) {
            mNM.notify(id, notification);
        } else {
            Log.i("Service not ready, discarding notification");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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
        if (activityCallbacks != null) {
            getApplication().unregisterActivityLifecycleCallbacks(activityCallbacks);
            activityCallbacks = null;
        }

        destroyOverlay();
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        instance = null;
        LinphoneManager.destroy();

        // Make sure our notification is gone.
        stopForegroundCompat(NOTIF_ID);
        mNM.cancel(INCALL_NOTIF_ID);
        for (Notified notif : mChatNotifMap.values()) {
            mNM.cancel(notif.notificationId);
        }


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
            incomingReceivedActivity = (Class<? extends Activity>) Class.forName(activityName);
            incomingReceivedActivityName = activityName;
            LinphonePreferences.instance().setActivityToLaunchOnIncomingReceived(incomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }
    }

    protected void onIncomingReceived() {
        //wakeup linphone
        startActivity(new Intent()
                .setClass(this, incomingReceivedActivity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}

