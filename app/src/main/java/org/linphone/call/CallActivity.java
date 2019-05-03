package org.linphone.call;

/*
CallActivity.java
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

import android.Manifest;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.AddressFamily;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallListenerStub;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.MediaEncryption;
import org.linphone.core.PayloadType;
import org.linphone.core.Player;
import org.linphone.core.StreamType;
import org.linphone.core.tools.Log;
import org.linphone.fragments.StatusFragment;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.receivers.BluetoothManager;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneGenericActivity;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;
import org.linphone.views.Numpad;

public class CallActivity extends LinphoneGenericActivity
        implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int SECONDS_BEFORE_HIDING_CONTROLS = 4000;
    private static final int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;
    private static final int PERMISSIONS_REQUEST_CAMERA = 202;
    private static final int PERMISSIONS_ENABLED_CAMERA = 203;
    private static final int PERMISSIONS_ENABLED_MIC = 204;
    private static final int PERMISSIONS_EXTERNAL_STORAGE = 205;

    private static CallActivity sInstance;
    private static long sTimeRemind = 0;
    private Handler mControlsHandler = new Handler();
    private Runnable mControls;
    private ImageView mSwitchCamera;
    private TextView mMissedChats;
    private RelativeLayout mActiveCallHeader, mSideMenuContent, mAvatarLayout;
    private ImageView mPause,
            mHangUp,
            mDialer,
            mVideo,
            mMicro,
            mSpeaker,
            mOptions,
            mAddCall,
            mTransfer,
            mConference,
            mConferenceStatus,
            mRecordCall,
            mRecording;
    private ImageView mAudioRoute, mRouteSpeaker, mRouteEarpiece, mRouteBluetooth, mMenu, mChat;
    private LinearLayout mNoCurrentCall, mCallInfo, mCallPaused;
    private ProgressBar mVideoProgress;
    private StatusFragment mStatus;
    private CallAudioFragment mAudioCallFragment;
    private CallVideoFragment mVideoCallFragment;
    private boolean mIsSpeakerEnabled = false,
            mIsMicMuted = false,
            mIsTransferAllowed,
            mIsVideoAsk,
            mIsRecording = false;
    private LinearLayout mControlsLayout;
    private Numpad mNumpad;
    private int mCameraNumber;
    private CountDownTimer mCountDownTimer;
    private boolean mIsVideoCallPaused = false;
    private Dialog mDialog = null;
    private HeadsetReceiver mHeadsetReceiver;

    private LinearLayout mCallsList, mConferenceList;
    private LayoutInflater mInflater;
    private ViewGroup mContainer;
    private boolean mIsConferenceRunning = false;
    private CoreListenerStub mListener;
    private DrawerLayout mSideMenu;

    private final Handler mHandler = new Handler();
    private Timer mTimer;
    private TimerTask mTask;
    private HashMap<String, String> mEncoderTexts;
    private HashMap<String, String> mDecoderTexts;
    private CallListenerStub mCallListener;
    private Call mCallDisplayedInStats;

    private boolean mOldIsSpeakerEnabled = false;

    public static CallActivity instance() {
        return sInstance;
    }

    public static boolean isInstanciated() {
        return sInstance != null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Compatibility.setShowWhenLocked(this, true);

        setContentView(R.layout.call);

        // Earset Connectivity Broadcast Processing
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.HEADSET_PLUG");
        mHeadsetReceiver = new HeadsetReceiver();
        registerReceiver(mHeadsetReceiver, intentFilter);

        mIsTransferAllowed =
                getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

        mCameraNumber = AndroidCameraConfiguration.retrieveCameras().length;

        mEncoderTexts = new HashMap<>();
        mDecoderTexts = new HashMap<>();

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onCallStateChanged(
                            Core lc, final Call call, Call.State state, String message) {
                        if (LinphoneManager.getLc().getCallsNb() == 0) {
                            finish();
                            return;
                        }

                        if (state == State.IncomingReceived || state == State.IncomingEarlyMedia) {
                            // This scenario will be handled by the Service listener
                            return;
                        } else if (state == State.Paused
                                || state == State.PausedByRemote
                                || state == State.Pausing) {
                            if (LinphoneManager.getLc().getCurrentCall() != null) {
                                mVideo.setEnabled(false);
                            }
                            if (isVideoEnabled(call)) {
                                showAudioView();
                            }
                        } else if (state == State.Resuming) {
                            if (LinphonePreferences.instance().isVideoEnabled()) {
                                mStatus.refreshStatusItems(call);
                                if (call.getCurrentParams().videoEnabled()) {
                                    showVideoView();
                                }
                            }
                            if (LinphoneManager.getLc().getCurrentCall() != null) {
                                mVideo.setEnabled(true);
                            }
                        } else if (state == State.StreamsRunning) {
                            switchVideo(isVideoEnabled(call));
                            enableAndRefreshInCallActions();

                            if (mStatus != null) {
                                mVideoProgress.setVisibility(View.GONE);
                                mStatus.refreshStatusItems(call);
                            }
                        } else if (state == State.UpdatedByRemote) {
                            // If the correspondent proposes video while audio call
                            boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
                            if (!videoEnabled) {
                                acceptCallUpdate(false);
                                return;
                            }

                            boolean remoteVideo = call.getRemoteParams().videoEnabled();
                            boolean localVideo = call.getCurrentParams().videoEnabled();
                            boolean autoAcceptCameraPolicy =
                                    LinphonePreferences.instance()
                                            .shouldAutomaticallyAcceptVideoRequests();
                            if (remoteVideo
                                    && !localVideo
                                    && !autoAcceptCameraPolicy
                                    && !LinphoneManager.getLc().isInConference()) {
                                showAcceptCallUpdateDialog();
                                createTimerForDialog(SECONDS_BEFORE_DENYING_CALL_UPDATE);
                            }
                        }

                        refreshIncallUi();
                        mTransfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
                    }

                    @Override
                    public void onCallEncryptionChanged(
                            Core lc,
                            final Call call,
                            boolean encrypted,
                            String authenticationToken) {
                        if (mStatus != null) {
                            if (call.getCurrentParams()
                                            .getMediaEncryption()
                                            .equals(MediaEncryption.ZRTP)
                                    && !call.getAuthenticationTokenVerified()) {
                                mStatus.showZRTPDialog(call);
                            }
                            mStatus.refreshStatusItems(call);
                        }
                    }
                };

        if (findViewById(R.id.fragmentContainer) != null) {
            initUI();

            if (LinphoneManager.getLc().getCallsNb() > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];

                if (LinphoneUtils.isCallEstablished(call)) {
                    enableAndRefreshInCallActions();
                }
            }
            if (savedInstanceState != null) {
                // Fragment already created, no need to create it again (else it will generate a
                // memory leak with duplicated fragments)
                mIsSpeakerEnabled = savedInstanceState.getBoolean("Speaker");
                mIsMicMuted = savedInstanceState.getBoolean("Mic");
                mIsVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
                if (savedInstanceState.getBoolean("AskingVideo")) {
                    showAcceptCallUpdateDialog();
                    sTimeRemind = savedInstanceState.getLong("sTimeRemind");
                    createTimerForDialog(sTimeRemind);
                }
                refreshInCallActions();
                return;
            } else {
                mIsSpeakerEnabled = LinphoneManager.getInstance().isSpeakerEnabled();
                mIsMicMuted = !LinphoneManager.getLc().micEnabled();
            }

            Fragment callFragment;
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                callFragment = new CallVideoFragment();
                mVideoCallFragment = (CallVideoFragment) callFragment;
                displayVideoCall(false);
                LinphoneManager.getInstance().routeAudioToSpeaker();
                mIsSpeakerEnabled = true;
            } else {
                callFragment = new CallAudioFragment();
                mAudioCallFragment = (CallAudioFragment) callFragment;
            }

            if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
                BluetoothManager.getInstance().routeAudioToBluetooth();
            }

            callFragment.setArguments(getIntent().getExtras());
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragmentContainer, callFragment)
                    .commitAllowingStateLoss();
        }
    }

    private void createTimerForDialog(long time) {
        mCountDownTimer =
                new CountDownTimer(time, 1000) {
                    public void onTick(long millisUntilFinished) {
                        sTimeRemind = millisUntilFinished;
                    }

                    public void onFinish() {
                        if (mDialog != null) {
                            mDialog.dismiss();
                            mDialog = null;
                        }
                        acceptCallUpdate(false);
                    }
                }.start();
    }

    private boolean isVideoEnabled(Call call) {
        if (call != null) {
            return call.getCurrentParams().videoEnabled();
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("Speaker", LinphoneManager.getInstance().isSpeakerEnabled());
        outState.putBoolean("Mic", !LinphoneManager.getLc().micEnabled());
        outState.putBoolean("VideoCallPaused", mIsVideoCallPaused);
        outState.putBoolean("AskingVideo", mIsVideoAsk);
        outState.putLong("sTimeRemind", sTimeRemind);
        if (mDialog != null) mDialog.dismiss();
        super.onSaveInstanceState(outState);
    }

    private boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    private void initUI() {
        mInflater = LayoutInflater.from(this);
        mContainer = findViewById(R.id.topLayout);
        mCallsList = findViewById(R.id.calls_list);
        mConferenceList = findViewById(R.id.conference_list);

        // TopBar
        mVideo = findViewById(R.id.video);
        mVideo.setOnClickListener(this);
        mVideo.setEnabled(false);

        mVideoProgress = findViewById(R.id.video_in_progress);
        mVideoProgress.setVisibility(View.GONE);

        mMicro = findViewById(R.id.micro);
        mMicro.setOnClickListener(this);

        mSpeaker = findViewById(R.id.speaker);
        mSpeaker.setOnClickListener(this);

        mOptions = findViewById(R.id.options);
        mOptions.setOnClickListener(this);
        mOptions.setEnabled(false);

        // BottonBar
        mHangUp = findViewById(R.id.hang_up);
        mHangUp.setOnClickListener(this);

        mDialer = findViewById(R.id.dialer);
        mDialer.setOnClickListener(this);

        mNumpad = findViewById(R.id.numpad);
        mNumpad.getBackground().setAlpha(240);

        mChat = findViewById(R.id.chat);
        mChat.setOnClickListener(this);
        mMissedChats = findViewById(R.id.missed_chats);

        // Others

        // Active Call
        mCallInfo = findViewById(R.id.active_call_info);

        mPause = findViewById(R.id.pause);
        mPause.setOnClickListener(this);
        mPause.setEnabled(false);

        mActiveCallHeader = findViewById(R.id.active_call);
        mNoCurrentCall = findViewById(R.id.no_current_call);
        mCallPaused = findViewById(R.id.remote_pause);

        mAvatarLayout = findViewById(R.id.avatar_layout);

        // Options
        mAddCall = findViewById(R.id.add_call);
        mAddCall.setOnClickListener(this);
        mAddCall.setEnabled(false);

        mTransfer = findViewById(R.id.transfer);
        mTransfer.setOnClickListener(this);
        mTransfer.setEnabled(false);

        mConference = findViewById(R.id.conference);
        mConference.setEnabled(false);
        mConference.setOnClickListener(this);

        mRecordCall = findViewById(R.id.record_call);
        mRecordCall.setOnClickListener(this);
        mRecordCall.setEnabled(false);

        mRecording = findViewById(R.id.recording);
        mRecording.setOnClickListener(this);
        mRecording.setEnabled(false);
        mRecording.setVisibility(View.GONE);

        try {
            mAudioRoute = findViewById(R.id.audio_route);
            mAudioRoute.setOnClickListener(this);
            mRouteSpeaker = findViewById(R.id.route_speaker);
            mRouteSpeaker.setOnClickListener(this);
            mRouteEarpiece = findViewById(R.id.route_earpiece);
            mRouteEarpiece.setOnClickListener(this);
            mRouteBluetooth = findViewById(R.id.route_bluetooth);
            mRouteBluetooth.setOnClickListener(this);
        } catch (NullPointerException npe) {
            Log.e("Bluetooth: Audio routes mMenu disabled on tablets for now (1)");
        }

        mSwitchCamera = findViewById(R.id.switchCamera);
        mSwitchCamera.setOnClickListener(this);

        mControlsLayout = findViewById(R.id.menu);

        if (!mIsTransferAllowed) {
            mAddCall.setBackgroundResource(R.drawable.options_add_call);
        }

        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            try {
                mAudioRoute.setVisibility(View.VISIBLE);
                mSpeaker.setVisibility(View.GONE);
            } catch (NullPointerException npe) {
                Log.e("Bluetooth: Audio routes mMenu disabled on tablets for now (2)");
            }
        } else {
            try {
                mAudioRoute.setVisibility(View.GONE);
                mSpeaker.setVisibility(View.VISIBLE);
            } catch (NullPointerException npe) {
                Log.e("Bluetooth: Audio routes mMenu disabled on tablets for now (3)");
            }
        }

        createInCallStats();
        LinphoneManager.getInstance().changeStatusToOnThePhone();
    }

    private void checkAndRequestPermission(String permission, int result) {
        int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " is "
                        + (permissionGranted == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));

        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[] {permission}, result);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, final int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                LinphoneUtils.dispatchOnUIThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                acceptCallUpdate(
                                        grantResults[0] == PackageManager.PERMISSION_GRANTED);
                            }
                        });
                break;
            case PERMISSIONS_ENABLED_CAMERA:
                LinphoneUtils.dispatchOnUIThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                disableVideo(grantResults[0] != PackageManager.PERMISSION_GRANTED);
                            }
                        });
                break;
            case PERMISSIONS_ENABLED_MIC:
                LinphoneUtils.dispatchOnUIThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                    toggleMicro();
                                }
                            }
                        });
                break;
            case PERMISSIONS_EXTERNAL_STORAGE:
                LinphoneUtils.dispatchOnUIThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                    toggleCallRecording(!mIsRecording);
                                }
                            }
                        });
        }
    }

    private void createInCallStats() {
        mSideMenu = findViewById(R.id.side_menu);
        mMenu = findViewById(R.id.call_quality);

        mSideMenuContent = findViewById(R.id.side_menu_content);

        mMenu.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mSideMenu.isDrawerVisible(Gravity.LEFT)) {
                            mSideMenu.closeDrawer(mSideMenuContent);
                        } else {
                            mSideMenu.openDrawer(mSideMenuContent);
                        }
                    }
                });

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            initCallStatsRefresher(lc.getCurrentCall(), findViewById(R.id.incall_stats));
        }
    }

    private void refreshIncallUi() {
        refreshInCallActions();
        refreshCallList();
        enableAndRefreshInCallActions();
        displayMissedChats();
    }

    public void setSpeakerEnabled(boolean enabled) {
        mIsSpeakerEnabled = enabled;
    }

    public void refreshInCallActions() {
        if (!LinphonePreferences.instance().isVideoEnabled() || mIsConferenceRunning) {
            mVideo.setEnabled(false);
        } else {
            if (mVideo.isEnabled()) {
                if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                    mVideo.setSelected(true);
                    mVideoProgress.setVisibility(View.INVISIBLE);
                } else {
                    mVideo.setSelected(false);
                }
            } else {
                mVideo.setSelected(false);
            }
        }
        if (getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName())
                != PackageManager.PERMISSION_GRANTED) {
            mVideo.setSelected(false);
        }

        mSpeaker.setSelected(mIsSpeakerEnabled);

        if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName())
                != PackageManager.PERMISSION_GRANTED) {
            mIsMicMuted = true;
        }
        mMicro.setSelected(mIsMicMuted);

        try {
            mRouteSpeaker.setSelected(false);
            if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
                mIsSpeakerEnabled = false; // We need this if mIsSpeakerEnabled wasn't set correctly
                mRouteEarpiece.setSelected(false);
                mRouteBluetooth.setSelected(true);
                return;
            } else {
                mRouteEarpiece.setSelected(true);
                mRouteBluetooth.setSelected(false);
            }

            if (mIsSpeakerEnabled) {
                mRouteSpeaker.setSelected(true);
                mRouteEarpiece.setSelected(false);
                mRouteBluetooth.setSelected(false);
            }
        } catch (NullPointerException npe) {
            Log.e("Bluetooth: Audio routes mMenu disabled on tablets for now (4)");
        }
    }

    private void enableAndRefreshInCallActions() {
        int confsize = 0;

        if (LinphoneManager.getLc().isInConference()) {
            confsize =
                    LinphoneManager.getLc().getConferenceSize()
                            - (LinphoneManager.getLc().getConference() != null ? 1 : 0);
        }

        // Enabled mTransfer button
        mTransfer.setEnabled(mIsTransferAllowed && !LinphoneManager.getLc().soundResourcesLocked());

        // Enable mConference button
        mConference.setEnabled(
                LinphoneManager.getLc().getCallsNb() > 1
                        && LinphoneManager.getLc().getCallsNb() > confsize
                        && !LinphoneManager.getLc().soundResourcesLocked());

        mAddCall.setEnabled(
                LinphoneManager.getLc().getCallsNb() < LinphoneManager.getLc().getMaxCalls()
                        && !LinphoneManager.getLc().soundResourcesLocked());
        mOptions.setEnabled(
                !getResources().getBoolean(R.bool.disable_options_in_call)
                        && (mAddCall.isEnabled() || mTransfer.isEnabled()));

        Call currentCall = LinphoneManager.getLc().getCurrentCall();

        mRecordCall.setEnabled(
                !LinphoneManager.getLc().soundResourcesLocked()
                        && currentCall != null
                        && currentCall.getCurrentParams().getRecordFile() != null);
        mRecordCall.setSelected(mIsRecording);

        mRecording.setEnabled(mIsRecording);
        mRecording.setVisibility(mIsRecording ? View.VISIBLE : View.GONE);

        mVideo.setEnabled(
                currentCall != null
                        && LinphonePreferences.instance().isVideoEnabled()
                        && !currentCall.mediaInProgress());

        mPause.setEnabled(currentCall != null && !currentCall.mediaInProgress());

        mMicro.setEnabled(true);
        mSpeaker.setEnabled(!isTablet());
        mTransfer.setEnabled(true);
        mPause.setEnabled(true);
        mDialer.setEnabled(true);
    }

    public void updateStatusFragment(StatusFragment statusFragment) {
        mStatus = statusFragment;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.video) {
            int camera =
                    getPackageManager()
                            .checkPermission(Manifest.permission.CAMERA, getPackageName());
            Log.i(
                    "[Permission] Camera permission is "
                            + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

            if (camera == PackageManager.PERMISSION_GRANTED) {
                disableVideo(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()));
            } else {
                checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_ENABLED_CAMERA);
            }
        } else if (id == R.id.micro) {
            int recordAudio =
                    getPackageManager()
                            .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
            Log.i(
                    "[Permission] Record audio permission is "
                            + (recordAudio == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));

            if (recordAudio == PackageManager.PERMISSION_GRANTED) {
                toggleMicro();
            } else {
                checkAndRequestPermission(
                        Manifest.permission.RECORD_AUDIO, PERMISSIONS_ENABLED_MIC);
            }
        } else if (id == R.id.speaker) {
            toggleSpeaker();
        } else if (id == R.id.add_call) {
            goBackToDialer();
        } else if (id == R.id.record_call) {
            int externalStorage =
                    getPackageManager()
                            .checkPermission(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE, getPackageName());
            Log.i(
                    "[Permission] External storage permission is "
                            + (externalStorage == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));

            if (externalStorage == PackageManager.PERMISSION_GRANTED) {
                toggleCallRecording(!mIsRecording);
            } else {
                checkAndRequestPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSIONS_EXTERNAL_STORAGE);
            }
        } else if (id == R.id.recording) {
            toggleCallRecording(false);
        } else if (id == R.id.pause) {
            pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
        } else if (id == R.id.hang_up) {
            hangUp();
        } else if (id == R.id.dialer) {
            hideOrDisplayNumpad();
        } else if (id == R.id.chat) {
            goToChatList();
        } else if (id == R.id.conference) {
            enterConference();
            hideOrDisplayCallOptions();
        } else if (id == R.id.switchCamera) {
            if (mVideoCallFragment != null) {
                mVideoCallFragment.switchCamera();
            }
        } else if (id == R.id.transfer) {
            goBackToDialerAndDisplayTransferButton();
        } else if (id == R.id.options) {
            hideOrDisplayCallOptions();
        } else if (id == R.id.audio_route) {
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_bluetooth) {
            if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
                mIsSpeakerEnabled = false;
                mRouteBluetooth.setSelected(true);
                mRouteSpeaker.setSelected(false);
                mRouteEarpiece.setSelected(false);
            }
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_earpiece) {
            LinphoneManager.getInstance().routeAudioToReceiver();
            mIsSpeakerEnabled = false;
            mRouteBluetooth.setSelected(false);
            mRouteSpeaker.setSelected(false);
            mRouteEarpiece.setSelected(true);
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.route_speaker) {
            LinphoneManager.getInstance().routeAudioToSpeaker();
            mIsSpeakerEnabled = true;
            mRouteBluetooth.setSelected(false);
            mRouteSpeaker.setSelected(true);
            mRouteEarpiece.setSelected(false);
            hideOrDisplayAudioRoutes();
        } else if (id == R.id.call_pause) {
            Call call = (Call) v.getTag();
            pauseOrResumeCall(call);
        } else if (id == R.id.conference_pause) {
            pauseOrResumeConference();
        }
    }

    private void toggleCallRecording(boolean enable) {
        Call call = LinphoneManager.getLc().getCurrentCall();

        if (call == null) return;

        if (enable && !mIsRecording) {
            call.startRecording();
            Log.d("start call mRecording");
            mRecordCall.setSelected(true);

            mRecording.setVisibility(View.VISIBLE);
            mRecording.setEnabled(true);

            mIsRecording = true;
        } else if (!enable && mIsRecording) {
            call.stopRecording();
            Log.d("stop call mRecording");
            mRecordCall.setSelected(false);

            mRecording.setVisibility(View.GONE);
            mRecording.setEnabled(false);

            mIsRecording = false;
        }
    }

    private void disableVideo(final boolean videoDisabled) {
        final Call call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        if (videoDisabled) {
            CallParams params = LinphoneManager.getLc().createCallParams(call);
            params.enableVideo(false);
            LinphoneManager.getLc().updateCall(call, params);
        } else {
            mVideoProgress.setVisibility(View.VISIBLE);
            if (call.getRemoteParams() != null && !call.getRemoteParams().lowBandwidthEnabled()) {
                LinphoneManager.getInstance().addVideo();
            } else {
                displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
            }
        }
    }

    private void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    private void switchVideo(final boolean displayVideo) {
        final Call call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        // Check if the call is not terminated
        if (call.getState() == State.End || call.getState() == State.Released) return;

        if (!displayVideo) {
            showAudioView();
        } else {
            if (!call.getRemoteParams().lowBandwidthEnabled()) {
                LinphoneManager.getInstance().addVideo();
                if (mVideoCallFragment == null || !mVideoCallFragment.isVisible()) showVideoView();
            } else {
                displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
            }
        }
    }

    private void showAudioView() {
        if (LinphoneManager.getLc().getCurrentCall() != null) {
            if (!mIsSpeakerEnabled) {
                LinphoneManager.getInstance().enableProximitySensing(true);
            }
        }
        replaceFragmentVideoByAudio();
        displayAudioCall();
        showStatusBar();
        removeCallbacks();
    }

    private void showVideoView() {
        if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            Log.w("Bluetooth not available, using mSpeaker");
            LinphoneManager.getInstance().routeAudioToSpeaker();
            mIsSpeakerEnabled = true;
        }
        refreshInCallActions();

        LinphoneManager.getInstance().enableProximitySensing(false);

        replaceFragmentAudioByVideo();
        hideStatusBar();
    }

    private void displayNoCurrentCall(boolean display) {
        if (!display) {
            mActiveCallHeader.setVisibility(View.VISIBLE);
            mNoCurrentCall.setVisibility(View.GONE);
        } else {
            mActiveCallHeader.setVisibility(View.GONE);
            mNoCurrentCall.setVisibility(View.VISIBLE);
        }
    }

    private void displayCallPaused(boolean display) {
        if (display) {
            mCallPaused.setVisibility(View.VISIBLE);
        } else {
            mCallPaused.setVisibility(View.GONE);
        }
    }

    private void displayAudioCall() {
        mControlsLayout.setVisibility(View.VISIBLE);
        mActiveCallHeader.setVisibility(View.VISIBLE);
        mCallInfo.setVisibility(View.VISIBLE);
        mAvatarLayout.setVisibility(View.VISIBLE);
        mSwitchCamera.setVisibility(View.GONE);
    }

    private void replaceFragmentVideoByAudio() {
        mAudioCallFragment = new CallAudioFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, mAudioCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            Log.e(e);
        }
    }

    private void replaceFragmentAudioByVideo() {
        //		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
        mVideoCallFragment = new CallVideoFragment();

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, mVideoCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            Log.e(e);
        }
    }

    private void toggleMicro() {
        Core lc = LinphoneManager.getLc();
        mIsMicMuted = !mIsMicMuted;
        lc.enableMic(!mIsMicMuted);
        mMicro.setSelected(mIsMicMuted);
    }

    private void toggleSpeaker() {
        mIsSpeakerEnabled = !mIsSpeakerEnabled;
        if (LinphoneManager.getLc().getCurrentCall() != null) {
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()))
                LinphoneManager.getInstance().enableProximitySensing(false);
            else LinphoneManager.getInstance().enableProximitySensing(!mIsSpeakerEnabled);
        }
        mSpeaker.setSelected(mIsSpeakerEnabled);
        if (mIsSpeakerEnabled) {
            LinphoneManager.getInstance().routeAudioToSpeaker();
            LinphoneManager.getInstance().enableSpeaker(mIsSpeakerEnabled);
        } else {
            Log.d("Toggle mSpeaker off, routing back to earpiece");
            LinphoneManager.getInstance().routeAudioToReceiver();
        }
    }

    private void pauseOrResumeCall(Call call) {
        Core lc = LinphoneManager.getLc();
        if (call != null && LinphoneManager.getLc().getCurrentCall() == call) {
            lc.pauseCall(call);
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
                mIsVideoCallPaused = true;
            }
            mPause.setSelected(true);
        } else if (call != null) {
            if (call.getState() == State.Paused) {
                lc.resumeCall(call);
                if (mIsVideoCallPaused) {
                    mIsVideoCallPaused = false;
                }
                mPause.setSelected(false);
            }
        }
    }

    private void hangUp() {
        Core lc = LinphoneManager.getLc();
        Call currentCall = lc.getCurrentCall();

        if (mIsRecording) {
            toggleCallRecording(false);
        }

        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }

    private void displayVideoCall(boolean display) {
        if (display) {
            showStatusBar();
            mControlsLayout.setVisibility(View.VISIBLE);
            mActiveCallHeader.setVisibility(View.VISIBLE);
            mCallInfo.setVisibility(View.VISIBLE);
            mAvatarLayout.setVisibility(View.GONE);
            mCallsList.setVisibility(View.VISIBLE);
            if (mCameraNumber > 1) {
                mSwitchCamera.setVisibility(View.VISIBLE);
            }
        } else {
            hideStatusBar();
            mControlsLayout.setVisibility(View.GONE);
            mActiveCallHeader.setVisibility(View.GONE);
            mSwitchCamera.setVisibility(View.GONE);
            mCallsList.setVisibility(View.GONE);
        }
    }

    public void displayVideoCallControlsIfHidden() {
        if (mControlsLayout != null) {
            if (mControlsLayout.getVisibility() != View.VISIBLE) {
                displayVideoCall(true);
            }
            resetControlsHidingCallBack();
        }
    }

    public void resetControlsHidingCallBack() {
        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;

        if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
            mControlsHandler.postDelayed(
                    mControls =
                            new Runnable() {
                                public void run() {
                                    hideNumpad();
                                    mVideo.setEnabled(true);
                                    mTransfer.setVisibility(View.INVISIBLE);
                                    mAddCall.setVisibility(View.INVISIBLE);
                                    mConference.setVisibility(View.INVISIBLE);
                                    mRecordCall.setVisibility(View.INVISIBLE);
                                    displayVideoCall(false);
                                    mNumpad.setVisibility(View.GONE);
                                    mOptions.setSelected(false);
                                }
                            },
                    SECONDS_BEFORE_HIDING_CONTROLS);
        }
    }

    public void removeCallbacks() {
        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;
    }

    private void hideNumpad() {
        if (mNumpad == null || mNumpad.getVisibility() != View.VISIBLE) {
            return;
        }

        mDialer.setImageResource(R.drawable.footer_dialer);
        mNumpad.setVisibility(View.GONE);
    }

    private void hideOrDisplayNumpad() {
        if (mNumpad == null) {
            return;
        }

        if (mNumpad.getVisibility() == View.VISIBLE) {
            hideNumpad();
        } else {
            mDialer.setImageResource(R.drawable.dialer_alt_back);
            mNumpad.setVisibility(View.VISIBLE);
        }
    }

    private void hideOrDisplayAudioRoutes() {
        if (mRouteSpeaker.getVisibility() == View.VISIBLE) {
            mRouteSpeaker.setVisibility(View.INVISIBLE);
            mRouteBluetooth.setVisibility(View.INVISIBLE);
            mRouteEarpiece.setVisibility(View.INVISIBLE);
            mAudioRoute.setSelected(false);
        } else {
            mRouteSpeaker.setVisibility(View.VISIBLE);
            mRouteBluetooth.setVisibility(View.VISIBLE);
            mRouteEarpiece.setVisibility(View.VISIBLE);
            mAudioRoute.setSelected(true);
        }
    }

    private void hideOrDisplayCallOptions() {
        // Hide mOptions
        if (mAddCall.getVisibility() == View.VISIBLE) {
            mOptions.setSelected(false);
            if (mIsTransferAllowed) {
                mTransfer.setVisibility(View.INVISIBLE);
            }
            mAddCall.setVisibility(View.INVISIBLE);
            mConference.setVisibility(View.INVISIBLE);
            mRecordCall.setVisibility(View.INVISIBLE);
        } else { // Display mOptions
            if (mIsTransferAllowed) {
                mTransfer.setVisibility(View.VISIBLE);
            }
            mAddCall.setVisibility(View.VISIBLE);
            mConference.setVisibility(View.VISIBLE);
            mRecordCall.setVisibility(View.VISIBLE);
            mOptions.setSelected(true);
            mTransfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
        }
    }

    private void goBackToDialer() {
        Intent intent = new Intent();
        intent.setClass(this, LinphoneActivity.class);
        intent.putExtra("AddCall", true);
        startActivity(intent);
    }

    private void goBackToDialerAndDisplayTransferButton() {
        Intent intent = new Intent();
        intent.setClass(this, LinphoneActivity.class);
        intent.putExtra("Transfer", true);
        startActivity(intent);
    }

    private void goToChatList() {
        Intent intent = new Intent();
        intent.setClass(this, LinphoneActivity.class);
        intent.putExtra("GoToChat", true);
        startActivity(intent);
    }

    private void acceptCallUpdate(boolean accept) {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }

        Call call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }

        CallParams params = LinphoneManager.getLc().createCallParams(call);
        if (accept) {
            params.enableVideo(true);
            LinphoneManager.getLc().enableVideoCapture(true);
            LinphoneManager.getLc().enableVideoDisplay(true);
        }

        LinphoneManager.getLc().acceptCallUpdate(call, params);
    }

    private void hideStatusBar() {
        if (isTablet()) {
            return;
        }

        findViewById(R.id.status).setVisibility(View.GONE);
        findViewById(R.id.fragmentContainer).setPadding(0, 0, 0, 0);
    }

    private void showStatusBar() {
        if (isTablet()) {
            return;
        }

        if (mStatus != null && !mStatus.isVisible()) {
            // Hack to ensure statusFragment is visible after coming back to
            // mDialer from mChat
            mStatus.getView().setVisibility(View.VISIBLE);
        }
        findViewById(R.id.status).setVisibility(View.VISIBLE);
        // findViewById(R.id.fragmentContainer).setPadding(0,
        // LinphoneUtils.pixelsToDpi(getResources(), 40), 0, 0);
    }

    private void showAcceptCallUpdateDialog() {
        mDialog = new Dialog(this);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.dark_grey_color));
        d.setAlpha(200);
        mDialog.setContentView(R.layout.dialog);
        mDialog.getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mDialog.getWindow().setBackgroundDrawable(d);

        TextView customText = mDialog.findViewById(R.id.dialog_message);
        customText.setText(getResources().getString(R.string.add_video_dialog));
        mDialog.findViewById(R.id.dialog_delete_button).setVisibility(View.GONE);
        Button accept = mDialog.findViewById(R.id.dialog_ok_button);
        accept.setVisibility(View.VISIBLE);
        accept.setText(R.string.accept);
        Button cancel = mDialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(R.string.decline);
        mIsVideoAsk = true;

        accept.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int camera =
                                getPackageManager()
                                        .checkPermission(
                                                Manifest.permission.CAMERA, getPackageName());
                        Log.i(
                                "[Permission] Camera permission is "
                                        + (camera == PackageManager.PERMISSION_GRANTED
                                                ? "granted"
                                                : "denied"));

                        if (camera == PackageManager.PERMISSION_GRANTED) {
                            acceptCallUpdate(true);
                        } else {
                            checkAndRequestPermission(
                                    Manifest.permission.CAMERA, PERMISSIONS_REQUEST_CAMERA);
                        }
                        mIsVideoAsk = false;
                        mDialog.dismiss();
                        mDialog = null;
                    }
                });

        cancel.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        acceptCallUpdate(false);
                        mIsVideoAsk = false;
                        mDialog.dismiss();
                        mDialog = null;
                    }
                });
        mDialog.show();
    }

    @Override
    protected void onResume() {

        sInstance = this;
        super.onResume();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
        mIsSpeakerEnabled = LinphoneManager.getInstance().isSpeakerEnabled();

        refreshIncallUi();
        handleViewIntent();

        if (mStatus != null && lc != null) {
            Call currentCall = lc.getCurrentCall();
            if (currentCall != null && !currentCall.getAuthenticationTokenVerified()) {
                mStatus.showZRTPDialog(currentCall);
            }
        }

        if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            if (!mIsSpeakerEnabled) {
                LinphoneManager.getInstance().enableProximitySensing(true);
                removeCallbacks();
            }
        }
    }

    private void handleViewIntent() {
        Intent intent = getIntent();
        if (intent != null && "android.intent.action.VIEW".equals(intent.getAction())) {
            Call call = LinphoneManager.getLc().getCurrentCall();
            if (call != null && isVideoEnabled(call)) {
                Player player = call.getPlayer();
                String path = intent.getData().getPath();
                Log.i("Openning " + path);
                /*int openRes = */ player.open(path);
                /*if(openRes == -1) {
                	String message = "Could not open " + path;
                	Log.e(message);
                	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                	return;
                }*/
                Log.i("Start playing");
                /*if(*/
                player.start() /* == -1) {*/;
                /*player.close();
                	String message = "Could not start playing " + path;
                	Log.e(message);
                	Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                }*/
            }
        }
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();

        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;
    }

    @Override
    protected void onDestroy() {
        LinphoneManager.getInstance().changeStatusToOnline();
        LinphoneManager.getInstance().enableProximitySensing(false);

        unregisterReceiver(mHeadsetReceiver);

        if (mControlsHandler != null && mControls != null) {
            mControlsHandler.removeCallbacks(mControls);
        }
        mControls = null;
        mControlsHandler = null;

        unbindDrawables(findViewById(R.id.topLayout));
        if (mTimer != null) {
            mTimer.cancel();
        }
        sInstance = null;
        super.onDestroy();
        System.gc();
    }

    private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ImageView) {
            view.setOnClickListener(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
        if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override // Never invoke actually
    public void onBackPressed() {
        if (mDialog != null) {
            acceptCallUpdate(false);
            mDialog.dismiss();
            mDialog = null;
        }
    }

    public void bindAudioFragment(CallAudioFragment fragment) {
        mAudioCallFragment = fragment;
    }

    public void bindVideoFragment(CallVideoFragment fragment) {
        mVideoCallFragment = fragment;
    }

    // CALL INFORMATION
    private void displayCurrentCall(Call call) {
        Address lAddress = call.getRemoteAddress();
        TextView contactName = findViewById(R.id.current_contact_name);
        setContactInformation(contactName, lAddress);
        registerCallDurationTimer(null, call);
    }

    private void displayPausedCalls(final Call call, int index) {
        // Control Row
        LinearLayout callView;

        if (call == null) {
            callView =
                    (LinearLayout)
                            mInflater.inflate(R.layout.conference_paused_row, mContainer, false);
            callView.setId(index + 1);
            callView.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            pauseOrResumeConference();
                        }
                    });
        } else {
            callView =
                    (LinearLayout) mInflater.inflate(R.layout.call_inactive_row, mContainer, false);
            callView.setId(index + 1);

            TextView contactName = callView.findViewById(R.id.contact_name);

            Address lAddress = call.getRemoteAddress();
            LinphoneContact lContact =
                    ContactsManager.getInstance().findContactFromAddress(lAddress);

            if (lContact == null) {
                String displayName = LinphoneUtils.getAddressDisplayName(lAddress);
                contactName.setText(displayName);
                ContactAvatar.displayAvatar(displayName, callView.findViewById(R.id.avatar_layout));
            } else {
                contactName.setText(lContact.getFullName());
                ContactAvatar.displayAvatar(lContact, callView.findViewById(R.id.avatar_layout));
            }

            displayCallStatusIconAndReturnCallPaused(callView, call);
            registerCallDurationTimer(callView, call);
        }
        mCallsList.addView(callView);
    }

    private void setContactInformation(TextView contactName, Address lAddress) {
        LinphoneContact lContact = ContactsManager.getInstance().findContactFromAddress(lAddress);
        if (lContact == null) {
            String displayName = LinphoneUtils.getAddressDisplayName(lAddress);
            contactName.setText(displayName);
            ContactAvatar.displayAvatar(displayName, mAvatarLayout, true);
        } else {
            contactName.setText(lContact.getFullName());
            ContactAvatar.displayAvatar(lContact, mAvatarLayout, true);
        }
    }

    private void displayCallStatusIconAndReturnCallPaused(LinearLayout callView, Call call) {
        ImageView onCallStateChanged = callView.findViewById(R.id.call_pause);
        onCallStateChanged.setTag(call);
        onCallStateChanged.setOnClickListener(this);

        if (call.getState() == State.Paused
                || call.getState() == State.PausedByRemote
                || call.getState() == State.Pausing) {
            onCallStateChanged.setSelected(false);
        } else if (call.getState() == State.OutgoingInit
                || call.getState() == State.OutgoingProgress
                || call.getState() == State.OutgoingRinging) {
        }
    }

    private void registerCallDurationTimer(View v, Call call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != State.StreamsRunning) {
            return;
        }

        Chronometer timer;
        if (v == null) {
            timer = findViewById(R.id.current_call_timer);
        } else {
            timer = v.findViewById(R.id.call_timer);
        }

        if (timer == null) {
            throw new IllegalArgumentException("no callee_duration view found");
        }

        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    private void refreshCallList() {
        mIsConferenceRunning = LinphoneManager.getLc().isInConference();
        List<Call> pausedCalls =
                LinphoneUtils.getCallsInState(
                        LinphoneManager.getLc(), Collections.singletonList(State.PausedByRemote));

        // MultiCalls
        if (LinphoneManager.getLc().getCallsNb() > 1) {
            mCallsList.setVisibility(View.VISIBLE);
        }

        // Active call
        if (LinphoneManager.getLc().getCurrentCall() != null) {
            displayNoCurrentCall(false);
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())
                    && !mIsConferenceRunning
                    && pausedCalls.size() == 0) {
                displayVideoCall(false);
            } else {
                displayAudioCall();
            }
        } else {
            showAudioView();
            displayNoCurrentCall(true);
            if (LinphoneManager.getLc().getCallsNb() == 1) {
                mCallsList.setVisibility(View.VISIBLE);
            }
        }

        // Conference
        if (mIsConferenceRunning) {
            displayConference(true);
        } else {
            displayConference(false);
        }

        if (mCallsList != null) {
            mCallsList.removeAllViews();
            int index = 0;

            if (LinphoneManager.getLc().getCallsNb() == 0) {
                goBackToDialer();
                return;
            }

            boolean isConfPaused = false;
            for (Call call : LinphoneManager.getLc().getCalls()) {
                if (call.getConference() != null && !mIsConferenceRunning) {
                    isConfPaused = true;
                    index++;
                } else {
                    if (call != LinphoneManager.getLc().getCurrentCall()
                            && call.getConference() == null) {
                        displayPausedCalls(call, index);
                        index++;
                    } else {
                        displayCurrentCall(call);
                    }
                }
            }

            if (!mIsConferenceRunning) {
                if (isConfPaused) {
                    mCallsList.setVisibility(View.VISIBLE);
                    displayPausedCalls(null, index);
                }
            }
        }

        // Paused by remote
        if (pausedCalls.size() == 1) {
            displayCallPaused(true);
        } else {
            displayCallPaused(false);
        }
    }

    // Conference
    private void exitConference(final Call call) {
        Core lc = LinphoneManager.getLc();

        if (lc.isInConference()) {
            lc.removeFromConference(call);
            if (lc.getConferenceSize() <= 1) {
                lc.leaveConference();
            }
        }
        refreshIncallUi();
    }

    private void enterConference() {
        LinphoneManager.getLc().addAllToConference();
    }

    private void pauseOrResumeConference() {
        Core lc = LinphoneManager.getLc();
        mConferenceStatus = findViewById(R.id.conference_pause);
        if (mConferenceStatus != null) {
            if (lc.isInConference()) {
                mConferenceStatus.setSelected(true);
                lc.leaveConference();
            } else {
                mConferenceStatus.setSelected(false);
                lc.enterConference();
            }
        }
        refreshCallList();
    }

    private void displayConferenceParticipant(int index, final Call call) {
        LinearLayout confView =
                (LinearLayout) mInflater.inflate(R.layout.conf_call_control_row, mContainer, false);
        mConferenceList.setId(index + 1);
        TextView contact = confView.findViewById(R.id.contactNameOrNumber);

        LinphoneContact lContact =
                ContactsManager.getInstance().findContactFromAddress(call.getRemoteAddress());
        if (lContact == null) {
            contact.setText(call.getRemoteAddress().getUsername());
        } else {
            contact.setText(lContact.getFullName());
        }

        registerCallDurationTimer(confView, call);

        ImageView quitConference = confView.findViewById(R.id.quitConference);
        quitConference.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        exitConference(call);
                    }
                });
        mConferenceList.addView(confView);
    }

    private void displayConferenceHeader() {
        mConferenceList.setVisibility(View.VISIBLE);
        RelativeLayout headerConference =
                (RelativeLayout) mInflater.inflate(R.layout.conference_header, mContainer, false);
        mConferenceStatus = headerConference.findViewById(R.id.conference_pause);
        mConferenceStatus.setOnClickListener(this);
        mConferenceList.addView(headerConference);
    }

    private void displayConference(boolean isInConf) {
        if (isInConf) {
            mControlsLayout.setVisibility(View.VISIBLE);
            mActiveCallHeader.setVisibility(View.GONE);
            mNoCurrentCall.setVisibility(View.GONE);
            mConferenceList.removeAllViews();

            // Conference Header
            displayConferenceHeader();

            // Conference participant
            int index = 1;
            for (Call call : LinphoneManager.getLc().getCalls()) {
                if (call.getConference() != null) {
                    displayConferenceParticipant(index, call);
                    index++;
                }
            }
            mConferenceList.setVisibility(View.VISIBLE);
        } else {
            mConferenceList.setVisibility(View.GONE);
        }
    }

    private void displayMissedChats() {
        int count = LinphoneManager.getInstance().getUnreadMessageCount();

        if (count > 0) {
            mMissedChats.setText(String.valueOf(count));
            mMissedChats.setVisibility(View.VISIBLE);
        } else {
            mMissedChats.clearAnimation();
            mMissedChats.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private void formatText(TextView tv, String name, String value) {
        tv.setText(Html.fromHtml("<b>" + name + " </b>" + value));
    }

    private String getEncoderText(String mime) {
        String ret = mEncoderTexts.get(mime);
        if (ret == null) {
            org.linphone.mediastream.Factory msfactory =
                    LinphoneManager.getLc().getMediastreamerFactory();
            ret = msfactory.getEncoderText(mime);
            mEncoderTexts.put(mime, ret);
        }
        return ret;
    }

    private String getDecoderText(String mime) {
        String ret = mDecoderTexts.get(mime);
        if (ret == null) {
            org.linphone.mediastream.Factory msfactory =
                    LinphoneManager.getLc().getMediastreamerFactory();
            ret = msfactory.getDecoderText(mime);
            mDecoderTexts.put(mime, ret);
        }
        return ret;
    }

    private void displayMediaStats(
            CallParams params,
            CallStats stats,
            PayloadType media,
            View layout,
            TextView title,
            TextView codec,
            TextView dl,
            TextView ul,
            TextView edl,
            TextView ice,
            TextView ip,
            TextView senderLossRate,
            TextView receiverLossRate,
            TextView enc,
            TextView dec,
            TextView videoResolutionSent,
            TextView videoResolutionReceived,
            TextView videoFpsSent,
            TextView videoFpsReceived,
            boolean isVideo,
            TextView jitterBuffer) {
        if (stats != null) {
            String mime = null;

            layout.setVisibility(View.VISIBLE);
            title.setVisibility(TextView.VISIBLE);
            if (media != null) {
                mime = media.getMimeType();
                formatText(
                        codec,
                        getString(R.string.call_stats_codec),
                        mime + " / " + (media.getClockRate() / 1000) + "kHz");
            }
            if (mime != null) {
                formatText(enc, getString(R.string.call_stats_encoder_name), getEncoderText(mime));
                formatText(dec, getString(R.string.call_stats_decoder_name), getDecoderText(mime));
            }
            formatText(
                    dl,
                    getString(R.string.call_stats_download),
                    String.valueOf((int) stats.getDownloadBandwidth()) + " kbits/s");
            formatText(
                    ul,
                    getString(R.string.call_stats_upload),
                    String.valueOf((int) stats.getUploadBandwidth()) + " kbits/s");
            if (isVideo) {
                formatText(
                        edl,
                        getString(R.string.call_stats_estimated_download),
                        String.valueOf(stats.getEstimatedDownloadBandwidth()) + " kbits/s");
            }
            formatText(ice, getString(R.string.call_stats_ice), stats.getIceState().toString());
            formatText(
                    ip,
                    getString(R.string.call_stats_ip),
                    (stats.getIpFamilyOfRemote() == AddressFamily.Inet6)
                            ? "IpV6"
                            : (stats.getIpFamilyOfRemote() == AddressFamily.Inet)
                                    ? "IpV4"
                                    : "Unknown");
            formatText(
                    senderLossRate,
                    getString(R.string.call_stats_sender_loss_rate),
                    new DecimalFormat("##.##").format(stats.getSenderLossRate()) + "%");
            formatText(
                    receiverLossRate,
                    getString(R.string.call_stats_receiver_loss_rate),
                    new DecimalFormat("##.##").format(stats.getReceiverLossRate()) + "%");
            if (isVideo) {
                formatText(
                        videoResolutionSent,
                        getString(R.string.call_stats_video_resolution_sent),
                        "\u2191 " + params.getSentVideoDefinition() != null
                                ? params.getSentVideoDefinition().getName()
                                : "");
                formatText(
                        videoResolutionReceived,
                        getString(R.string.call_stats_video_resolution_received),
                        "\u2193 " + params.getReceivedVideoDefinition() != null
                                ? params.getReceivedVideoDefinition().getName()
                                : "");
                formatText(
                        videoFpsSent,
                        getString(R.string.call_stats_video_fps_sent),
                        "\u2191 " + params.getSentFramerate());
                formatText(
                        videoFpsReceived,
                        getString(R.string.call_stats_video_fps_received),
                        "\u2193 " + params.getReceivedFramerate());
            } else {
                formatText(
                        jitterBuffer,
                        getString(R.string.call_stats_jitter_buffer),
                        new DecimalFormat("##.##").format(stats.getJitterBufferSizeMs()) + " ms");
            }
        } else {
            layout.setVisibility(View.GONE);
            title.setVisibility(TextView.GONE);
        }
    }

    private void initCallStatsRefresher(final Call call, final View view) {
        if (mCallDisplayedInStats == call) return;

        if (mTimer != null && mTask != null) {
            mTimer.cancel();
            mTimer = null;
            mTask = null;
        }
        mCallDisplayedInStats = call;

        if (call == null) return;

        final TextView titleAudio = view.findViewById(R.id.call_stats_audio);
        final TextView titleVideo = view.findViewById(R.id.call_stats_video);
        final TextView codecAudio = view.findViewById(R.id.codec_audio);
        final TextView codecVideo = view.findViewById(R.id.codec_video);
        final TextView encoderAudio = view.findViewById(R.id.encoder_audio);
        final TextView decoderAudio = view.findViewById(R.id.decoder_audio);
        final TextView encoderVideo = view.findViewById(R.id.encoder_video);
        final TextView decoderVideo = view.findViewById(R.id.decoder_video);
        final TextView displayFilter = view.findViewById(R.id.display_filter);
        final TextView dlAudio = view.findViewById(R.id.downloadBandwith_audio);
        final TextView ulAudio = view.findViewById(R.id.uploadBandwith_audio);
        final TextView dlVideo = view.findViewById(R.id.downloadBandwith_video);
        final TextView ulVideo = view.findViewById(R.id.uploadBandwith_video);
        final TextView edlVideo = view.findViewById(R.id.estimatedDownloadBandwidth_video);
        final TextView iceAudio = view.findViewById(R.id.ice_audio);
        final TextView iceVideo = view.findViewById(R.id.ice_video);
        final TextView videoResolutionSent = view.findViewById(R.id.video_resolution_sent);
        final TextView videoResolutionReceived = view.findViewById(R.id.video_resolution_received);
        final TextView videoFpsSent = view.findViewById(R.id.video_fps_sent);
        final TextView videoFpsReceived = view.findViewById(R.id.video_fps_received);
        final TextView senderLossRateAudio = view.findViewById(R.id.senderLossRateAudio);
        final TextView receiverLossRateAudio = view.findViewById(R.id.receiverLossRateAudio);
        final TextView senderLossRateVideo = view.findViewById(R.id.senderLossRateVideo);
        final TextView receiverLossRateVideo = view.findViewById(R.id.receiverLossRateVideo);
        final TextView ipAudio = view.findViewById(R.id.ip_audio);
        final TextView ipVideo = view.findViewById(R.id.ip_video);
        final TextView jitterBufferAudio = view.findViewById(R.id.jitterBufferAudio);
        final View videoLayout = view.findViewById(R.id.callStatsVideo);
        final View audioLayout = view.findViewById(R.id.callStatsAudio);

        mCallListener =
                new CallListenerStub() {
                    public void onStateChanged(Call call, Call.State cstate, String message) {
                        if (cstate == Call.State.End || cstate == Call.State.Error) {
                            if (mTimer != null) {
                                Log.i(
                                        "Call is terminated, stopping mCountDownTimer in charge of stats refreshing.");
                                mTimer.cancel();
                            }
                        }
                    }
                };

        mTimer = new Timer();
        mTask =
                new TimerTask() {
                    @Override
                    public void run() {
                        if (call == null) {
                            mTimer.cancel();
                            return;
                        }

                        if (titleAudio == null
                                || codecAudio == null
                                || dlVideo == null
                                || edlVideo == null
                                || iceAudio == null
                                || videoResolutionSent == null
                                || videoLayout == null
                                || titleVideo == null
                                || ipVideo == null
                                || ipAudio == null
                                || codecVideo == null
                                || dlAudio == null
                                || ulAudio == null
                                || ulVideo == null
                                || iceVideo == null
                                || videoResolutionReceived == null) {
                            mTimer.cancel();
                            return;
                        }

                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                                                == null) return;
                                        synchronized (LinphoneManager.getLc()) {
                                            if (LinphoneActivity.isInstanciated()
                                                    && call.getState() != Call.State.Released) {
                                                CallParams params = call.getCurrentParams();
                                                if (params != null) {
                                                    CallStats audioStats =
                                                            call.getStats(StreamType.Audio);
                                                    CallStats videoStats = null;

                                                    if (params.videoEnabled())
                                                        videoStats =
                                                                call.getStats(StreamType.Video);

                                                    PayloadType payloadAudio =
                                                            params.getUsedAudioPayloadType();
                                                    PayloadType payloadVideo =
                                                            params.getUsedVideoPayloadType();

                                                    formatText(
                                                            displayFilter,
                                                            getString(
                                                                    R.string
                                                                            .call_stats_display_filter),
                                                            call.getCore().getVideoDisplayFilter());

                                                    displayMediaStats(
                                                            params,
                                                            audioStats,
                                                            payloadAudio,
                                                            audioLayout,
                                                            titleAudio,
                                                            codecAudio,
                                                            dlAudio,
                                                            ulAudio,
                                                            null,
                                                            iceAudio,
                                                            ipAudio,
                                                            senderLossRateAudio,
                                                            receiverLossRateAudio,
                                                            encoderAudio,
                                                            decoderAudio,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            false,
                                                            jitterBufferAudio);

                                                    displayMediaStats(
                                                            params,
                                                            videoStats,
                                                            payloadVideo,
                                                            videoLayout,
                                                            titleVideo,
                                                            codecVideo,
                                                            dlVideo,
                                                            ulVideo,
                                                            edlVideo,
                                                            iceVideo,
                                                            ipVideo,
                                                            senderLossRateVideo,
                                                            receiverLossRateVideo,
                                                            encoderVideo,
                                                            decoderVideo,
                                                            videoResolutionSent,
                                                            videoResolutionReceived,
                                                            videoFpsSent,
                                                            videoFpsReceived,
                                                            true,
                                                            null);
                                                }
                                            }
                                        }
                                    }
                                });
                    }
                };
        call.addListener(mCallListener);
        mTimer.scheduleAtFixedRate(mTask, 0, 1000);
    }

    //// Earset Connectivity Broadcast innerClass
    public class HeadsetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
                if (intent.hasExtra("state")) {
                    switch (intent.getIntExtra("state", 0)) {
                        case 0:
                            if (mOldIsSpeakerEnabled) {
                                LinphoneManager.getInstance().routeAudioToSpeaker();
                                mIsSpeakerEnabled = true;
                                mSpeaker.setEnabled(true);
                            }
                            break;
                        case 1:
                            LinphoneManager.getInstance().routeAudioToReceiver();
                            mOldIsSpeakerEnabled = mIsSpeakerEnabled;
                            mIsSpeakerEnabled = false;
                            mSpeaker.setEnabled(false);
                            break;
                    }
                    refreshInCallActions();
                }
            }
        }
    }
}
