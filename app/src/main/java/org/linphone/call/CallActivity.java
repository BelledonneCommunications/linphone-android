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
package org.linphone.call;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import java.lang.ref.WeakReference;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.DialerActivity;
import org.linphone.activities.LinphoneGenericActivity;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.AndroidAudioManager;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class CallActivity extends LinphoneGenericActivity
        implements CallStatusBarFragment.StatsClikedListener,
                ContactsUpdatedListener,
                CallActivityInterface {
    private static final int SECONDS_BEFORE_HIDING_CONTROLS = 4000;
    private static final int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;

    private static final int CAMERA_TO_TOGGLE_VIDEO = 0;
    private static final int MIC_TO_DISABLE_MUTE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_FOR_RECORDING = 2;
    private static final int CAMERA_TO_ACCEPT_UPDATE = 3;

    private static class HideControlsRunnable implements Runnable {
        private WeakReference<CallActivity> mWeakCallActivity;

        public HideControlsRunnable(CallActivity activity) {
            mWeakCallActivity = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            // Make sure that at the time this is executed this is still required
            Call call = LinphoneManager.getCore().getCurrentCall();
            if (call != null && call.getCurrentParams().videoEnabled()) {
                CallActivity activity = mWeakCallActivity.get();
                if (activity != null) activity.updateButtonsVisibility(false);
            }
        }
    }

    private final HideControlsRunnable mHideControlsRunnable = new HideControlsRunnable(this);

    private float mPreviewX, mPreviewY;
    private TextureView mLocalPreview, mRemoteVideo;
    private RelativeLayout mButtons,
            mActiveCalls,
            mContactAvatar,
            mActiveCallHeader,
            mConferenceHeader;
    private LinearLayout mCallsList, mCallPausedByRemote, mConferenceList;
    private ImageView mMicro, mSpeaker, mVideo;
    private ImageView mPause, mSwitchCamera, mRecordingInProgress;
    private ImageView mExtrasButtons, mAddCall, mTransferCall, mRecordCall, mConference;
    private ImageView mAudioRoute, mRouteEarpiece, mRouteSpeaker, mRouteBluetooth;
    private TextView mContactName, mMissedMessages;
    private ProgressBar mVideoInviteInProgress;
    private Chronometer mCallTimer;
    private CountDownTimer mCallUpdateCountDownTimer;
    private Dialog mCallUpdateDialog;

    private CallStatsFragment mStatsFragment;
    private Core mCore;
    private CoreListener mListener;
    private AndroidAudioManager mAudioManager;
    private VideoZoomHelper mZoomHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Compatibility.setShowWhenLocked(this, true);

        setContentView(R.layout.call);

        mLocalPreview = findViewById(R.id.local_preview_texture);
        mLocalPreview.setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return moveLocalPreview(v, event);
                    }
                });

        mRemoteVideo = findViewById(R.id.remote_video_texture);
        mRemoteVideo.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        makeButtonsVisibleTemporary();
                    }
                });

        mActiveCalls = findViewById(R.id.active_calls);
        mActiveCallHeader = findViewById(R.id.active_call);
        mCallPausedByRemote = findViewById(R.id.remote_pause);
        mCallsList = findViewById(R.id.calls_list);
        mConferenceList = findViewById(R.id.conference_list);
        mConferenceHeader = findViewById(R.id.conference_header);
        mButtons = findViewById(R.id.buttons);

        ImageView conferencePause = findViewById(R.id.conference_pause);
        conferencePause.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().pauseConference();
                        updateCallsList();
                    }
                });

        mContactName = findViewById(R.id.current_contact_name);
        mContactAvatar = findViewById(R.id.avatar_layout);
        mCallTimer = findViewById(R.id.current_call_timer);

        mVideoInviteInProgress = findViewById(R.id.video_in_progress);
        mVideo = findViewById(R.id.video);
        mVideo.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (checkAndRequestPermission(
                                Manifest.permission.CAMERA, CAMERA_TO_TOGGLE_VIDEO)) {
                            toggleVideo();
                        }
                    }
                });

        mMicro = findViewById(R.id.micro);
        mMicro.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (checkAndRequestPermission(
                                Manifest.permission.RECORD_AUDIO, MIC_TO_DISABLE_MUTE)) {
                            toggleMic();
                        }
                    }
                });

        mSpeaker = findViewById(R.id.speaker);
        mSpeaker.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleSpeaker();
                    }
                });

        mAudioRoute = findViewById(R.id.audio_route);
        mAudioRoute.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleAudioRouteButtons();
                    }
                });

        mRouteEarpiece = findViewById(R.id.route_earpiece);
        mRouteEarpiece.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAudioManager.routeAudioToEarPiece();
                        updateAudioRouteButtons();
                    }
                });

        mRouteSpeaker = findViewById(R.id.route_speaker);
        mRouteSpeaker.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAudioManager.routeAudioToSpeaker();
                        updateAudioRouteButtons();
                    }
                });

        mRouteBluetooth = findViewById(R.id.route_bluetooth);
        mRouteBluetooth.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAudioManager.routeAudioToBluetooth();
                        updateAudioRouteButtons();
                    }
                });

        mExtrasButtons = findViewById(R.id.options);
        mExtrasButtons.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleExtrasButtons();
                    }
                });
        mExtrasButtons.setSelected(false);
        mExtrasButtons.setEnabled(!getResources().getBoolean(R.bool.disable_options_in_call));

        mAddCall = findViewById(R.id.add_call);
        mAddCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBackToDialer();
                    }
                });

        mTransferCall = findViewById(R.id.transfer);
        mTransferCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBackToDialerAndDisplayTransferButton();
                    }
                });
        mTransferCall.setEnabled(getResources().getBoolean(R.bool.allow_transfers));

        mConference = findViewById(R.id.conference);
        mConference.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCore.addAllToConference();
                    }
                });

        mRecordCall = findViewById(R.id.record_call);
        mRecordCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (checkAndRequestPermission(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                WRITE_EXTERNAL_STORAGE_FOR_RECORDING)) {
                            toggleRecording();
                        }
                    }
                });

        ImageView numpadButton = findViewById(R.id.dialer);
        numpadButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        findViewById(R.id.numpad)
                                .setVisibility(
                                        findViewById(R.id.numpad).getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                    }
                });

        ImageView hangUp = findViewById(R.id.hang_up);
        hangUp.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().terminateCurrentCallOrConferenceOrAll();
                    }
                });

        ImageView chat = findViewById(R.id.chat);
        chat.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goToChatList();
                    }
                });

        mPause = findViewById(R.id.pause);
        mPause.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        togglePause(mCore.getCurrentCall());
                    }
                });

        mSwitchCamera = findViewById(R.id.switchCamera);
        mSwitchCamera.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().switchCamera();
                    }
                });

        mRecordingInProgress = findViewById(R.id.recording);
        mRecordingInProgress.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleRecording();
                    }
                });

        mMissedMessages = findViewById(R.id.missed_chats);

        DrawerLayout sideMenu = findViewById(R.id.side_menu);
        RelativeLayout sideMenuContent = findViewById(R.id.side_menu_content);
        mStatsFragment =
                (CallStatsFragment) getFragmentManager().findFragmentById(R.id.call_stats_fragment);
        mStatsFragment.setDrawer(sideMenu, sideMenuContent);

        CallStatusBarFragment statusBarFragment =
                (CallStatusBarFragment)
                        getFragmentManager().findFragmentById(R.id.status_bar_fragment);
        statusBarFragment.setStatsListener(this);

        mZoomHelper = new VideoZoomHelper(this, mRemoteVideo);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onMessageReceived(Core core, ChatRoom cr, ChatMessage message) {
                        updateMissedChatCount();
                    }

                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Released) {
                            if (core.getCallsNb() == 0) {
                                finish();
                            }
                        } else if (state == Call.State.PausedByRemote) {
                            if (core.getCurrentCall() != null) {
                                showVideoControls(false);
                                mCallPausedByRemote.setVisibility(View.VISIBLE);
                            }
                        } else if (state == Call.State.Pausing || state == Call.State.Paused) {
                            if (core.getCurrentCall() != null) {
                                showVideoControls(false);
                            }
                        } else if (state == Call.State.StreamsRunning) {
                            mCallPausedByRemote.setVisibility(View.GONE);

                            setCurrentCallContactInformation();
                            updateInterfaceDependingOnVideo();
                        } else if (state == Call.State.UpdatedByRemote) {
                            // If the correspondent asks for video while in audio call
                            boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
                            if (!videoEnabled) {
                                // Video is disabled globally, don't even ask user
                                acceptCallUpdate(false);
                                return;
                            }

                            boolean showAcceptUpdateDialog =
                                    LinphoneManager.getCallManager()
                                            .shouldShowAcceptCallUpdateDialog(call);
                            if (showAcceptUpdateDialog) {
                                showAcceptCallUpdateDialog();
                                createTimerForDialog(SECONDS_BEFORE_DENYING_CALL_UPDATE);
                            }
                        }

                        updateButtons();
                        updateCallsList();
                    }
                };

        mCore = LinphoneManager.getCore();
        if (mCore != null) {
            boolean recordAudioPermissionGranted =
                    checkPermission(Manifest.permission.RECORD_AUDIO);
            if (!recordAudioPermissionGranted) {
                Log.w("[Call Activity] RECORD_AUDIO permission denied, muting microphone");
                mCore.enableMic(false);
            }

            Call call = mCore.getCurrentCall();
            boolean videoEnabled =
                    LinphonePreferences.instance().isVideoEnabled()
                            && call.getCurrentParams().videoEnabled();

            if (videoEnabled) {
                mAudioManager = LinphoneManager.getAudioManager();
                mAudioManager.routeAudioToSpeaker();
                mSpeaker.setSelected(true);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mCore = LinphoneManager.getCore();
        if (mCore != null) {
            mCore.setNativeVideoWindowId(mRemoteVideo);
            mCore.setNativePreviewWindowId(mLocalPreview);
            mCore.addListener(mListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mAudioManager = LinphoneManager.getAudioManager();

        updateButtons();
        updateMissedChatCount();
        updateInterfaceDependingOnVideo();

        updateCallsList();
        ContactsManager.getInstance().addContactsListener(this);
        LinphoneManager.getCallManager().setCallInterface(this);

        if (mCore.getCallsNb() == 0) {
            Log.w("[Call Activity] Resuming but no call found...");
            finish();
        }

        LinphoneService.instance().destroyOverlay();
    }

    @Override
    protected void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        LinphoneManager.getCallManager().setCallInterface(null);

        Core core = LinphoneManager.getCore();
        if (LinphonePreferences.instance().isOverlayEnabled()
                && core != null
                && core.getCurrentCall() != null) {
            Call call = core.getCurrentCall();
            if (call.getState() == Call.State.StreamsRunning) {
                // Prevent overlay creation if video call is paused by remote
                LinphoneService.instance().createOverlay();
            }
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
            core.setNativeVideoWindowId(null);
            core.setNativePreviewWindowId(null);
        }

        if (mZoomHelper != null) {
            mZoomHelper.destroy();
            mZoomHelper = null;
        }
        if (mCallUpdateCountDownTimer != null) {
            mCallUpdateCountDownTimer.cancel();
            mCallUpdateCountDownTimer = null;
        }

        mCallTimer.stop();
        mCallTimer = null;

        mListener = null;
        mLocalPreview = null;
        mRemoteVideo = null;
        mStatsFragment = null;

        mButtons = null;
        mActiveCalls = null;
        mContactAvatar = null;
        mActiveCallHeader = null;
        mConferenceHeader = null;
        mCallsList = null;
        mCallPausedByRemote = null;
        mConferenceList = null;
        mMicro = null;
        mSpeaker = null;
        mVideo = null;
        mPause = null;
        mSwitchCamera = null;
        mRecordingInProgress = null;
        mExtrasButtons = null;
        mAddCall = null;
        mTransferCall = null;
        mRecordCall = null;
        mConference = null;
        mAudioRoute = null;
        mRouteEarpiece = null;
        mRouteSpeaker = null;
        mRouteBluetooth = null;
        mContactName = null;
        mMissedMessages = null;
        mVideoInviteInProgress = null;
        mCallUpdateDialog = null;

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mAudioManager.onKeyVolumeAdjust(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onStatsClicked() {
        if (mStatsFragment.isOpened()) {
            mStatsFragment.openOrCloseSideMenu(false, true);
        } else {
            mStatsFragment.openOrCloseSideMenu(true, true);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Permission not granted, won't change anything
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) return;

        switch (requestCode) {
            case CAMERA_TO_TOGGLE_VIDEO:
                LinphoneUtils.reloadVideoDevices();
                toggleVideo();
                break;
            case MIC_TO_DISABLE_MUTE:
                toggleMic();
                break;
            case WRITE_EXTERNAL_STORAGE_FOR_RECORDING:
                toggleRecording();
                break;
            case CAMERA_TO_ACCEPT_UPDATE:
                LinphoneUtils.reloadVideoDevices();
                acceptCallUpdate(true);
                break;
        }
    }

    private boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkAndRequestPermission(String permission, int result) {
        if (!checkPermission(permission)) {
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[] {permission}, result);
            return false;
        }
        return true;
    }

    @Override
    public void onContactsUpdated() {
        setCurrentCallContactInformation();
    }

    @Override
    public void onUserLeaveHint() {
        if (mCore == null) return;
        Call call = mCore.getCurrentCall();
        if (call == null) return;
        boolean videoEnabled =
                LinphonePreferences.instance().isVideoEnabled()
                        && call.getCurrentParams().videoEnabled();
        if (videoEnabled && getResources().getBoolean(R.bool.allow_pip_while_video_call)) {
            Compatibility.enterPipMode(this);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, Configuration newConfig) {
        if (isInPictureInPictureMode) {
            updateButtonsVisibility(false);
        }
    }

    @Override
    public void refreshInCallActions() {
        updateButtons();
    }

    @Override
    public void resetCallControlsHidingTimer() {
        LinphoneUtils.removeFromUIThreadDispatcher(mHideControlsRunnable);
        LinphoneUtils.dispatchOnUIThreadAfter(
                mHideControlsRunnable, SECONDS_BEFORE_HIDING_CONTROLS);
    }

    // BUTTONS

    private void updateAudioRouteButtons() {
        mRouteSpeaker.setSelected(mAudioManager.isAudioRoutedToSpeaker());
        mRouteBluetooth.setSelected(mAudioManager.isUsingBluetoothAudioRoute());
        mRouteEarpiece.setSelected(mAudioManager.isAudioRoutedToEarpiece());
    }

    private void updateButtons() {
        Call call = mCore.getCurrentCall();

        mMicro.setSelected(!mCore.micEnabled());

        mSpeaker.setSelected(mAudioManager.isAudioRoutedToSpeaker());

        updateAudioRouteButtons();

        boolean isBluetoothAvailable = mAudioManager.isBluetoothHeadsetConnected();
        mSpeaker.setVisibility(isBluetoothAvailable ? View.GONE : View.VISIBLE);
        mAudioRoute.setVisibility(isBluetoothAvailable ? View.VISIBLE : View.GONE);

        mVideo.setEnabled(
                LinphonePreferences.instance().isVideoEnabled()
                        && call != null
                        && !call.mediaInProgress());
        mVideo.setSelected(call != null && call.getCurrentParams().videoEnabled());
        mSwitchCamera.setVisibility(
                call != null && call.getCurrentParams().videoEnabled()
                        ? View.VISIBLE
                        : View.INVISIBLE);

        mPause.setEnabled(call != null && !call.mediaInProgress());

        mRecordCall.setSelected(call != null && call.isRecording());
        mRecordingInProgress.setVisibility(
                call != null && call.isRecording() ? View.VISIBLE : View.GONE);

        mConference.setEnabled(
                mCore.getCallsNb() > 1
                        && mCore.getCallsNb() > mCore.getConferenceSize()
                        && !mCore.soundResourcesLocked());
    }

    private void toggleMic() {
        mCore.enableMic(!mCore.micEnabled());
        mMicro.setSelected(!mCore.micEnabled());
    }

    private void toggleSpeaker() {
        if (mAudioManager.isAudioRoutedToSpeaker()) {
            mAudioManager.routeAudioToEarPiece();
        } else {
            mAudioManager.routeAudioToSpeaker();
        }
        mSpeaker.setSelected(mAudioManager.isAudioRoutedToSpeaker());
    }

    private void toggleVideo() {
        Call call = mCore.getCurrentCall();
        if (call == null) return;

        mVideoInviteInProgress.setVisibility(View.VISIBLE);
        mVideo.setEnabled(false);
        if (call.getCurrentParams().videoEnabled()) {
            LinphoneManager.getCallManager().removeVideo();
        } else {
            LinphoneManager.getCallManager().addVideo();
        }
    }

    private void togglePause(Call call) {
        if (call == null) return;

        if (call == mCore.getCurrentCall()) {
            call.pause();
            mPause.setSelected(true);
        } else if (call.getState() == Call.State.Paused) {
            call.resume();
            mPause.setSelected(false);
        }
    }

    private void toggleAudioRouteButtons() {
        mAudioRoute.setSelected(!mAudioRoute.isSelected());
        mRouteEarpiece.setVisibility(mAudioRoute.isSelected() ? View.VISIBLE : View.GONE);
        mRouteSpeaker.setVisibility(mAudioRoute.isSelected() ? View.VISIBLE : View.GONE);
        mRouteBluetooth.setVisibility(mAudioRoute.isSelected() ? View.VISIBLE : View.GONE);
    }

    private void toggleExtrasButtons() {
        mExtrasButtons.setSelected(!mExtrasButtons.isSelected());
        mAddCall.setVisibility(mExtrasButtons.isSelected() ? View.VISIBLE : View.GONE);
        mTransferCall.setVisibility(mExtrasButtons.isSelected() ? View.VISIBLE : View.GONE);
        mRecordCall.setVisibility(mExtrasButtons.isSelected() ? View.VISIBLE : View.GONE);
        mConference.setVisibility(mExtrasButtons.isSelected() ? View.VISIBLE : View.GONE);
    }

    private void toggleRecording() {
        Call call = mCore.getCurrentCall();
        if (call == null) return;

        if (call.isRecording()) {
            call.stopRecording();
        } else {
            call.startRecording();
        }
        mRecordCall.setSelected(call.isRecording());
        mRecordingInProgress.setVisibility(call.isRecording() ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateMissedChatCount() {
        int count = 0;
        if (mCore != null) {
            count = mCore.getUnreadChatMessageCountFromActiveLocals();
        }

        if (count > 0) {
            mMissedMessages.setText(String.valueOf(count));
            mMissedMessages.setVisibility(View.VISIBLE);
        } else {
            mMissedMessages.clearAnimation();
            mMissedMessages.setVisibility(View.GONE);
        }
    }

    private void updateButtonsVisibility(boolean visible) {
        findViewById(R.id.status_bar_fragment).setVisibility(visible ? View.VISIBLE : View.GONE);
        if (mActiveCalls != null) mActiveCalls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (mButtons != null) mButtons.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void makeButtonsVisibleTemporary() {
        updateButtonsVisibility(true);
        resetCallControlsHidingTimer();
    }

    // VIDEO RELATED

    private void showVideoControls(boolean videoEnabled) {
        mContactAvatar.setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
        mRemoteVideo.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mLocalPreview.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mSwitchCamera.setVisibility(videoEnabled ? View.VISIBLE : View.INVISIBLE);
        updateButtonsVisibility(!videoEnabled);
        mVideo.setSelected(videoEnabled);
        LinphoneManager.getInstance().enableProximitySensing(!videoEnabled);

        if (!videoEnabled) {
            LinphoneUtils.removeFromUIThreadDispatcher(mHideControlsRunnable);
        }
    }

    private void updateInterfaceDependingOnVideo() {
        Call call = mCore.getCurrentCall();
        if (call == null) {
            showVideoControls(false);
            return;
        }

        mVideoInviteInProgress.setVisibility(View.GONE);
        mVideo.setEnabled(
                LinphonePreferences.instance().isVideoEnabled()
                        && call != null
                        && !call.mediaInProgress());

        boolean videoEnabled =
                LinphonePreferences.instance().isVideoEnabled()
                        && call.getCurrentParams().videoEnabled();
        showVideoControls(videoEnabled);
    }

    private boolean moveLocalPreview(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPreviewX = view.getX() - motionEvent.getRawX();
                mPreviewY = view.getY() - motionEvent.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                view.animate()
                        .x(motionEvent.getRawX() + mPreviewX)
                        .y(motionEvent.getRawY() + mPreviewY)
                        .setDuration(0)
                        .start();
                break;
            default:
                return false;
        }
        return true;
    }

    // NAVIGATION

    private void goBackToDialer() {
        Intent intent = new Intent();
        intent.setClass(this, DialerActivity.class);
        intent.putExtra("Transfer", false);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void goBackToDialerAndDisplayTransferButton() {
        Intent intent = new Intent();
        intent.setClass(this, DialerActivity.class);
        intent.putExtra("Transfer", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void goToChatList() {
        Intent intent = new Intent();
        intent.setClass(this, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    // CALL UPDATE

    private void createTimerForDialog(long time) {
        mCallUpdateCountDownTimer =
                new CountDownTimer(time, 1000) {
                    public void onTick(long millisUntilFinished) {}

                    public void onFinish() {
                        if (mCallUpdateDialog != null) {
                            mCallUpdateDialog.dismiss();
                            mCallUpdateDialog = null;
                        }
                        acceptCallUpdate(false);
                    }
                }.start();
    }

    private void acceptCallUpdate(boolean accept) {
        if (mCallUpdateCountDownTimer != null) {
            mCallUpdateCountDownTimer.cancel();
        }
        LinphoneManager.getCallManager().acceptCallUpdate(accept);
    }

    private void showAcceptCallUpdateDialog() {
        mCallUpdateDialog = new Dialog(this);
        mCallUpdateDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mCallUpdateDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        mCallUpdateDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        mCallUpdateDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.dark_grey_color));
        d.setAlpha(200);
        mCallUpdateDialog.setContentView(R.layout.dialog);
        mCallUpdateDialog
                .getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mCallUpdateDialog.getWindow().setBackgroundDrawable(d);

        TextView customText = mCallUpdateDialog.findViewById(R.id.dialog_message);
        customText.setText(getResources().getString(R.string.add_video_dialog));
        mCallUpdateDialog.findViewById(R.id.dialog_delete_button).setVisibility(View.GONE);
        Button accept = mCallUpdateDialog.findViewById(R.id.dialog_ok_button);
        accept.setVisibility(View.VISIBLE);
        accept.setText(R.string.accept);
        Button cancel = mCallUpdateDialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(R.string.decline);

        accept.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (checkPermission(Manifest.permission.CAMERA)) {
                            acceptCallUpdate(true);
                        } else {
                            checkAndRequestPermission(
                                    Manifest.permission.CAMERA, CAMERA_TO_ACCEPT_UPDATE);
                        }
                        mCallUpdateDialog.dismiss();
                        mCallUpdateDialog = null;
                    }
                });

        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        acceptCallUpdate(false);
                        mCallUpdateDialog.dismiss();
                        mCallUpdateDialog = null;
                    }
                });
        mCallUpdateDialog.show();
    }

    // CONFERENCE

    private void displayConferenceCall(final Call call) {
        LinearLayout conferenceCallView =
                (LinearLayout)
                        LayoutInflater.from(this)
                                .inflate(R.layout.call_conference_cell, null, false);

        TextView contactNameView = conferenceCallView.findViewById(R.id.contact_name);
        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAddress(call.getRemoteAddress());
        if (contact != null) {
            ContactAvatar.displayAvatar(
                    contact, conferenceCallView.findViewById(R.id.avatar_layout), true);
            contactNameView.setText(contact.getFullName());
        } else {
            String displayName = LinphoneUtils.getAddressDisplayName(call.getRemoteAddress());
            ContactAvatar.displayAvatar(
                    displayName, conferenceCallView.findViewById(R.id.avatar_layout), true);
            contactNameView.setText(displayName);
        }

        Chronometer timer = conferenceCallView.findViewById(R.id.call_timer);
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * call.getDuration());
        timer.start();

        ImageView removeFromConference =
                conferenceCallView.findViewById(R.id.remove_from_conference);
        removeFromConference.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().removeCallFromConference(call);
                    }
                });

        mConferenceList.addView(conferenceCallView);
    }

    private void displayPausedConference() {
        LinearLayout pausedConferenceView =
                (LinearLayout)
                        LayoutInflater.from(this)
                                .inflate(R.layout.call_conference_paused_cell, null, false);

        ImageView conferenceResume = pausedConferenceView.findViewById(R.id.conference_resume);
        conferenceResume.setSelected(true);
        conferenceResume.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().resumeConference();
                        updateCallsList();
                    }
                });

        mCallsList.addView(pausedConferenceView);
    }

    // OTHER

    private void updateCallsList() {
        Call currentCall = mCore.getCurrentCall();
        if (currentCall != null) {
            setCurrentCallContactInformation();
        }

        boolean callThatIsNotCurrentFound = false;
        boolean pausedConferenceDisplayed = false;
        boolean conferenceDisplayed = false;
        mCallsList.removeAllViews();
        mConferenceList.removeAllViews();

        for (Call call : mCore.getCalls()) {
            if (call.getConference() != null) {
                if (mCore.isInConference()) {
                    displayConferenceCall(call);
                    conferenceDisplayed = true;
                } else if (!pausedConferenceDisplayed) {
                    displayPausedConference();
                    pausedConferenceDisplayed = true;
                }
            } else if (call != currentCall) {
                displayPausedCall(call);
                callThatIsNotCurrentFound = true;
            }
        }

        mCallsList.setVisibility(
                pausedConferenceDisplayed || callThatIsNotCurrentFound ? View.VISIBLE : View.GONE);
        mActiveCallHeader.setVisibility(
                currentCall != null && !conferenceDisplayed ? View.VISIBLE : View.GONE);
        mConferenceHeader.setVisibility(conferenceDisplayed ? View.VISIBLE : View.GONE);
        mConferenceList.setVisibility(mConferenceHeader.getVisibility());
    }

    private void displayPausedCall(final Call call) {
        LinearLayout callView =
                (LinearLayout)
                        LayoutInflater.from(this).inflate(R.layout.call_inactive_row, null, false);

        TextView contactName = callView.findViewById(R.id.contact_name);
        Address address = call.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact == null) {
            String displayName = LinphoneUtils.getAddressDisplayName(address);
            contactName.setText(displayName);
            ContactAvatar.displayAvatar(displayName, callView.findViewById(R.id.avatar_layout));
        } else {
            contactName.setText(contact.getFullName());
            ContactAvatar.displayAvatar(contact, callView.findViewById(R.id.avatar_layout));
        }

        Chronometer timer = callView.findViewById(R.id.call_timer);
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * call.getDuration());
        timer.start();

        ImageView resumeCall = callView.findViewById(R.id.call_pause);
        resumeCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        togglePause(call);
                    }
                });

        mCallsList.addView(callView);
    }

    private void updateCurrentCallTimer() {
        Call call = mCore.getCurrentCall();
        if (call == null) return;

        mCallTimer.setBase(SystemClock.elapsedRealtime() - 1000 * call.getDuration());
        mCallTimer.start();
    }

    private void setCurrentCallContactInformation() {
        updateCurrentCallTimer();

        Call call = mCore.getCurrentCall();
        if (call == null) return;

        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAddress(call.getRemoteAddress());
        if (contact != null) {
            ContactAvatar.displayAvatar(contact, mContactAvatar, true);
            mContactName.setText(contact.getFullName());
        } else {
            String displayName = LinphoneUtils.getAddressDisplayName(call.getRemoteAddress());
            ContactAvatar.displayAvatar(displayName, mContactAvatar, true);
            mContactName.setText(displayName);
        }
    }
}
