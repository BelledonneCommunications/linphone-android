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
package org.linphone;

import static android.content.Intent.ACTION_MAIN;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.ContactsContract;
import java.util.ArrayList;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.Call;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.GlobalState;
import org.linphone.core.LogLevel;
import org.linphone.core.LoggingService;
import org.linphone.core.LoggingServiceListener;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.notifications.NotificationsManager;
import org.linphone.service.LinphoneService;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.PushNotificationUtils;

public class LinphoneContext {
    private static LinphoneContext sInstance = null;

    private Context mContext;

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
    private final ArrayList<CoreStartedListener> mCoreStartedListeners;

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinphoneContext instance() {
        return sInstance;
    }

    public LinphoneContext(Context context) {
        mContext = context;
        mCoreStartedListeners = new ArrayList<>();

        LinphonePreferences.instance().setContext(context);
        Factory.instance().setLogCollectionPath(context.getFilesDir().getAbsolutePath());
        boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
        LinphoneUtils.configureLoggingService(isDebugEnabled, context.getString(R.string.app_name));

        // Dump some debugging information to the logs
        dumpDeviceInformation();
        dumpLinphoneInformation();

        sInstance = this;
        Log.i("[Context] Ready");

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onGlobalStateChanged(Core core, GlobalState state, String message) {
                        Log.i("[Context] Global state is [", state, "]");

                        if (state == GlobalState.On) {
                            for (CoreStartedListener listener : mCoreStartedListeners) {
                                listener.onCoreStarted();
                            }
                        }
                    }

                    @Override
                    public void onConfiguringStatus(
                            Core core, ConfiguringState status, String message) {
                        Log.i("[Context] Configuring state is [", status, "]");

                        if (status == ConfiguringState.Successful) {
                            LinphonePreferences.instance()
                                    .setPushNotificationEnabled(
                                            LinphonePreferences.instance()
                                                    .isPushNotificationEnabled());
                        }
                    }

                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        Log.i("[Context] Call state is [", state, "]");

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

                            // In case of push notification Service won't be started until here
                            if (!LinphoneService.isReady()) {
                                Log.i("[Context] Service not running, starting it");
                                Intent intent = new Intent(ACTION_MAIN);
                                intent.setClass(mContext, LinphoneService.class);
                                mContext.startService(intent);
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

        if (DeviceUtils.isAppUserRestricted(mContext)) {
            // See https://firebase.google.com/docs/cloud-messaging/android/receive#restricted
            Log.w(
                    "[Context] Device has been restricted by user (Android 9+), push notifications won't work !");
        }

        int bucket = DeviceUtils.getAppStandbyBucket(mContext);
        if (bucket > 0) {
            Log.w(
                    "[Context] Device is in bucket "
                            + Compatibility.getAppStandbyBucketNameFromValue(bucket));
        }

        if (!PushNotificationUtils.isAvailable(mContext)) {
            Log.w("[Context] Push notifications won't work !");
        }
    }

    public void start(boolean isPush) {
        Log.i("[Context] Starting, push status is ", isPush);
        mLinphoneManager.startLibLinphone(isPush, mListener);

        mNotificationManager.onCoreReady();

        mContactsManager = new ContactsManager(mContext);
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
        Log.i("[Context] Destroying");
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
            core = null; // To allow the gc calls below to free the Core
        }

        // Make sure our notification is gone.
        if (mNotificationManager != null) {
            mNotificationManager.destroy();
        }

        if (mContactsManager != null) {
            mContactsManager.destroy();
        }

        // Destroy the LinphoneManager second to last to ensure any getCore() call will work
        if (mLinphoneManager != null) {
            mLinphoneManager.destroy();
        }

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

    public Context getApplicationContext() {
        return mContext;
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

    public void addCoreStartedListener(CoreStartedListener listener) {
        mCoreStartedListeners.add(listener);
    }

    public void removeCoreStartedListener(CoreStartedListener listener) {
        mCoreStartedListeners.remove(listener);
    }

    /* Log device related information */

    private void dumpDeviceInformation() {
        Log.i("==== Phone information dump ====");
        Log.i("DISPLAY NAME=" + Compatibility.getDeviceName(mContext));
        Log.i("DEVICE=" + Build.DEVICE);
        Log.i("MODEL=" + Build.MODEL);
        Log.i("MANUFACTURER=" + Build.MANUFACTURER);
        Log.i("ANDROID SDK=" + Build.VERSION.SDK_INT);
        StringBuilder sb = new StringBuilder();
        sb.append("ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi).append(", ");
        }
        Log.i(sb.substring(0, sb.length() - 2));
    }

    private void dumpLinphoneInformation() {
        Log.i("==== Linphone information dump ====");
        Log.i("VERSION NAME=" + org.linphone.BuildConfig.VERSION_NAME);
        Log.i("VERSION CODE=" + org.linphone.BuildConfig.VERSION_CODE);
        Log.i("PACKAGE=" + org.linphone.BuildConfig.APPLICATION_ID);
        Log.i("BUILD TYPE=" + org.linphone.BuildConfig.BUILD_TYPE);
        Log.i("SDK VERSION=" + mContext.getString(R.string.linphone_sdk_version));
        Log.i("SDK BRANCH=" + mContext.getString(R.string.linphone_sdk_branch));
    }

    /* Call activities */

    private void onIncomingReceived() {
        Intent intent = new Intent(mContext, CallIncomingActivity.class);
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

    public interface CoreStartedListener {
        void onCoreStarted();
    }
}
