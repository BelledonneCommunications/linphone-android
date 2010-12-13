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



import org.linphone.core.AndroidCameraRecordManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.VideoSize;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;

public class VideoCallActivity extends Activity {
	private SurfaceView mVideoView;
	private SurfaceView mVideoCaptureView;
	private AndroidCameraRecordManager recordManager;
	private static final String tag = "Linphone";
	public static boolean launched = false;
	private WakeLock mWakeLock;
	private static final int capturePreviewLargestDimension = 150;
//	private static final float similarRatio = 0.1f;

	public void onCreate(Bundle savedInstanceState) {
		launched = true;
		Log.d(tag, "onCreate VideoCallActivity");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videocall);

		mVideoView = (SurfaceView) findViewById(R.id.video_surface); 
		LinphoneCore lc = LinphoneService.getLc();
		lc.setVideoWindow(mVideoView);
		
		mVideoCaptureView = (SurfaceView) findViewById(R.id.video_capture_surface);

		final int rotation = getWindowManager().getDefaultDisplay().getOrientation();
		recordManager = AndroidCameraRecordManager.getInstance();
		recordManager.setSurfaceView(mVideoCaptureView, rotation);
		mVideoCaptureView.setZOrderOnTop(true);
		
		if (!recordManager.isMuted()) sendStaticImage(false);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");
		mWakeLock.acquire();
		
		if (Integer.parseInt(Build.VERSION.SDK) < 8) {
			// Force to display in portrait orientation for old devices
			// as they do not support surfaceView rotation
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		// Handle the fact that the preferred size may have a ratio /an orientation different from the one
		// in the videocall.xml as the front camera on Samsung captures in landscape.
		updateSvLayoutParamsFromVideoSize(mVideoCaptureView, lc.getPreferredVideoSize());
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
		
		return true;
	}

	private void sendStaticImage(boolean send) {
		LinphoneCore lc =  LinphoneService.getLc();
		if (lc.isIncall()) {
			lc.getCurrentCall().enableCamera(!send);
		}
	}

	private void updateSvLayoutParamsFromVideoSize(SurfaceView sv, VideoSize vs) {
		LayoutParams lp = sv.getLayoutParams();
		float newRatio = ratioWidthHeight(vs);

		//		float previewRatio = (float) lp.width / lp.height;
//		if (Math.abs((newRatio-previewRatio)/newRatio) < similarRatio) return;

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
			if (!recordManager.isMuted()) sendStaticImage(true);
			finish();
			break;
		case R.id.videocall_menu_change_resolution:
			BandwidthManager manager = BandwidthManager.getInstance();
			manager.setUserRestriction(!manager.isUserRestriction());
			sendStaticImage(recordManager.isMuted());
			rewriteChangeResolutionItem(item);
			
			// Resize preview frame
			VideoSize newVideoSize = LinphoneService.getLc().getPreferredVideoSize();
			updateSvLayoutParamsFromVideoSize(mVideoCaptureView, newVideoSize);
			break;
		case R.id.videocall_menu_terminate_call:
			LinphoneCore lc =  LinphoneService.getLc();
			if (lc.isIncall()) {
				lc.terminateCall(lc.getCurrentCall());
			}
			finish();
			break;
		case R.id.videocall_menu_toggle_camera:
			sendStaticImage(recordManager.toggleMute());
			rewriteToggleCameraItem(item);
			break;
/*		case R.id.videocall_menu_switch_camera:
			recordManager.stopVideoRecording();
			recordManager.toggleUseFrontCamera();
			InviteManager.getInstance().reinvite();
			// camera will be restarted when mediastreamer chain is recreated and setParameters is called
			break;
*/		default:
			Log.e(LinphoneService.TAG, "Unknown menu item ["+item+"]");
			break;
		}

		return false;
	}


	@Override
	protected void onDestroy() {
		launched = false;
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d(tag, "onPause VideoCallActivity");
		if (mWakeLock.isHeld())	mWakeLock.release();
		super.onPause();
	}
	
	
	public float ratioWidthHeight(VideoSize vs) {
		return (float) vs.getWidth() / vs.getHeight();
	}

}
