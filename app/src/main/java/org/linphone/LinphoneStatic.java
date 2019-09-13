package org.linphone;

/*
LinphoneStatic.java
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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.provider.ContactsContract;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.LogLevel;
import org.linphone.core.LoggingService;
import org.linphone.core.LoggingServiceListener;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.notifications.NotificationsManager;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class LinphoneStatic {
    private static final String START_LINPHONE_LOGS = " ==== Phone information dump ====";
    private static LinphoneStatic sInstance = null;

    private Context mContext;
    public final Handler handler = new Handler();

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

    private CoreListenerStub mListener;
    private NotificationsManager mNotificationManager;
    private LinphoneManager mLinphoneManager;
    private ContactsManager mContactsManager;

    private Class<? extends Activity> mIncomingReceivedActivity = CallIncomingActivity.class;

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinphoneStatic instance() {
        return sInstance;
    }

    public LinphoneStatic(Context context) {
        mContext = context;

        LinphonePreferences.instance().setContext(context);
        Factory.instance().setLogCollectionPath(context.getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneUtils.configureLoggingService(isDebugEnabled, context.getString(R.string.app_name));

        // Dump some debugging information to the logs
        Log.i(START_LINPHONE_LOGS);
        dumpDeviceInformation();
        dumpInstalledLinphoneInformation();

        String incomingReceivedActivityName =
                LinphonePreferences.instance().getActivityToLaunchOnIncomingReceived();
        try {
            mIncomingReceivedActivity =
                    (Class<? extends Activity>) Class.forName(incomingReceivedActivityName);
        } catch (ClassNotFoundException e) {
            Log.e(e);
        }

        sInstance = this;

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (sInstance == null) {
                            Log.i(
                                    "[Service] Service not ready, discarding call state change to ",
                                    state.toString());
                            return;
                        }

                        if (mContext.getResources().getBoolean(R.bool.enable_call_notification)) {
                            mNotificationManager.displayCallNotification(call);
                        }

                        if (state == Call.State.IncomingReceived
                                || state == Call.State.IncomingEarlyMedia) {
                            // Starting SDK 24 (Android 7.0) we rely on the fullscreen intent of the
                            // call incoming notification
                            if (Version.sdkStrictlyBelow(Version.API24_NOUGAT_70)) {
                                if (!mLinphoneManager.getCallGsmON()) onIncomingReceived();
                            }
                        } else if (state == Call.State.OutgoingInit) {
                            onOutgoingStarted();
                        } else if (state == Call.State.Connected) {
                            onCallStarted();
                        } else if (state == Call.State.End
                                || state == Call.State.Released
                                || state == Call.State.Error) {
                            if (LinphoneService.isReady()) {
                                LinphoneService.instance().destroyOverlay();
                            }

                            if (state == Call.State.Released
                                    && call.getCallLog().getStatus() == Call.Status.Missed) {
                                mNotificationManager.displayMissedCallNotification(call);
                            }
                        }
                    }
                };

        mLinphoneManager = new LinphoneManager(context);
        mNotificationManager = new NotificationsManager(context);
    }

    public void start(boolean isPush) {
        mLinphoneManager.startLibLinphone(isPush);
        LinphoneManager.getCore().addListener(mListener);

        mNotificationManager.onCoreReady();

        mContactsManager = new ContactsManager(mContext, handler);
        if (!Version.sdkAboveOrEqual(Version.API26_O_80)
                || (mContactsManager.hasReadContactsAccess())) {
            mContext.getContentResolver()
                    .registerContentObserver(
                            ContactsContract.Contacts.CONTENT_URI, true, mContactsManager);
        }
        if (mContactsManager.hasReadContactsAccess()) {
            mContactsManager.enableContactsAccess();
        }
        mContactsManager.initializeContactManager();
    }

    public void destroy() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
            core = null; // To allow the gc calls below to free the Core
        }

        // Make sure our notification is gone.
        if (mNotificationManager != null) {
            mNotificationManager.destroy();
        }
        mContactsManager.destroy();

        // Destroy the LinphoneManager second to last to ensure any getCore() call will work
        mLinphoneManager.destroy();

        // Wait for every other object to be destroyed to make LinphoneService.instance() invalid
        sInstance = null;

        if (LinphonePreferences.instance().useJavaLogger()) {
            Factory.instance().getLoggingService().removeListener(mJavaLoggingService);
        }
        LinphonePreferences.instance().destroy();
    }

    public void updateContext(Context context) {
        mContext = context;
    }

    /* Managers accessors */

    public LoggingServiceListener getJavaLoggingService() {
        return mJavaLoggingService;
    }

    public NotificationsManager getNotificationManager() {
        return mNotificationManager;
    }

    public LinphoneManager getLinphoneManager() {
        return mLinphoneManager;
    }

    public ContactsManager getContactsManager() {
        return mContactsManager;
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
            info = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException nnfe) {
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
        Intent intent = new Intent().setClass(mContext, mIncomingReceivedActivity);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void onOutgoingStarted() {
        Intent intent = new Intent(mContext, CallOutgoingActivity.class);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void onCallStarted() {
        Intent intent = new Intent(mContext, CallActivity.class);
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
