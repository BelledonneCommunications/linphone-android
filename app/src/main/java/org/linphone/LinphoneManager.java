package org.linphone;

/*
LinphoneManager.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.linphone.assistant.AssistantActivity;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.CoreListener;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.Event;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.InfoMessage;
import org.linphone.core.PresenceActivity;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.SubscriptionState;
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
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.receivers.BluetoothManager;
import org.linphone.receivers.HookReceiver;
import org.linphone.receivers.OutgoingCallReceiver;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.MediaScanner;
import org.linphone.utils.MediaScannerListener;
import org.linphone.utils.PushNotificationUtils;

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
public class LinphoneManager implements CoreListener, SensorEventListener, AccountCreatorListener {

    private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;

    private static LinphoneManager sInstance;
    private static boolean sExited;

    public final String configFile;
    public String wizardLoginViewDomain = null;

    /** Called when the activity is first created. */
    private final String mLPConfigXsd;

    private final String mLinphoneFactoryConfigFile;
    private final String mLinphoneDynamicConfigFile, mDefaultDynamicConfigFile;
    private final String mChatDatabaseFile;
    private final String mRingSoundFile;
    private final String mCallLogDatabaseFile;
    private final String mFriendsDatabaseFile;
    private final String mUserCertsPath;
    private final Context mServiceContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final Resources mRessources;
    private final LinphonePreferences mPrefs;
    private Core mCore;
    private OpenH264DownloadHelper mCodecDownloader;
    private OpenH264DownloadHelperListener mCodecListener;
    private final String mBasePath;
    private boolean mAudioFocused;
    private boolean mEchoTesterIsRunning;
    private boolean mCallGsmON;
    private final ConnectivityManager mConnectivityManager;
    private BroadcastReceiver mHookReceiver;
    private BroadcastReceiver mCallReceiver;
    private IntentFilter mHookIntentFilter;
    private IntentFilter mCallIntentFilter;
    private final Handler mHandler = new Handler();
    private WakeLock mProximityWakelock;
    private AccountCreator mAccountCreator;
    private final SensorManager mSensorManager;
    private final Sensor mProximity;
    private boolean mProximitySensingEnabled;
    private boolean mHandsetON = false;
    private Address mCurrentChatRoomAddress;
    private Timer mTimer;
    private final Map<String, Integer> mUnreadChatsPerRoom;
    private final MediaScanner mMediaScanner;
    private Call mRingingCall;
    private MediaPlayer mRingerPlayer;
    private final Vibrator mVibrator;
    private boolean mIsRinging;
    private boolean mHasLastCallSasBeenRejected;

    private LinphoneManager(Context c) {
        mUnreadChatsPerRoom = new HashMap();
        sExited = false;
        mEchoTesterIsRunning = false;
        mServiceContext = c;
        mBasePath = c.getFilesDir().getAbsolutePath();
        mLPConfigXsd = mBasePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = mBasePath + "/linphonerc";
        configFile = mBasePath + "/.linphonerc";
        mLinphoneDynamicConfigFile = mBasePath + "/linphone_assistant_create.rc";
        mDefaultDynamicConfigFile = mBasePath + "/default_assistant_create.rc";
        mChatDatabaseFile = mBasePath + "/linphone-history.db";
        mCallLogDatabaseFile = mBasePath + "/linphone-log-history.db";
        mFriendsDatabaseFile = mBasePath + "/linphone-friends.db";
        mRingSoundFile = mBasePath + "/share/sounds/linphone/rings/notes_of_the_optimistic.mkv";
        mUserCertsPath = mBasePath + "/user-certs";

        mPrefs = LinphonePreferences.instance();
        mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        mConnectivityManager =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mRessources = c.getResources();
        mHasLastCallSasBeenRejected = false;

        File f = new File(mUserCertsPath);
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e("[Manager] " + mUserCertsPath + " can't be created.");
            }
        }

        mMediaScanner = new MediaScanner(c);
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
        sExited = true;
        sInstance.destroyCore();
        sInstance = null;
    }

    private static boolean reinviteWithVideo() {
        return CallManager.getInstance().reinviteWithVideo();
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

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        Log.w(
                "[Manager] Routing audio to "
                        + (speakerOn ? "speaker" : "earpiece")
                        + ", disabling bluetooth audio route");
        BluetoothManager.getInstance().disableBluetoothSCO();

        enableSpeaker(speakerOn);
    }

    public boolean isSpeakerEnabled() {
        return mAudioManager != null && mAudioManager.isSpeakerphoneOn();
    }

    public void enableSpeaker(boolean enable) {
        mAudioManager.setSpeakerphoneOn(enable);
    }

    private void initOpenH264DownloadHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.i("[Manager] Android >= 5.1 we disable the download of OpenH264");
            OpenH264DownloadHelper.setOpenH264DownloadEnabled(false);
            return;
        }

        mCodecDownloader = Factory.instance().createOpenH264DownloadHelper(getContext());
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
                                            if (Build.VERSION.SDK_INT
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
                                            }
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

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
    }

    public void routeAudioToReceiver() {
        routeAudioToSpeakerHelper(false);
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

    public void subscribeFriendList(boolean enabled) {
        Core lc = getLcIfManagerNotDestroyedOrNull();
        if (lc != null && lc.getFriendsLists() != null && lc.getFriendsLists().length > 0) {
            FriendList friendList = (lc.getFriendsLists())[0];
            Log.i("[Manager] Presence list subscription is " + (enabled ? "enabled" : "disabled"));
            friendList.enableSubscriptions(enabled);
        }
    }

    public void newOutgoingCall(AddressType address) {
        String to = address.getText().toString();
        newOutgoingCall(to, address.getDisplayedName());
    }

    public void newOutgoingCall(Address to) {
        if (to == null) return;

        ProxyConfig lpc = mCore.getDefaultProxyConfig();
        if (mRessources.getBoolean(R.bool.forbid_self_call)
                && lpc != null
                && to.weakEqual(lpc.getIdentityAddress())) {
            return;
        }

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneService.instance().getApplicationContext());

        if (mCore.isNetworkReachable()) {
            if (Version.isVideoCapable()) {
                boolean prefVideoEnable = mPrefs.isVideoEnabled();
                boolean prefInitiateWithVideo = mPrefs.shouldInitiateVideoCall();
                CallManager.getInstance()
                        .inviteAddress(
                                to,
                                prefVideoEnable && prefInitiateWithVideo,
                                isLowBandwidthConnection);
            } else {
                CallManager.getInstance().inviteAddress(to, false, isLowBandwidthConnection);
            }
        } else if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance()
                    .displayCustomToast(
                            getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
        } else {
            Log.e("[Manager] Error: " + getString(R.string.error_network_unreachable));
        }
    }

    public void newOutgoingCall(String to, String displayName) {
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

        if (displayName != null) lAddress.setDisplayName(displayName);

        newOutgoingCall(lAddress);
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
            mCore.addListener(this);
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

    private void initPushNotificationsService() {
        PushNotificationUtils.init(mServiceContext);
    }

    private synchronized void initLiblinphone(Core lc) {
        mCore = lc;

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
            initPushNotificationsService();
        }

        mCallIntentFilter = new IntentFilter("android.intent.action.ACTION_NEW_OUTGOING_CALL");
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

        mHookIntentFilter = new IntentFilter("com.base.module.phone.HOOKEVENT");
        mHookIntentFilter.setPriority(999);
        mHookReceiver = new HookReceiver();
        mServiceContext.registerReceiver(mHookReceiver, mHookIntentFilter);

        resetCameraFromPreferences();

        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);
        mCallGsmON = false;

        updateMissedChatCount();
    }

    public void setHandsetMode(Boolean on) {
        if (mCore.isIncomingInvitePending() && on) {
            mHandsetON = true;
            acceptCall(mCore.getCurrentCall());
            LinphoneActivity.instance().startIncallActivity();
        } else if (on && CallActivity.isInstanciated()) {
            mHandsetON = true;
            CallActivity.instance().setSpeakerEnabled(true);
            CallActivity.instance().refreshInCallActions();
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

    public void onNewSubscriptionRequested(Core lc, Friend lf, String url) {}

    public void onNotifyPresenceReceived(Core lc, Friend lf) {}

    @Override
    public void onEcCalibrationAudioInit(Core lc) {}

    @Override
    public void onDtmfReceived(Core lc, Call call, int dtmf) {
        Log.d("[Manager] DTMF received: " + dtmf);
    }

    @Override
    public void onMessageReceived(Core lc, final ChatRoom cr, final ChatMessage message) {
        if (mServiceContext.getResources().getBoolean(R.bool.disable_chat)) {
            return;
        }

        if (mCurrentChatRoomAddress != null
                && cr.getPeerAddress()
                        .asStringUriOnly()
                        .equals(mCurrentChatRoomAddress.asStringUriOnly())) {
            Log.i(
                    "[Manager] Message received for currently displayed chat room, do not make a notification");
            return;
        }

        if (message.getErrorInfo() != null
                && message.getErrorInfo().getReason() == Reason.UnsupportedContent) {
            Log.w("[Manager] Message received but content is unsupported, do not notify it");
            return;
        }

        if (!message.hasTextContent() && message.getFileTransferInformation() == null) {
            Log.w(
                    "[Manager] Message has no text or file transfer information to display, ignoring it...");
            return;
        }

        increaseUnreadCountForChatRoom(cr);

        if (mServiceContext.getResources().getBoolean(R.bool.disable_chat_message_notification)
                || message.isOutgoing()) {
            return;
        }

        final Address from = message.getFromAddress();
        final LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(from);
        final String textMessage =
                (message.hasTextContent())
                        ? message.getTextContent()
                        : getString(R.string.content_description_incoming_file);

        String file = null;
        for (Content c : message.getContents()) {
            if (c.isFile()) {
                file = c.getFilePath();
                getMediaScanner()
                        .scanFile(
                                new File(file),
                                new MediaScannerListener() {
                                    @Override
                                    public void onMediaScanned(String path, Uri uri) {
                                        createNotification(
                                                cr,
                                                contact,
                                                from,
                                                textMessage,
                                                message.getTime(),
                                                uri,
                                                FileUtils.getMimeFromFile(path));
                                    }
                                });
                break;
            }
        }

        if (file == null) {
            createNotification(cr, contact, from, textMessage, message.getTime(), null, null);
        }
    }

    private void createNotification(
            ChatRoom cr,
            LinphoneContact contact,
            Address from,
            String textMessage,
            long time,
            Uri file,
            String mime) {
        if (cr.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            if (contact != null) {
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayMessageNotification(
                                cr.getPeerAddress().asStringUriOnly(),
                                contact.getFullName(),
                                contact.getThumbnailUri(),
                                textMessage,
                                cr.getLocalAddress(),
                                time,
                                file,
                                mime);
            } else {
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayMessageNotification(
                                cr.getPeerAddress().asStringUriOnly(),
                                from.getUsername(),
                                null,
                                textMessage,
                                cr.getLocalAddress(),
                                time,
                                file,
                                mime);
            }
        } else {
            String subject = cr.getSubject();
            if (contact != null) {
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayGroupChatMessageNotification(
                                subject,
                                cr.getPeerAddress().asStringUriOnly(),
                                contact.getFullName(),
                                contact.getThumbnailUri(),
                                textMessage,
                                cr.getLocalAddress(),
                                time,
                                file,
                                mime);
            } else {
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayGroupChatMessageNotification(
                                subject,
                                cr.getPeerAddress().asStringUriOnly(),
                                from.getUsername(),
                                null,
                                textMessage,
                                cr.getLocalAddress(),
                                time,
                                file,
                                mime);
            }
        }
    }

    public void setCurrentChatRoomAddress(Address address) {
        mCurrentChatRoomAddress = address;
        LinphoneService.instance()
                .setCurrentlyDisplayedChatRoom(address != null ? address.asStringUriOnly() : null);
    }

    @Override
    public void onEcCalibrationResult(Core lc, EcCalibratorStatus status, int delay_ms) {
        ((AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE))
                .setMode(AudioManager.MODE_NORMAL);
        mAudioManager.abandonAudioFocus(null);
        Log.i("[Manager] Set audio mode on 'Normal'");
    }

    public void onGlobalStateChanged(final Core lc, final GlobalState state, final String message) {
        Log.i("New global state [", state, "]");
        if (state == GlobalState.On) {
            try {
                initLiblinphone(lc);
            } catch (IllegalArgumentException iae) {
                Log.e("[Manager] Global State Changed Illegal Argument Exception: " + iae);
            }
        }
    }

    public void onRegistrationStateChanged(
            final Core lc,
            final ProxyConfig proxy,
            final RegistrationState state,
            final String message) {
        Log.i("[Manager] New registration state [" + state + "]");

        if (state == RegistrationState.Failed) {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager)
                            mServiceContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            Log.i("[Manager] Active network type: " + activeNetworkInfo.getTypeName());
            if (activeNetworkInfo.isAvailable() && activeNetworkInfo.isConnected()) {
                Log.i("[Manager] Active network is available");
            }
            Log.i(
                    "[Manager] Active network reason and extra info: "
                            + activeNetworkInfo.getReason()
                            + " / "
                            + activeNetworkInfo.getExtraInfo());
            Log.i(
                    "[Manager] Active network state "
                            + activeNetworkInfo.getState()
                            + " / "
                            + activeNetworkInfo.getDetailedState());
        }
    }

    public Context getContext() {
        try {
            if (LinphoneActivity.isInstanciated()) return LinphoneActivity.instance();
            else if (CallActivity.isInstanciated()) return CallActivity.instance();
            else if (CallIncomingActivity.isInstanciated()) return CallIncomingActivity.instance();
            else if (mServiceContext != null) return mServiceContext;
            else if (LinphoneService.isReady())
                return LinphoneService.instance().getApplicationContext();
        } catch (Exception e) {
            Log.e(e);
        }
        return null;
    }

    public void setAudioManagerModeNormal() {
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }

    private void setAudioManagerInCallMode() {
        if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            Log.w("[Manager][AudioManager] already in MODE_IN_COMMUNICATION, skipping...");
            return;
        }
        Log.d("[Manager][AudioManager] Mode: MODE_IN_COMMUNICATION");

        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    @SuppressLint("Wakelock")
    public void onCallStateChanged(
            final Core lc, final Call call, final State state, final String message) {
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
                                    if (LinphoneManager.getInstance() != null) {
                                        LinphoneManager.getInstance().routeAudioToReceiver();
                                        if (LinphoneActivity.instance() != null)
                                            LinphoneActivity.instance().startIncallActivity();
                                    }
                                }
                            }
                        }
                    };
            mTimer = new Timer("Auto answer");
            mTimer.schedule(lTask, mPrefs.getAutoAnswerTime());
        } else if (state == State.IncomingReceived
                || (state == State.IncomingEarlyMedia
                        && mRessources.getBoolean(R.bool.allow_ringing_while_early_media))) {
            // Brighten screen for at least 10 seconds
            if (mCore.getCallsNb() == 1) {
                requestAudioFocus(STREAM_RING);

                mRingingCall = call;
                startRinging();
                // otherwise there is the beep
            }
        } else if (call == mRingingCall && mIsRinging) {
            // previous state was ringing, so stop ringing
            stopRinging();
        }

        if (state == State.Connected) {
            if (mCore.getCallsNb() == 1) {
                // It is for incoming calls, because outgoing calls enter MODE_IN_COMMUNICATION
                // immediately when they start.
                // However, incoming call first use the MODE_RINGING to play the local ring.
                if (call.getDir() == Call.Dir.Incoming) {
                    setAudioManagerInCallMode();
                    // mAudioManager.abandonAudioFocus(null);
                    requestAudioFocus(STREAM_VOICE_CALL);
                }
            }

            if (Hacks.needSoftvolume()) {
                Log.w("[Manager] Using soft volume audio hack");
                adjustVolume(0); // Synchronize
            }
        }

        if (state == State.End || state == State.Error) {
            if (mCore.getCallsNb() == 0) {
                // Disabling proximity sensor
                enableProximitySensing(false);
                Context activity = getContext();
                if (mAudioFocused) {
                    int res = mAudioManager.abandonAudioFocus(null);
                    Log.d(
                            "[Manager] Audio focus released a bit later: "
                                    + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                                            ? "Granted"
                                            : "Denied"));
                    mAudioFocused = false;
                }
                if (activity != null) {
                    TelephonyManager tm =
                            (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                        Log.d("[Manager] ---AudioManager: back to MODE_NORMAL");
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);
                        Log.d("[Manager] All call terminated, routing back to earpiece");
                        routeAudioToReceiver();
                    }
                }
            }
        }
        if (state == State.UpdatedByRemote) {
            // If the correspondent proposes video while audio call
            boolean remoteVideo = call.getRemoteParams().videoEnabled();
            boolean localVideo = call.getCurrentParams().videoEnabled();
            boolean autoAcceptCameraPolicy =
                    LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
            if (remoteVideo
                    && !localVideo
                    && !autoAcceptCameraPolicy
                    && LinphoneManager.getLc().getConference() == null) {
                LinphoneManager.getLc().deferCallUpdate(call);
            }
        }
        if (state == State.OutgoingInit) {
            // Enter the MODE_IN_COMMUNICATION mode as soon as possible, so that ringback
            // is heard normally in earpiece or bluetooth receiver.
            setAudioManagerInCallMode();
            requestAudioFocus(STREAM_VOICE_CALL);
            startBluetooth();
        }

        if (state == State.StreamsRunning) {
            startBluetooth();
            setAudioManagerInCallMode();
        }
    }

    private void startBluetooth() {
        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            BluetoothManager.getInstance().routeAudioToBluetooth();
        }
    }

    public void onCallStatsUpdated(final Core lc, final Call call, final CallStats stats) {}

    @Override
    public void onChatRoomStateChanged(Core lc, ChatRoom cr, ChatRoom.State state) {}

    @Override
    public void onQrcodeFound(Core lc, String result) {}

    public void onCallEncryptionChanged(
            Core lc, Call call, boolean encrypted, String authenticationToken) {}

    public void startEcCalibration() {
        routeAudioToSpeaker();
        setAudioManagerInCallMode();
        Log.i("[Manager] Set audio mode on 'Voice Communication'");
        requestAudioFocus(STREAM_VOICE_CALL);
        int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
        mCore.startEchoCancellerCalibration();
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
    }

    public int startEchoTester() {
        routeAudioToSpeaker();
        setAudioManagerInCallMode();
        Log.i("[Manager] Set audio mode on 'Voice Communication'");
        requestAudioFocus(STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
        int sampleRate;
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
        String sampleRateProperty =
                mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        sampleRate = Integer.parseInt(sampleRateProperty);
        mCore.startEchoTester(sampleRate);
        mEchoTesterIsRunning = true;
        return 1;
    }

    public int stopEchoTester() {
        mEchoTesterIsRunning = false;
        mCore.stopEchoTester();
        routeAudioToReceiver();
        ((AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE))
                .setMode(AudioManager.MODE_NORMAL);
        Log.i("[Manager] Set audio mode on 'Normal'");
        return 1; // status;
    }

    public boolean getEchoTesterStatus() {
        return mEchoTesterIsRunning;
    }

    private void requestAudioFocus(int stream) {
        if (!mAudioFocused) {
            int res =
                    mAudioManager.requestAudioFocus(
                            null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            Log.d(
                    "[Manager] Audio focus requested: "
                            + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                                    ? "Granted"
                                    : "Denied"));
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
        }
    }

    public void enableDeviceRingtone(boolean use) {
        if (use) {
            mCore.setRing(null);
        } else {
            mCore.setRing(mRingSoundFile);
        }
    }

    private synchronized void startRinging() {
        if (!LinphonePreferences.instance().isDeviceRingtoneEnabled()) {
            // Enable speaker audio route, linphone library will do the ringing itself automatically
            routeAudioToSpeaker();
            return;
        }

        if (mRessources.getBoolean(R.bool.allow_ringing_while_early_media)) {
            routeAudioToSpeaker(); // Need to be able to ear the ringtone during the early media
        }

        // if (Hacks.needGalaxySAudioHack())
        mAudioManager.setMode(MODE_RINGTONE);

        try {
            if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
                            || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)
                    && mVibrator != null
                    && LinphonePreferences.instance().isIncomingCallVibrationEnabled()) {
                long[] patern = {0, 1000, 1000};
                mVibrator.vibrate(patern, 1);
            }
            if (mRingerPlayer == null) {
                requestAudioFocus(STREAM_RING);
                mRingerPlayer = new MediaPlayer();
                mRingerPlayer.setAudioStreamType(STREAM_RING);

                String ringtone =
                        LinphonePreferences.instance()
                                .getRingtone(Settings.System.DEFAULT_RINGTONE_URI.toString());
                try {
                    if (ringtone.startsWith("content://")) {
                        mRingerPlayer.setDataSource(mServiceContext, Uri.parse(ringtone));
                    } else {
                        FileInputStream fis = new FileInputStream(ringtone);
                        mRingerPlayer.setDataSource(fis.getFD());
                        fis.close();
                    }
                } catch (IOException e) {
                    Log.e(e, "[Manager] Cannot set ringtone");
                }

                mRingerPlayer.prepare();
                mRingerPlayer.setLooping(true);
                mRingerPlayer.start();
            } else {
                Log.w("[Manager] Already ringing");
            }
        } catch (Exception e) {
            Log.e(e, "[Manager] Cannot handle incoming call");
        }
        mIsRinging = true;
    }

    private synchronized void stopRinging() {
        if (mRingerPlayer != null) {
            mRingerPlayer.stop();
            mRingerPlayer.release();
            mRingerPlayer = null;
        }
        if (mVibrator != null) {
            mVibrator.cancel();
        }

        if (Hacks.needGalaxySAudioHack()) mAudioManager.setMode(AudioManager.MODE_NORMAL);

        mIsRinging = false;
        // You may need to call galaxys audio hack after this method
        if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            if (mServiceContext.getResources().getBoolean(R.bool.isTablet)) {
                Log.d("[Manager] Stopped ringing, routing back to speaker");
                routeAudioToSpeaker();
            } else {
                Log.d("[Manager] Stopped ringing, routing back to earpiece");
                routeAudioToReceiver();
            }
        }
    }

    /** @return false if already in video call. */
    public boolean addVideo() {
        Call call = mCore.getCurrentCall();
        enableCamera(call, true);
        return reinviteWithVideo();
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
                    FileUtils.getCallRecordingFilename(getContext(), call.getRemoteAddress()));
        } else {
            Log.e("[Manager] Could not create call params for call");
            return false;
        }

        mCore.acceptCallWithParams(call, params);
        return true;
    }

    public void adjustVolume(int i) {
        // starting from ICS, volume must be adjusted by the application, at least for
        // STREAM_VOICE_CALL volume stream
        mAudioManager.adjustStreamVolume(
                LINPHONE_VOLUME_STREAM,
                i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI);
    }

    public void isAccountWithAlias() {
        if (!LinphonePreferences.instance().isLinkPopupEnabled()) return;

        if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {

            long now = new Timestamp(new Date().getTime()).getTime();
            if (LinphonePreferences.instance().getLinkPopupTime() != null
                    && Long.parseLong(LinphonePreferences.instance().getLinkPopupTime()) >= now)
                return;

            long future =
                    new Timestamp(
                                    LinphoneActivity.instance()
                                            .getResources()
                                            .getInteger(R.integer.popup_time_interval))
                            .getTime();
            long newDate = now + future;

            if (mAccountCreator != null) {
                mAccountCreator.setUsername(
                        LinphonePreferences.instance()
                                .getAccountUsername(
                                        LinphonePreferences.instance().getDefaultAccountIndex()));
                mAccountCreator.isAccountExist();
                LinphonePreferences.instance().setLinkPopupTime(String.valueOf(newDate));
            }
        } else {
            LinphonePreferences.instance().setLinkPopupTime(null);
        }
    }

    private void askLinkWithPhoneNumber() {
        if (!LinphonePreferences.instance().isLinkPopupEnabled()) return;

        final Dialog dialog =
                LinphoneActivity.instance()
                        .displayDialog(
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
                        assistant.setClass(LinphoneActivity.instance(), AssistantActivity.class);
                        assistant.putExtra("LinkPhoneNumber", true);
                        assistant.putExtra("LinkPhoneNumberAsk", true);
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

    @Override
    public void onTransferStateChanged(Core lc, Call call, State new_call_state) {}

    @Override
    public void onInfoReceived(Core lc, Call call, InfoMessage info) {
        Log.d("[Manager] Info message received from " + call.getRemoteAddress().asString());
        Content ct = info.getContent();
        if (ct != null) {
            Log.d(
                    "[Manager] Info received with body with mime type "
                            + ct.getType()
                            + "/"
                            + ct.getSubtype()
                            + " and data ["
                            + ct.getStringBuffer()
                            + "]");
        }
    }

    @Override
    public void onSubscriptionStateChanged(Core lc, Event ev, SubscriptionState state) {
        Log.d(
                "[Manager] Subscription state changed to "
                        + state
                        + " event name is "
                        + ev.getName());
    }

    @Override
    public void onCallLogUpdated(Core lc, CallLog newcl) {}

    @Override
    public void onNotifyReceived(Core lc, Event ev, String eventName, Content content) {
        Log.d("[Manager] Notify received for event " + eventName);
        if (content != null)
            Log.d(
                    "[Manager] With content "
                            + content.getType()
                            + "/"
                            + content.getSubtype()
                            + " data:"
                            + content.getStringBuffer());
    }

    @Override
    public void onSubscribeReceived(Core lc, Event lev, String subscribeEvent, Content body) {}

    @Override
    public void onPublishStateChanged(Core lc, Event ev, PublishState state) {
        Log.d("[Manager] Publish state changed to " + state + " for event name " + ev.getName());
    }

    @Override
    public void onIsComposingReceived(Core lc, ChatRoom cr) {
        Log.d("[Manager] Composing received for chatroom " + cr.getPeerAddress().asStringUriOnly());
    }

    @Override
    public void onMessageReceivedUnableDecrypt(Core lc, ChatRoom room, ChatMessage message) {}

    @Override
    public void onConfiguringStatus(Core lc, ConfiguringState state, String message) {
        Log.d("[Manager] Remote provisioning status = " + state.toString() + " (" + message + ")");

        LinphonePreferences prefs = LinphonePreferences.instance();
        if (state == ConfiguringState.Successful) {
            if (prefs.isProvisioningLoginViewEnabled()) {
                ProxyConfig proxyConfig = lc.createProxyConfig();
                Address addr = proxyConfig.getIdentityAddress();
                wizardLoginViewDomain = addr.getDomain();
            }
            prefs.setPushNotificationEnabled(prefs.isPushNotificationEnabled());
        }
    }

    @Override
    public void onCallCreated(Core lc, Call call) {}

    @Override
    public void onLogCollectionUploadProgressIndication(Core linphoneCore, int offset, int total) {
        if (total > 0)
            Log.d(
                    "[Manager] Log upload progress: currently uploaded = "
                            + offset
                            + " , total = "
                            + total
                            + ", % = "
                            + String.valueOf((offset * 100) / total));
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
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setMessage(
                                    getString(R.string.update_available) + ": " + versionAv);
                            builder.setCancelable(false);
                            builder.setNeutralButton(
                                    getString(R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialogInterface, int i) {
                                            if (urlToUse != null) {
                                                Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                                                urlIntent.setData(Uri.parse(urlToUse));
                                                getContext().startActivity(urlIntent);
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
    public void onEcCalibrationAudioUninit(Core lc) {}

    private void sendLogs(String info) {
        Context context = LinphoneActivity.instance();
        final String appName = context.getString(R.string.app_name);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(
                Intent.EXTRA_EMAIL,
                new String[] {context.getString(R.string.about_bugreport_email)});
        i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
        i.putExtra(Intent.EXTRA_TEXT, info);
        i.setType("application/zip");

        try {
            context.startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(ex);
        }
    }

    @Override
    public void onLogCollectionUploadStateChanged(
            Core linphoneCore, LogCollectionUploadState state, String info) {
        Log.d("[Manager] Log upload state: " + state.toString() + ", info = " + info);
        if (state == LogCollectionUploadState.Delivered) {
            ClipboardManager clipboard =
                    (ClipboardManager) mServiceContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Logs url", info);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(
                            LinphoneActivity.instance(),
                            getString(R.string.logs_url_copied_to_clipboard),
                            Toast.LENGTH_SHORT)
                    .show();
            sendLogs(info);
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

    @Override
    public void onReferReceived(Core lc, String refer_to) {}

    @Override
    public void onNetworkReachable(Core lc, boolean enable) {}

    @Override
    public void onAuthenticationRequested(Core lc, AuthInfo authInfo, AuthMethod method) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onNotifyPresenceReceivedForUriOrTel(
            Core lc, Friend lf, String uri_or_tel, PresenceModel presence_model) {}

    @Override
    public void onBuddyInfoUpdated(Core lc, Friend lf) {}

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (status.equals(AccountCreator.Status.AccountExist)) {
            accountCreator.isAccountLinked();
        }
    }

    @Override
    public void onCreateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onLinkAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (status.equals(AccountCreator.Status.AccountNotLinked)) {
            askLinkWithPhoneNumber();
        }
    }

    @Override
    public void onActivateAlias(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountActivated(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onRecoverAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountLinked(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (status.equals(AccountCreator.Status.AccountNotLinked)) {
            askLinkWithPhoneNumber();
        }
    }

    @Override
    public void onIsAliasUsed(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onUpdateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    private void updateMissedChatCount() {
        for (ChatRoom cr : LinphoneManager.getLc().getChatRooms()) {
            updateUnreadCountForChatRoom(cr, cr.getUnreadMessagesCount());
        }
    }

    public int getUnreadMessageCount() {
        int count = 0;
        for (ChatRoom room : mCore.getChatRooms()) {
            count += room.getUnreadMessagesCount();
        }
        return count;
    }

    public void updateUnreadCountForChatRoom(
            String localSipUri, String remoteSipUri, Integer value) {
        String key = localSipUri + "//" + remoteSipUri;
        mUnreadChatsPerRoom.put(key, value);
    }

    public void updateUnreadCountForChatRoom(ChatRoom cr, Integer value) {
        String localSipUri = cr.getLocalAddress().asStringUriOnly();
        String remoteSipUri = cr.getPeerAddress().asStringUriOnly();
        updateUnreadCountForChatRoom(localSipUri, remoteSipUri, value);
    }

    private void increaseUnreadCountForChatRoom(ChatRoom cr) {
        String localSipUri = cr.getLocalAddress().asStringUriOnly();
        String remoteSipUri = cr.getPeerAddress().asStringUriOnly();
        String key = localSipUri + "//" + remoteSipUri;
        if (mUnreadChatsPerRoom.containsKey(key)) {
            mUnreadChatsPerRoom.put(key, mUnreadChatsPerRoom.get(key) + 1);
        } else {
            mUnreadChatsPerRoom.put(key, 1);
        }
    }

    public interface AddressType {
        CharSequence getText();

        void setText(CharSequence s);

        String getDisplayedName();

        void setDisplayedName(String s);
    }

    public boolean hasLastCallSasBeenRejected() {
        return mHasLastCallSasBeenRejected;
    }

    public void lastCallSasRejected(boolean rejected) {
        mHasLastCallSasBeenRejected = rejected;
    }
}
