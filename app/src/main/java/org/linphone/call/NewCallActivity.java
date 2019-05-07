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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.DialerActivity;
import org.linphone.activities.ThemeableActivity;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.AndroidAudioManager;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;
import org.linphone.views.Numpad;

public class NewCallActivity extends ThemeableActivity
        implements CallStatusBarFragment.StatsClikedListener, ContactsUpdatedListener {
    private static final int SECONDS_BEFORE_HIDING_CONTROLS = 4000;

    private Handler mHandler = new Handler();
    private Runnable mHideControlsRunnable =
            new Runnable() {
                @Override
                public void run() {
                    updateButtonsVisibility(false);
                }
            };

    private int mPreviewX, mPreviewY;
    private TextureView mLocalPreview, mRemoteVideo;
    private RelativeLayout mActiveCalls, mContactAvatar;
    private LinearLayout mMenu;
    private ImageView mMicro, mSpeaker, mVideo;
    private ImageView mNumpadButton, mHangUp, mChat;
    private ImageView mPause, mSwitchCamera, mRecoringInProgress;
    private ImageView mExtrasButtons, mAddCall, mTransferCall, mRecordCall, mConference;
    private Numpad mNumpad;
    private TextView mContactName, mMissedMessages;
    private ProgressBar mVideoInviteInProgress;

    private CallStatusBarFragment mStatusBarFragment;
    private CallStatsFragment mStatsFragment;
    private Core mCore;
    private CoreListener mListener;
    private AndroidAudioManager mAudioManager;

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
                        moveLocalPreview(event);
                        return true;
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
        mMenu = findViewById(R.id.menu);

        mContactName = findViewById(R.id.current_contact_name);
        mContactAvatar = findViewById(R.id.avatar_layout);

        mVideoInviteInProgress = findViewById(R.id.video_in_progress);
        mVideo = findViewById(R.id.video);
        mVideo.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleVideo();
                    }
                });

        mMicro = findViewById(R.id.micro);
        mMicro.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleMic();
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

        mExtrasButtons = findViewById(R.id.options);
        mExtrasButtons.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleExtrasButtons();
                    }
                });
        mExtrasButtons.setSelected(false);

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

        mConference = findViewById(R.id.conference);
        mConference.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO
                    }
                });

        mRecordCall = findViewById(R.id.record_call);
        mRecordCall.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleRecording();
                    }
                });

        mNumpad = findViewById(R.id.numpad);

        mNumpadButton = findViewById(R.id.dialer);
        mNumpadButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mNumpad.setVisibility(
                                mNumpad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                });

        mHangUp = findViewById(R.id.hang_up);
        mHangUp.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().terminateCurrentCallOrConferenceOrAll();
                    }
                });

        mChat = findViewById(R.id.chat);
        mChat.setOnClickListener(
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

        mRecoringInProgress = findViewById(R.id.recording);
        mRecoringInProgress.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleRecording();
                    }
                });

        mMissedMessages = findViewById(R.id.missed_chats);

        DrawerLayout mSideMenu = findViewById(R.id.side_menu);
        RelativeLayout mSideMenuContent = findViewById(R.id.side_menu_content);
        mStatsFragment =
                (CallStatsFragment) getFragmentManager().findFragmentById(R.id.call_stats_fragment);
        mStatsFragment.setDrawer(mSideMenu, mSideMenuContent);

        mStatusBarFragment =
                (CallStatusBarFragment)
                        getFragmentManager().findFragmentById(R.id.status_bar_fragment);
        mStatusBarFragment.setStatsListener(this);

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
                        } else if (state == Call.State.Paused
                                || state == Call.State.PausedByRemote
                                || state == Call.State.Pausing) {

                        } else if (state == Call.State.Resuming
                                || state == Call.State.StreamsRunning
                                || state == Call.State.UpdatedByRemote) {
                            updateButtons();
                            updateInterfaceDependingOnVideo(call);
                        }
                    }
                };
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
        updateInterfaceDependingOnVideo(mCore.getCurrentCall());

        setContactInformation();
        ContactsManager.getInstance().addContactsListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        ContactsManager.getInstance().removeContactsListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
            core.setNativeVideoWindowId(null);
            core.setNativePreviewWindowId(null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LinphoneManager.getAudioManager().onKeyVolumeAdjust(keyCode)) return true;
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
        // TODO
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
    public void onContactsUpdated() {
        setContactInformation();
    }

    @Override
    public void onUserLeaveHint() {
        Call call = mCore.getCurrentCall();
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

    // BUTTONS

    private void updateButtons() {
        Call call = mCore.getCurrentCall();

        int recordAudioPermission =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        boolean recordAudioPermissionGranted =
                recordAudioPermission == PackageManager.PERMISSION_GRANTED;

        mMicro.setEnabled(recordAudioPermissionGranted);
        mMicro.setSelected(!recordAudioPermissionGranted || !mCore.micEnabled());

        mSpeaker.setEnabled(true);
        mSpeaker.setSelected(LinphoneManager.getAudioManager().isAudioRoutedToSpeaker());

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
        mRecoringInProgress.setVisibility(
                call != null && call.isRecording() ? View.VISIBLE : View.GONE);
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
            mHandler.removeCallbacks(mHideControlsRunnable);
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
        mRecoringInProgress.setVisibility(call.isRecording() ? View.VISIBLE : View.INVISIBLE);
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
        mActiveCalls.setVisibility(visible ? View.VISIBLE : View.GONE);
        mMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void makeButtonsVisibleTemporary() {
        updateButtonsVisibility(true);
        mHandler.removeCallbacks(mHideControlsRunnable);
        mHandler.postDelayed(mHideControlsRunnable, SECONDS_BEFORE_HIDING_CONTROLS);
    }

    // VIDEO RELATED

    private void updateInterfaceDependingOnVideo(Call call) {
        if (call == null) return;

        mVideoInviteInProgress.setVisibility(View.GONE);
        mVideo.setEnabled(LinphonePreferences.instance().isVideoEnabled());

        boolean videoEnabled =
                LinphonePreferences.instance().isVideoEnabled()
                        && call.getCurrentParams().videoEnabled();

        mContactAvatar.setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
        mRemoteVideo.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mLocalPreview.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mSwitchCamera.setVisibility(videoEnabled ? View.VISIBLE : View.INVISIBLE);
        updateButtonsVisibility(!videoEnabled);
        mVideo.setSelected(videoEnabled);
        LinphoneManager.getInstance().enableProximitySensing(!videoEnabled);

        if (videoEnabled) {
            mAudioManager.routeAudioToSpeaker();
            resizePreview(call);
        }
    }

    private void resizePreview(Call call) {
        if (call == null) return;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenHeight = metrics.heightPixels;
        int maxHeight =
                screenHeight / 4; // Let's take at most 1/4 of the screen for the camera preview

        VideoDefinition videoSize =
                call.getCurrentParams()
                        .getSentVideoDefinition(); // It already takes care of rotation
        if (videoSize.getWidth() == 0 || videoSize.getHeight() == 0) {
            Log.w(
                    "[Call Activity] [Video] Couldn't get sent video definition, using default video definition");
            videoSize = call.getCore().getPreferredVideoDefinition();
        }
        int width = videoSize.getWidth();
        int height = videoSize.getHeight();

        Log.d("[Call Activity] [Video] Video height is " + height + ", width is " + width);
        width = width * maxHeight / height;
        height = maxHeight;

        if (mLocalPreview == null) {
            Log.e("[Call Activity] [Video] mCaptureView is null !");
            return;
        }

        RelativeLayout.LayoutParams newLp = new RelativeLayout.LayoutParams(width, height);
        newLp.addRule(
                RelativeLayout.ALIGN_PARENT_BOTTOM,
                1); // Clears the rule, as there is no removeRule until API 17.
        newLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 1);
        mLocalPreview.setLayoutParams(newLp);
        Log.d("[Call Activity] [Video] Video preview size set to " + width + "x" + height);
    }

    private void moveLocalPreview(MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPreviewX = (int) motionEvent.getX();
                mPreviewY = (int) motionEvent.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();
                RelativeLayout.LayoutParams lp =
                        (RelativeLayout.LayoutParams) mLocalPreview.getLayoutParams();
                lp.addRule(
                        RelativeLayout.ALIGN_PARENT_BOTTOM,
                        0); // Clears the rule, as there is no removeRule until API
                // 17.
                lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                int left = lp.leftMargin + (x - mPreviewX);
                int top = lp.topMargin + (y - mPreviewY);
                lp.leftMargin = left;
                lp.topMargin = top;
                mLocalPreview.setLayoutParams(lp);
                break;
        }
    }

    // NAVIGATION

    private void goBackToDialer() {
        Intent intent = new Intent();
        intent.setClass(this, DialerActivity.class);
        intent.putExtra("Transfer", false);
        startActivity(intent);
    }

    private void goBackToDialerAndDisplayTransferButton() {
        Intent intent = new Intent();
        intent.setClass(this, DialerActivity.class);
        intent.putExtra("Transfer", true);
        startActivity(intent);
    }

    private void goToChatList() {
        Intent intent = new Intent();
        intent.setClass(this, ChatActivity.class);
        startActivity(intent);
    }

    // CONTACT

    private void setContactInformation() {
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
