package org.linphone;

/*
LinphoneManager.java
Copyright (C) 2018 Belledonne Communications, Grenoble, France

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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import org.linphone.assistant.PhoneAccountLinkingAssistantActivity;
import org.linphone.call.CallActivity;
import org.linphone.call.CallManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallParams;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.PresenceActivity;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.Reason;
import org.linphone.core.Tunnel;
import org.linphone.core.TunnelConfig;
import org.linphone.core.VersionUpdateCheckResult;
import org.linphone.core.tools.H264Helper;
import org.linphone.core.tools.Log;
import org.linphone.core.tools.OpenH264DownloadHelper;
import org.linphone.core.tools.OpenH264DownloadHelperListener;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;
import org.linphone.receivers.BluetoothManager;
import org.linphone.receivers.HookReceiver;
import org.linphone.receivers.OutgoingCallReceiver;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.AndroidAudioManager;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.MediaScanner;
import org.linphone.utils.PushNotificationUtils;
import org.linphone.views.AddressType;

/**
 * Manager of the low level LibLinphone stuff.<br>
 * Including:
 *
 * <ul>
 *   <li>Starting C liblinphone
 *   <li>Reacting to C liblinphone state changes
 *   <li>Calling Linphone android service listener methods
 *   <li>Interacting from Android GUI/service with low level SIP stuff/
 * </ul>
 *
 * <p>Add Service Listener to react to Linphone state changes.
 */
public class LinphoneManager implements SensorEventListener {
    private static LinphoneManager sInstance;
    private static boolean sExited;

    public final String configFile;

    /** Called when the activity is first created. */
    private final String mLPConfigXsd;

    private final String mLinphoneFactoryConfigFile;
    private final String mLinphoneDynamicConfigFile, mDefaultDynamicConfigFile;
    private final String mRingSoundFile;
    private final String mCallLogDatabaseFile;
    private final String mFriendsDatabaseFile;
    private final String mUserCertsPath;
    private final Context mServiceContext;
    private final PowerManager mPowerManager;
    private final Resources mRessources;
    private final LinphonePreferences mPrefs;
    private Core mCore;
    private CoreListenerStub mCoreListener;
    private OpenH264DownloadHelper mCodecDownloader;
    private OpenH264DownloadHelperListener mCodecListener;
    private final String mBasePath;
    private boolean mCallGsmON;
    private final ConnectivityManager mConnectivityManager;
    private BroadcastReceiver mHookReceiver;
    private BroadcastReceiver mCallReceiver;
    private final Handler mHandler = new Handler();
    private WakeLock mProximityWakelock;
    private AccountCreator mAccountCreator;
    private AccountCreatorListenerStub mAccountCreatorListener;
    private final SensorManager mSensorManager;
    private final Sensor mProximity;
    private boolean mProximitySensingEnabled;
    private boolean mHandsetON = false;
    private Timer mTimer;
    private final MediaScanner mMediaScanner;

    private CallActivity.CallActivityInterface mCallInterface;
    private AndroidAudioManager mAudioManager;

