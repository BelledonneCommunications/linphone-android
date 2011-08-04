/*
VideoCallActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.linphone;



import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.Version;
import org.linphone.core.VideoSize;
import org.linphone.core.video.AndroidCameraRecordManager;
import org.linphone.core.video.AndroidCameraRecordManager.OnCapturingStateChangedListener;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;

/**
 * For Android SDK >= 5
 * @author Guillaume Beraudo
 *
 */
public class VideoCallActivity extends SoftVolumeActivity implements OnCapturingStateChangedListener {
	private SurfaceView mVideoView;
	private SurfaceView mVideoCaptureView;
	private AndroidCameraRecordManager recordManager;
	public static boolean launched = false;
	private WakeLock mWakeLock;
	private static final int capturePreviewLargestDimension = 150;
	private Handler handler = new Handler();


	public void onCreate(Bundle savedInstanceState) {
		launched = true;
		Log.d("onCreate VideoCallActivity");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videocall);

		mVideoView = (SurfaceView) findViewById(R.id.video_surface); 
		LinphoneCore lc = LinphoneManager.getLc();
		lc.setVideoWindow(mVideoView);

		mVideoCaptureView = (SurfaceView) findViewById(R.id.video_capture_surface);

		recordManager = AndroidCameraRecordManager.getInstance();
		recordManager.setOnCapturingStateChanged(this);
		recordManager.setSurfaceView(mVideoCaptureView);
		mVideoCaptureView.setZOrderOnTop(true);

		if (!recordManager.isMuted()) LinphoneManager.getInstance().sendStaticImage(false);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,Log.TAG);
		mWakeLock.acquire();

		fixScreenOrientationForOldDevices();
	}

	private void fixScreenOrientationForOldDevices() {
		if (Version.sdkAboveOrEqual(Version.API08_FROYO_22)) return;

		// Force to display in portrait orientation for old devices
		// as they do not support surfaceView rotation
		setRequestedOrientation(recordManager.isCameraMountedPortrait() ?
				ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
				ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		resizeCapturePreview(mVideoCaptureView);
	}

	@Override
	protected void onResume() {
		if (Version.sdkAboveOrEqual(8) && recordManager.isOutputOrientationMismatch(LinphoneManager.getLc())) {
			Log.i("Phone orientation has changed: updating call.");
			CallManager.getInstance().updateCall();
			// resizeCapturePreview by callback when recording started
		}
		super.onResume();
	}


	private void rewriteToggleCameraItem(MenuItem item) {
		if (recordManager.isRecording()) {
			item.setTitle(getString(R.string.menu_videocall_toggle_camera_disable));
		} else {
			item.setTitle(getString(R.string.menu_videocall_toggle_camera_enable));
		}
	}


	private void rewriteChangeResolutionItem(MenuItem item) {
		if (BandwidthManager.getInstance().isUserRestriction()) {
			item.setTitle(getString(R.string.menu_videocall_change_resolution_when_low_resolution));
		} else {
			item.setTitle(getString(R.string.menu_videocall_change_resolution_when_high_resolution));
		}
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the currently selected menu XML resource.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.videocall_activity_menu, menu);

		rewriteToggleCameraItem(menu.findItem(R.id.videocall_menu_toggle_camera));
		rewriteChangeResolutionItem(menu.findItem(R.id.videocall_menu_change_resolution));

		if (!recordManager.hasSeveralCameras()) {
			menu.findItem(R.id.videocall_menu_switch_camera).setVisible(false);
		}
		return true;
	}



	/**
	 * Base capture frame on streamed dimensions and orientation.
	 * @param sv capture surface view to resize the layout
	 * @param vs video size from which to calculate the dimensions
	 */
	private void resizeCapturePreview(SurfaceView sv) {
		LayoutParams lp = sv.getLayoutParams();
		VideoSize vs = LinphoneManager.getLc().getPreferredVideoSize();

		float newRatio = (float) vs.width / vs.height;

		if (vs.isPortrait()) {
			lp.height = capturePreviewLargestDimension;
			lp.width = Math.round(lp.height * newRatio);
		} else {
			lp.width = capturePreviewLargestDimension;
			lp.height = Math.round(lp.width / newRatio);
		}

		sv.setLayoutParams(lp);
	}



	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.videocall_menu_back_to_dialer:
			finish();
			break;
		case R.id.videocall_menu_change_resolution:
			LinphoneManager.getInstance().changeResolution();
			rewriteChangeResolutionItem(item);
			break;
		case R.id.videocall_menu_terminate_call:
			LinphoneManager.getInstance().terminateCall();
			break;
		case R.id.videocall_menu_toggle_camera:
			LinphoneManager.getInstance().toggleCameraMuting();
			rewriteToggleCameraItem(item);
			break;
		case R.id.videocall_menu_switch_camera:
			LinphoneManager.getInstance().switchCamera();
			fixScreenOrientationForOldDevices();
			break;
		default:
			Log.e("Unknown menu item [",item,"]");
			break;
		}

		return true;
	}


	@Override
	protected void onDestroy() {
		launched = false;
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d("onPause VideoCallActivity");
		LinphoneManager.getInstance().sendStaticImage(true);
		if (mWakeLock.isHeld())	mWakeLock.release();
		super.onPause();
	}

	public void captureStarted() {
		handler.post(new Runnable() {
			public void run() {
				resizeCapturePreview(mVideoCaptureView);
			}
		});
	}

	public void captureStopped() {
	}

}
