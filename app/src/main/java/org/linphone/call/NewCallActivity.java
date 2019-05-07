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
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.DialerActivity;
import org.linphone.activities.ThemableActivity;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class NewCallActivity extends ThemableActivity
        implements CallStatusBarFragment.StatsClikedListener {
    private TextureView mLocalPreview, mRemoteVideo;
    private RelativeLayout mActiveCalls;
    private LinearLayout mMenu;

    private ImageView mMicro, mSpeaker, mVideo;

    private CallStatusBarFragment mStatusBarFragment;
    private CallStatsFragment mStatsFragment;
    private CoreListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Compatibility.setShowWhenLocked(this, true);

        setContentView(R.layout.call);

        mLocalPreview = findViewById(R.id.local_preview_texture);
        mRemoteVideo = findViewById(R.id.remote_video_texture);

        mActiveCalls = findViewById(R.id.active_calls);
        mMenu = findViewById(R.id.menu);

        mMicro = findViewById(R.id.micro);
        mSpeaker = findViewById(R.id.speaker);
        mVideo = findViewById(R.id.video);

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
                        // TODO: update unread message count
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

                        } else if (state == Call.State.Resuming) {
                            updateInterfaceDependingOnVideo(call);
                        } else if (state == Call.State.StreamsRunning) {
                            updateInterfaceDependingOnVideo(call);
                        } else if (state == Call.State.UpdatedByRemote) {
                            updateInterfaceDependingOnVideo(call);
                        }
                    }
                };
    }

    @Override
    protected void onStart() {
        super.onStart();

        Core core = LinphoneManager.getCore();
        core.setNativeVideoWindowId(mRemoteVideo);
        core.setNativePreviewWindowId(mLocalPreview);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }

        updateButtons();

        updateInterfaceDependingOnVideo(core.getCurrentCall());
    }

    @Override
    protected void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Core core = LinphoneManager.getCore();
        core.setNativeVideoWindowId(null);
        core.setNativePreviewWindowId(null);
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
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {}

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

    private void updateButtons() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();

        int recordAudioPermission =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        boolean recordAudioPermissionGranted =
                recordAudioPermission == PackageManager.PERMISSION_GRANTED;

        mMicro.setEnabled(recordAudioPermissionGranted);
        mMicro.setSelected(!recordAudioPermissionGranted || !core.micEnabled());

        mSpeaker.setEnabled(true);
        mSpeaker.setSelected(LinphoneManager.getAudioManager().isAudioRoutedToSpeaker());

        mVideo.setEnabled(LinphonePreferences.instance().isVideoEnabled());
        mVideo.setSelected(call != null && call.getCurrentParams().videoEnabled());
    }

    // VIDEO RELATED

    private void updateInterfaceDependingOnVideo(Call call) {
        if (call == null) return;

        boolean videoEnabled =
                LinphonePreferences.instance().isVideoEnabled()
                        && call.getCurrentParams().videoEnabled();

        findViewById(R.id.status_bar_fragment)
                .setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
        mActiveCalls.setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
        mMenu.setVisibility(videoEnabled ? View.GONE : View.VISIBLE);
        mRemoteVideo.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mLocalPreview.setVisibility(videoEnabled ? View.VISIBLE : View.GONE);
        mVideo.setSelected(videoEnabled);
        LinphoneManager.getInstance().enableProximitySensing(!videoEnabled);

        if (videoEnabled) {
            resizePreview(call);
        }
    }

    private void startVideo(Call call) {
        if (call == null) return;
        if (call.getState() == Call.State.End || call.getState() == Call.State.Released) return;
        if (!LinphonePreferences.instance().isVideoEnabled()
                || call.getCurrentParams().videoEnabled()) {
            return;
        }

        if (!call.getRemoteParams().lowBandwidthEnabled()) {
            LinphoneManager.getCallManager().addVideo();
        } else {
            Toast.makeText(this, getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG).show();
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

    private void switchCamera() {
        try {
            Core core = LinphoneManager.getCore();
            String currentDevice = core.getVideoDevice();
            String[] devices = core.getVideoDevicesList();
            int index = 0;
            for (String d : devices) {
                if (d.equals(currentDevice)) {
                    break;
                }
                index++;
            }

            String newDevice;
            if (index == 1) newDevice = devices[0];
            else if (devices.length > 1) newDevice = devices[1];
            else newDevice = devices[index];
            core.setVideoDevice(newDevice);

            LinphoneManager.getCallManager().updateCall();
        } catch (ArithmeticException ae) {
            Log.e("[Call Activity] [Video] Cannot swtich camera : no camera");
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
}