    private LinphoneManager(Context c) {
        sExited = false;
        mServiceContext = c;
        mBasePath = c.getFilesDir().getAbsolutePath();
        mLPConfigXsd = mBasePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = mBasePath + "/linphonerc";
        configFile = mBasePath + "/.linphonerc";
        mLinphoneDynamicConfigFile = mBasePath + "/linphone_assistant_create.rc";
        mDefaultDynamicConfigFile = mBasePath + "/default_assistant_create.rc";
        mCallLogDatabaseFile = mBasePath + "/linphone-log-history.db";
        mFriendsDatabaseFile = mBasePath + "/linphone-friends.db";
        mRingSoundFile = mBasePath + "/share/sounds/linphone/rings/notes_of_the_optimistic.mkv";
        mUserCertsPath = mBasePath + "/user-certs";

        mPrefs = LinphonePreferences.instance();
        mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        mConnectivityManager =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mRessources = c.getResources();

        File f = new File(mUserCertsPath);
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e("[Manager] " + mUserCertsPath + " can't be created.");
            }
        }

        mMediaScanner = new MediaScanner(c);

        mCoreListener =
                new CoreListenerStub() {
                    @Override
                    public void onGlobalStateChanged(
                            final Core lc, final GlobalState state, final String message) {
                        Log.i("New global state [", state, "]");
                        if (state == GlobalState.On) {
                            try {
                                initLiblinphone(lc);
                            } catch (IllegalArgumentException iae) {
                                Log.e(
                                        "[Manager] Global State Changed Illegal Argument Exception: "
                                                + iae);
                            }
                        }
                    }

                    @Override
                    public void onConfiguringStatus(
                            Core lc, ConfiguringState state, String message) {
                        Log.d(
                                "[Manager] Remote provisioning status = "
                                        + state.toString()
                                        + " ("
                                        + message
                                        + ")");

                        LinphonePreferences prefs = LinphonePreferences.instance();
                        if (state == ConfiguringState.Successful) {
                            prefs.setPushNotificationEnabled(prefs.isPushNotificationEnabled());
                        }
                    }

                    @SuppressLint("Wakelock")
                    @Override
                    public void onCallStateChanged(
                            final Core lc,
                            final Call call,
                            final State state,
                            final String message) {
                        Log.i("[Manager] New call state [", state, "]");
                        if (state == State.IncomingReceived && !call.equals(lc.getCurrentCall())) {
                            if (call.getReplacedCall() != null) {
                                // attended transfer
                                // it will be accepted automatically.
                                return;
                            }
                        }

                        if ((state == State.IncomingReceived || state == State.IncomingEarlyMedia)
                                && getCallGsmON()) {
                            if (mCore != null) {
                                mCore.declineCall(call, Reason.Busy);
                            }
                        } else if (state == State.IncomingReceived
                                && (LinphonePreferences.instance().isAutoAnswerEnabled())
                                && !getCallGsmON()) {
                            TimerTask lTask =
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            if (mCore != null) {
                                                if (mCore.getCallsNb() > 0) {
                                                    acceptCall(call);
                                                    mAudioManager.routeAudioToEarPiece();
                                                }
                                            }
                                        }
                                    };
                            mTimer = new Timer("Auto answer");
                            mTimer.schedule(lTask, mPrefs.getAutoAnswerTime());
                        }

                        if (state == State.End || state == State.Error) {
                            if (mCore.getCallsNb() == 0) {
                                // Disabling proximity sensor
                                enableProximitySensing(false);
                            }
                        }
                        if (state == State.UpdatedByRemote) {
                            // If the correspondent proposes video while audio call
                            boolean remoteVideo = call.getRemoteParams().videoEnabled();
                            boolean localVideo = call.getCurrentParams().videoEnabled();
                            boolean autoAcceptCameraPolicy =
                                    LinphonePreferences.instance()
                                            .shouldAutomaticallyAcceptVideoRequests();
                            if (remoteVideo
                                    && !localVideo
                                    && !autoAcceptCameraPolicy
                                    && LinphoneManager.getLc().getConference() == null) {
                                LinphoneManager.getLc().deferCallUpdate(call);
                            }
                        }
                    }

                    @Override
                    public void onVersionUpdateCheckResultReceived(
                            Core lc, VersionUpdateCheckResult result, String version, String url) {
                        if (result == VersionUpdateCheckResult.NewVersionAvailable) {
                            final String urlToUse = url;
                            final String versionAv = version;
                            mHandler.postDelayed(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            AlertDialog.Builder builder =
                                                    new AlertDialog.Builder(mServiceContext);
                                            builder.setMessage(
                                                    getString(R.string.update_available)
                                                            + ": "
                                                            + versionAv);
                                            builder.setCancelable(false);
                                            builder.setNeutralButton(
                                                    getString(R.string.ok),
                                                    new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialogInterface,
                                                                int i) {
                                                            if (urlToUse != null) {
                                                                Intent urlIntent =
                                                                        new Intent(
                                                                                Intent.ACTION_VIEW);
                                                                urlIntent.setData(
                                                                        Uri.parse(urlToUse));
                                                                mServiceContext.startActivity(
                                                                        urlIntent);
                                                            }
                                                        }
                                                    });
                                            builder.show();
                                        }
                                    },
                                    1000);
                        }
                    }

                    @Override
                    public void onFriendListCreated(Core lc, FriendList list) {
                        if (LinphoneService.isReady()) {
                            list.addListener(ContactsManager.getInstance());
                        }
                    }

                    @Override
                    public void onFriendListRemoved(Core lc, FriendList list) {
                        list.removeListener(ContactsManager.getInstance());
                    }
                };

        mAccountCreatorListener =
                new AccountCreatorListenerStub() {
                    @Override
                    public void onIsAccountExist(
                            AccountCreator accountCreator,
                            AccountCreator.Status status,
                            String resp) {
                        if (status.equals(AccountCreator.Status.AccountExist)) {
                            accountCreator.isAccountLinked();
                        }
                    }

                    @Override
                    public void onLinkAccount(
                            AccountCreator accountCreator,
                            AccountCreator.Status status,
                            String resp) {
                        if (status.equals(AccountCreator.Status.AccountNotLinked)) {
                            askLinkWithPhoneNumber();
                        }
                    }

                    @Override
                    public void onIsAccountLinked(
                            AccountCreator accountCreator,
                            AccountCreator.Status status,
                            String resp) {
                        if (status.equals(AccountCreator.Status.AccountNotLinked)) {
                            askLinkWithPhoneNumber();
                        }
                    }
                };
    }

    public static synchronized void createAndStart(Context c, boolean isPush) {
        if (sInstance != null) {
            Log.e(
                    "[Manager] Linphone Manager is already initialized ! Destroying it and creating a new one...");
            destroy();
        }

        sInstance = new LinphoneManager(c);
        sInstance.startLibLinphone(c, isPush);
        sInstance.initOpenH264DownloadHelper();

        // H264 codec Management - set to auto mode -> MediaCodec >= android 5.0 >= OpenH264
        H264Helper.setH264Mode(H264Helper.MODE_AUTO, getLc());
    }

    public static synchronized LinphoneManager getInstance() {
        if (sInstance != null) return sInstance;

        if (sExited) {
            throw new RuntimeException(
                    "[Manager] Linphone Manager was already destroyed. "
                            + "Better use getLcIfManagerNotDestroyedOrNull and check returned value");
        }

        throw new RuntimeException("[Manager] Linphone Manager should be created before accessed");
    }

    public static synchronized Core getLc() {
        return getInstance().mCore;
    }

    public static synchronized AndroidAudioManager getAudioManager() {
        return getInstance().mAudioManager;
    }

    private static Boolean isProximitySensorNearby(final SensorEvent event) {
        float threshold = 4.001f; // <= 4 cm is near

        final float distanceInCm = event.values[0];
        final float maxDistance = event.sensor.getMaximumRange();
        Log.d(
                "[Manager] Proximity sensor report ["
                        + distanceInCm
                        + "] , for max range ["
                        + maxDistance
                        + "]");

        if (maxDistance <= threshold) {
            // Case binary 0/1 and short sensors
            threshold = maxDistance;
        }
        return distanceInCm < threshold;
    }

    private static void ContactsManagerDestroy() {
        if (LinphoneManager.sInstance != null && LinphoneManager.sInstance.mServiceContext != null)
            LinphoneManager.sInstance
                    .mServiceContext
                    .getContentResolver()
                    .unregisterContentObserver(ContactsManager.getInstance());
        ContactsManager.getInstance().destroy();
    }

    private static void BluetoothManagerDestroy() {
        BluetoothManager.getInstance().destroy();
    }

    public static synchronized void destroy() {
        if (sInstance == null) return;
        sInstance.changeStatusToOffline();
        sInstance.mMediaScanner.destroy();
        sInstance.mAudioManager.destroy();
        sExited = true;
        sInstance.destroyCore();
        sInstance = null;
    }

    public static synchronized Core getLcIfManagerNotDestroyedOrNull() {
        if (sExited || sInstance == null) {
            // Can occur if the UI thread play a posted event but in the meantime the
            // LinphoneManager was destroyed
            // Ex: stop call and quickly terminate application.
            return null;
        }
        return getLc();
    }

    public static boolean isInstanciated() {
        return sInstance != null;
    }

    private void initOpenH264DownloadHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.i("[Manager] Android >= 5.1 we disable the download of OpenH264");
            OpenH264DownloadHelper.setOpenH264DownloadEnabled(false);
            return;
        }

        mCodecDownloader = Factory.instance().createOpenH264DownloadHelper(mServiceContext);
        mCodecListener =
                new OpenH264DownloadHelperListener() {
                    ProgressDialog progress;
                    final int ctxt = 0;

                    @Override
                    public void OnProgress(final int current, final int max) {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        OpenH264DownloadHelper ohcodec =
                                                LinphoneManager.getInstance()
                                                        .getOpenH264DownloadHelper();
                                        if (progress == null) {
                                            progress =
                                                    new ProgressDialog(
                                                            (Context) ohcodec.getUserData(ctxt));
                                            progress.setCanceledOnTouchOutside(false);
                                            progress.setCancelable(false);
                                            progress.setProgressStyle(
                                                    ProgressDialog.STYLE_HORIZONTAL);
                                        } else if (current <= max) {
                                            progress.setMessage(
                                                    getString(
                                                            R.string
                                                                    .assistant_openh264_downloading));
                                            progress.setMax(max);
                                            progress.setProgress(current);
                                            progress.show();
                                        } else {
                                            progress.dismiss();
                                            progress = null;
                                            /*if (Build.VERSION.SDK_INT
                                                    >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                                LinphoneManager.getLc()
                                                        .reloadMsPlugins(
                                                                AssistantActivity.instance()
                                                                        .getApplicationInfo()
                                                                        .nativeLibraryDir);
                                                AssistantActivity.instance().endDownloadCodec();
                                            } else {
                                                // We need to restart due to bad android linker
                                                AssistantActivity.instance().restartApplication();
                                            }*/
                                        }
                                    }
                                });
                    }

                    @Override
                    public void OnError(final String error) {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (progress != null) progress.dismiss();
                                        AlertDialog.Builder builder =
                                                new AlertDialog.Builder(
                                                        (Context)
                                                                LinphoneManager.getInstance()
                                                                        .getOpenH264DownloadHelper()
                                                                        .getUserData(ctxt));
                                        builder.setMessage(
                                                getString(R.string.assistant_openh264_error));
                                        builder.setCancelable(false);
                                        builder.setNeutralButton(getString(R.string.ok), null);
                                        builder.show();
                                    }
                                });
                    }
                };
        mCodecDownloader.setOpenH264HelperListener(mCodecListener);
    }

    public OpenH264DownloadHelperListener getOpenH264HelperListener() {
        return mCodecListener;
    }

    public OpenH264DownloadHelper getOpenH264DownloadHelper() {
        return mCodecDownloader;
    }

    private boolean isPresenceModelActivitySet() {
        Core lc = getLcIfManagerNotDestroyedOrNull();
        if (isInstanciated() && lc != null) {
            return lc.getPresenceModel() != null && lc.getPresenceModel().getActivity() != null;
        }
        return false;
    }

    public void changeStatusToOnline() {
        Core lc = getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;
        PresenceModel model = lc.createPresenceModel();
        model.setBasicStatus(PresenceBasicStatus.Open);
        lc.setPresenceModel(model);
    }

    public void changeStatusToOnThePhone() {
        Core lc = getLcIfManagerNotDestroyedOrNull();
        if (lc == null) return;

        if (isInstanciated()
                && isPresenceModelActivitySet()
                && lc.getPresenceModel().getActivity().getType()
                        != PresenceActivity.Type.OnThePhone) {
            lc.getPresenceModel().getActivity().setType(PresenceActivity.Type.OnThePhone);
        } else if (isInstanciated() && !isPresenceModelActivitySet()) {
            PresenceModel model =
                    lc.createPresenceModelWithActivity(PresenceActivity.Type.OnThePhone, null);
            lc.setPresenceModel(model);
        }
    }

    private void changeStatusToOffline() {
        Core lc = getLcIfManagerNotDestroyedOrNull();
        if (isInstanciated() && lc != null) {
            PresenceModel model = lc.getPresenceModel();
            model.setBasicStatus(PresenceBasicStatus.Closed);
            lc.setPresenceModel(model);
        }
    }

    public void newOutgoingCall(AddressType address) {
        String to = address.getText().toString();
        newOutgoingCall(to, address.getDisplayedName());
    }

    public void newOutgoingCall(String to, String displayName) {
        //		if (mCore.inCall()) {
        //			listenerDispatcher.tryingNewOutgoingCallButAlreadyInCall();
        //			return;
        //		}
        if (to == null) return;

        // If to is only a username, try to find the contact to get an alias if existing
        if (!to.startsWith("sip:") || !to.contains("@")) {
            LinphoneContact contact = ContactsManager.getInstance().findContactFromPhoneNumber(to);
            if (contact != null) {
                String alias = contact.getContactFromPresenceModelForUriOrTel(to);
                if (alias != null) {
                    to = alias;
                }
            }
        }

        Address lAddress;
        lAddress = mCore.interpretUrl(to); // InterpretUrl does normalizePhoneNumber
        if (lAddress == null) {
            Log.e("[Manager] Couldn't convert to String to Address : " + to);
            return;
        }

        ProxyConfig lpc = mCore.getDefaultProxyConfig();
        if (mRessources.getBoolean(R.bool.forbid_self_call)
                && lpc != null
                && lAddress.weakEqual(lpc.getIdentityAddress())) {
            return;
        }
        lAddress.setDisplayName(displayName);

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneService.instance().getApplicationContext());

        if (mCore.isNetworkReachable()) {
            if (Version.isVideoCapable()) {
                boolean prefVideoEnable = mPrefs.isVideoEnabled();
                boolean prefInitiateWithVideo = mPrefs.shouldInitiateVideoCall();
                CallManager.getInstance()
                        .inviteAddress(
                                lAddress,
                                prefVideoEnable && prefInitiateWithVideo,
                                isLowBandwidthConnection);
            } else {
                CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection);
            }
        } else {
            Toast.makeText(
                            mServiceContext,
                            getString(R.string.error_network_unreachable),
                            Toast.LENGTH_LONG)
                    .show();
            Log.e("[Manager] Error: " + getString(R.string.error_network_unreachable));
        }
    }

    private void resetCameraFromPreferences() {
        boolean useFrontCam = mPrefs.useFrontCam();
        int camId = 0;
        AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing == useFrontCam) {
                camId = androidCamera.id;
                break;
            }
        }
        String[] devices = getLc().getVideoDevicesList();
        if (camId >= devices.length) {
            Log.e(
                    "[Manager] Trying to use a camera id that's higher than the linphone's devices list, using 0 to prevent crash...");
            camId = 0;
        }
        String newDevice = devices[camId];
        LinphoneManager.getLc().setVideoDevice(newDevice);
    }

    private void enableCamera(Call call, boolean enable) {
        if (call != null) {
            call.enableCamera(enable);
            if (mServiceContext.getResources().getBoolean(R.bool.enable_call_notification))
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayCallNotification(mCore.getCurrentCall());
        }
    }

    public void playDtmf(ContentResolver r, char dtmf) {
        try {
            if (Settings.System.getInt(r, Settings.System.DTMF_TONE_WHEN_DIALING) == 0) {
                // audible touch disabled: don't play on speaker, only send in outgoing stream
                return;
            }
        } catch (SettingNotFoundException e) {
            Log.e("[Manager] playDtmf exception: " + e);
        }

        getLc().playDtmf(dtmf, -1);
    }

    private void terminateCall() {
        if (mCore.inCall()) {
            mCore.terminateCall(mCore.getCurrentCall());
        }
    }

    public void initTunnelFromConf() {
        if (!mCore.tunnelAvailable()) return;

        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        Tunnel tunnel = mCore.getTunnel();
        tunnel.cleanServers();
        TunnelConfig config = mPrefs.getTunnelConfig();
        if (config.getHost() != null) {
            tunnel.addServer(config);
            manageTunnelServer(info);
        }
    }

    private boolean isTunnelNeeded(NetworkInfo info) {
        if (info == null) {
            Log.i("[Manager] No connectivity: tunnel should be disabled");
            return false;
        }

        String pref = mPrefs.getTunnelMode();

        if (getString(R.string.tunnel_mode_entry_value_always).equals(pref)) {
            return true;
        }

        if (info.getType() != ConnectivityManager.TYPE_WIFI
                && getString(R.string.tunnel_mode_entry_value_3G_only).equals(pref)) {
            Log.i("[Manager] Need tunnel: 'no wifi' connection");
            return true;
        }

        return false;
    }

    private void manageTunnelServer(NetworkInfo info) {
        if (mCore == null) return;
        if (!mCore.tunnelAvailable()) return;
        Tunnel tunnel = mCore.getTunnel();

        Log.i("[Manager] Managing tunnel");
        if (isTunnelNeeded(info)) {
            Log.i("[Manager] Tunnel need to be activated");
            tunnel.setMode(Tunnel.Mode.Enable);
        } else {
            Log.i("[Manager] Tunnel should not be used");
            String pref = mPrefs.getTunnelMode();
            tunnel.setMode(Tunnel.Mode.Disable);
            if (getString(R.string.tunnel_mode_entry_value_auto).equals(pref)) {
                tunnel.setMode(Tunnel.Mode.Auto);
            }
        }
    }

    private synchronized void destroyCore() {
        Log.w("[Manager] Destroying Core");
        sExited = true;
        ContactsManagerDestroy();
        BluetoothManagerDestroy();
        try {
            mTimer.cancel();
            destroyLinphoneCore();
        } catch (RuntimeException e) {
            Log.e("[Manager] Destroy Core Runtime Exception: " + e);
        } finally {
            try {
                mServiceContext.unregisterReceiver(mHookReceiver);
            } catch (Exception e) {
                Log.e("[Manager] unregister receiver exception: " + e);
            }
            try {
                mServiceContext.unregisterReceiver(mCallReceiver);
            } catch (Exception e) {
                Log.e("[Manager] unregister receiver exception: " + e);
            }
            mCore = null;
        }
    }

    public void restartCore() {
        mCore.stop();
        mCore.start();
    }

    private synchronized void startLibLinphone(Context c, boolean isPush) {
        try {
            copyAssetsFromPackage();
            // traces alway start with traces enable to not missed first initialization
            mCore = Factory.instance().createCore(configFile, mLinphoneFactoryConfigFile, c);
            mCore.addListener(mCoreListener);
            if (isPush) {
                Log.w(
                        "[Manager] We are here because of a received push notification, enter background mode before starting the Core");
                mCore.enterBackground();
            }
            mCore.start();
            TimerTask lTask =
                    new TimerTask() {
                        @Override
                        public void run() {
                            LinphoneUtils.dispatchOnUIThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mCore != null) {
                                                mCore.iterate();
                                            }
                                        }
                                    });
                        }
                    };
            /*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
            mTimer = new Timer("Linphone scheduler");
            mTimer.schedule(lTask, 0, 20);
        } catch (Exception e) {
            Log.e(e, "[Manager] Cannot start linphone");
        }
    }

    private synchronized void initLiblinphone(Core lc) {
        mCore = lc;
        mAudioManager = new AndroidAudioManager(mServiceContext);

        mCore.setZrtpSecretsFile(mBasePath + "/zrtp_secrets");

        String deviceName = mPrefs.getDeviceName(mServiceContext);
        String appName = mServiceContext.getResources().getString(R.string.user_agent);
        String androidVersion = BuildConfig.VERSION_NAME;
        String userAgent = appName + "/" + androidVersion + " (" + deviceName + ") LinphoneSDK";

        mCore.setUserAgent(
                userAgent,
                getString(R.string.linphone_sdk_version)
                        + " ("
                        + getString(R.string.linphone_sdk_branch)
                        + ")");

        // mCore.setChatDatabasePath(mChatDatabaseFile);
        mCore.setCallLogsDatabasePath(mCallLogDatabaseFile);
        mCore.setFriendsDatabasePath(mFriendsDatabaseFile);
        mCore.setUserCertificatesPath(mUserCertsPath);
        // mCore.setCallErrorTone(Reason.NotFound, mErrorToneFile);
        enableDeviceRingtone(mPrefs.isDeviceRingtoneEnabled());

        int availableCores = Runtime.getRuntime().availableProcessors();
        Log.w("[Manager] MediaStreamer : " + availableCores + " cores detected and configured");

        mCore.migrateLogsFromRcToDb();

        // Migrate existing linphone accounts to have conference factory uri and LIME X3Dh url set
        String uri = getString(R.string.default_conference_factory_uri);
        for (ProxyConfig lpc : mCore.getProxyConfigList()) {
            if (lpc.getIdentityAddress().getDomain().equals(getString(R.string.default_domain))) {
                if (lpc.getConferenceFactoryUri() == null) {
                    lpc.edit();
                    Log.i(
                            "[Manager] Setting conference factory on proxy config "
                                    + lpc.getIdentityAddress().asString()
                                    + " to default value: "
                                    + uri);
                    lpc.setConferenceFactoryUri(uri);
                    lpc.done();
                }

                if (mCore.limeX3DhAvailable()) {
                    String url = mCore.getLimeX3DhServerUrl();
                    if (url == null || url.length() == 0) {
                        url = getString(R.string.default_lime_x3dh_server_url);
                        Log.i("[Manager] Setting LIME X3Dh server url to default value: " + url);
                        mCore.setLimeX3DhServerUrl(url);
                    }
                }
            }
        }

        if (mServiceContext.getResources().getBoolean(R.bool.enable_push_id)) {
            PushNotificationUtils.init(mServiceContext);
        }

        IntentFilter mCallIntentFilter =
                new IntentFilter("android.intent.action.ACTION_NEW_OUTGOING_CALL");
        mCallIntentFilter.setPriority(99999999);
        mCallReceiver = new OutgoingCallReceiver();
        try {
            mServiceContext.registerReceiver(mCallReceiver, mCallIntentFilter);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        mProximityWakelock =
                mPowerManager.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        mServiceContext.getPackageName() + ";manager_proximity_sensor");

        IntentFilter mHookIntentFilter = new IntentFilter("com.base.module.phone.HOOKEVENT");
        mHookIntentFilter.setPriority(999);
        mHookReceiver = new HookReceiver();
        mServiceContext.registerReceiver(mHookReceiver, mHookIntentFilter);

        resetCameraFromPreferences();

        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(mAccountCreatorListener);
        mCallGsmON = false;
    }

    public void setCallInterface(CallActivity.CallActivityInterface callInterface) {
        mCallInterface = callInterface;
    }

    public void resetCallControlsHidingTimer() {
        if (mCallInterface != null) {
            mCallInterface.resetCallControlsHidingTimer();
        }
    }

    public void refreshInCallActions() {
        if (mCallInterface != null) {
            mCallInterface.refreshInCallActions();
        }
    }

    public void setHandsetMode(Boolean on) {
        if (mCore.isIncomingInvitePending() && on) {
            mHandsetON = true;
            acceptCall(mCore.getCurrentCall());
        } else if (on && mCallInterface != null) {
            mHandsetON = true;
            mCallInterface.setSpeakerEnabled(true);
            mCallInterface.refreshInCallActions();
        } else if (!on) {
            mHandsetON = false;
            LinphoneManager.getInstance().terminateCall();
        }
    }

    public boolean isHansetModeOn() {
        return mHandsetON;
    }

    private void copyAssetsFromPackage() throws IOException {
        copyIfNotExist(R.raw.linphonerc_default, configFile);
        copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
        copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
        copyFromPackage(
                R.raw.default_assistant_create, new File(mDefaultDynamicConfigFile).getName());
        copyFromPackage(
                R.raw.linphone_assistant_create, new File(mLinphoneDynamicConfigFile).getName());
    }

    private void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    private void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream = mServiceContext.openFileOutput(target, 0);
        InputStream lInputStream = mRessources.openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }

    private void destroyLinphoneCore() {
        if (LinphonePreferences.instance() != null) {
            // We set network reachable at false before destroy LC to not send register with expires
            // at 0
            if (LinphonePreferences.instance().isPushNotificationEnabled()) {
                Log.w(
                        "[Manager] Setting network reachability to False to prevent unregister and allow incoming push notifications");
                mCore.setNetworkReachable(false);
            }
        }
        mCore.stop();
        mCore.removeListener(mCoreListener);
    }

    public void enableProximitySensing(boolean enable) {
        if (enable) {
            if (!mProximitySensingEnabled) {
                mSensorManager.registerListener(
                        this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
                mProximitySensingEnabled = true;
            }
        } else {
            if (mProximitySensingEnabled) {
                mSensorManager.unregisterListener(this);
                mProximitySensingEnabled = false;
                // Don't forgeting to release wakelock if held
                if (mProximityWakelock.isHeld()) {
                    mProximityWakelock.release();
                }
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.timestamp == 0) return;
        if (isProximitySensorNearby(event)) {
            if (!mProximityWakelock.isHeld()) {
                mProximityWakelock.acquire();
            }
        } else {
            if (mProximityWakelock.isHeld()) {
                mProximityWakelock.release();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public MediaScanner getMediaScanner() {
        return mMediaScanner;
    }

    private String getString(int key) {
        return mRessources.getString(key);
    }

    public void checkForUpdate() {
        String url = LinphonePreferences.instance().getCheckReleaseUrl();
        if (url != null && !url.isEmpty()) {
            int lastTimestamp = LinphonePreferences.instance().getLastCheckReleaseTimestamp();
            int currentTimeStamp = (int) System.currentTimeMillis();
            int interval =
                    mServiceContext
                            .getResources()
                            .getInteger(R.integer.time_between_update_check); // 24h
            if (lastTimestamp == 0 || currentTimeStamp - lastTimestamp >= interval) {
                LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                        .checkForUpdate(BuildConfig.VERSION_NAME);
                LinphonePreferences.instance().setLastCheckReleaseTimestamp(currentTimeStamp);
            }
        }
    }

    public void enableDeviceRingtone(boolean use) {
        if (use) {
            mCore.setRing(null);
        } else {
            mCore.setRing(mRingSoundFile);
        }
    }

    /** @return false if already in video call. */
    public boolean addVideo() {
        Call call = mCore.getCurrentCall();
        enableCamera(call, true);
        return CallManager.getInstance().reinviteWithVideo();
    }

    public boolean acceptCall(Call call) {
        if (call == null) return false;

        CallParams params = LinphoneManager.getLc().createCallParams(call);

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneService.instance().getApplicationContext());

        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
            params.setRecordFile(
                    FileUtils.getCallRecordingFilename(mServiceContext, call.getRemoteAddress()));
        } else {
            Log.e("[Manager] Could not create call params for call");
            return false;
        }

        mCore.acceptCallWithParams(call, params);
        return true;
    }

    public void isAccountWithAlias() {
        if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
            long now = new Timestamp(new Date().getTime()).getTime();
            if (mAccountCreator != null && LinphonePreferences.instance().getLinkPopupTime() == null
                    || Long.parseLong(LinphonePreferences.instance().getLinkPopupTime()) < now) {
                mAccountCreator.setUsername(
                        LinphonePreferences.instance()
                                .getAccountUsername(
                                        LinphonePreferences.instance().getDefaultAccountIndex()));
                mAccountCreator.isAccountExist();
            }
        } else {
            LinphonePreferences.instance().setLinkPopupTime(null);
        }
    }

    private void askLinkWithPhoneNumber() {
        if (!LinphonePreferences.instance().isLinkPopupEnabled()) return;

        long now = new Timestamp(new Date().getTime()).getTime();
        if (LinphonePreferences.instance().getLinkPopupTime() != null
                && Long.parseLong(LinphonePreferences.instance().getLinkPopupTime()) >= now) return;

        long future =
                new Timestamp(
                                mServiceContext
                                        .getResources()
                                        .getInteger(
                                                R.integer.phone_number_linking_popup_time_interval))
                        .getTime();
        long newDate = now + future;

        LinphonePreferences.instance().setLinkPopupTime(String.valueOf(newDate));

        final Dialog dialog =
                LinphoneUtils.getDialog(
                        mServiceContext,
                        String.format(
                                getString(R.string.link_account_popup),
                                LinphoneManager.getLc()
                                        .getDefaultProxyConfig()
                                        .getIdentityAddress()
                                        .asStringUriOnly()));
        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setText(getString(R.string.link));
        ok.setVisibility(View.VISIBLE);
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(getString(R.string.maybe_later));

        dialog.findViewById(R.id.dialog_do_not_ask_again_layout).setVisibility(View.VISIBLE);
        final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
        dialog.findViewById(R.id.doNotAskAgainLabel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                            }
                        });

        ok.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent assistant = new Intent();
                        assistant.setClass(
                                mServiceContext, PhoneAccountLinkingAssistantActivity.class);
                        mServiceContext.startActivity(assistant);
                        dialog.dismiss();
                    }
                });

        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableLinkPopup(false);
                        }
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    public String getDefaultDynamicConfigFile() {
        return mDefaultDynamicConfigFile;
    }

    public String getLinphoneDynamicConfigFile() {
        return mLinphoneDynamicConfigFile;
    }

    public boolean getCallGsmON() {
        return mCallGsmON;
    }

    public void setCallGsmON(boolean on) {
        mCallGsmON = on;
    }
}
