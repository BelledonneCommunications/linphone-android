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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;

public class VideoCallActivity extends Activity {
	SurfaceView mVideoView;
	SurfaceView mVideoCaptureView;
	AndroidCameraRecordManager recordManager;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videocall);

		mVideoView = (SurfaceView) findViewById(R.id.video_surface); 
		LinphoneService.instance().getLinphoneCore().setVideoWindow((Object) mVideoView);
		
		mVideoCaptureView = (SurfaceView) findViewById(R.id.video_capture_surface);
		
		final int rotation = getWindowManager().getDefaultDisplay().getRotation();
		recordManager = AndroidCameraRecordManager.getInstance(AndroidCameraRecordManager.CAMERA_ID_FIXME_USE_PREFERENCE);
		recordManager.setSurfaceView(mVideoCaptureView, rotation);
		mVideoCaptureView.setZOrderOnTop(true);
	}
	

	private void rewriteToggleCameraItem(MenuItem item) {
		if (recordManager.isRecording()) {
			item.setTitle(getString(R.string.menu_videocall_toggle_camera_disable));
		} else {
			item.setTitle(getString(R.string.menu_videocall_toggle_camera_enable));
		}
	}

	private void rewriteChangeResolutionItem(MenuItem item) {
		switch (BandwidthManager.getInstance().getCurrentProfile()) {
		case BandwidthManager.HIGH_RESOLUTION:
			item.setTitle(getString(R.string.menu_videocall_change_resolution_when_high_resolution));
			break;
		case BandwidthManager.LOW_RESOLUTION:
			item.setTitle(getString(R.string.menu_videocall_change_resolution_when_low_resolution));
			break;
		default:
			throw new RuntimeException("Current profile is unknown " + BandwidthManager.getInstance().getCurrentProfile());
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


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.videocall_menu_back_to_dialer:
			finish();
			break;
		case R.id.videocall_menu_change_resolution:
			BandwidthManager manager = BandwidthManager.getInstance();
			switch (manager.getCurrentProfile()) {
			case BandwidthManager.HIGH_RESOLUTION:
				manager.changeTo(BandwidthManager.LOW_RESOLUTION);
				break;
			case BandwidthManager.LOW_RESOLUTION:
				manager.changeTo(BandwidthManager.HIGH_RESOLUTION);
				break;
			default:
				throw new RuntimeException("Current profile is unknown " + manager.getCurrentProfile());
			}
			
			rewriteChangeResolutionItem(item);
			break;
		case R.id.videocall_menu_terminate_call:
			LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
			if (lLinphoneCore.isIncall()) {
				lLinphoneCore.terminateCall(lLinphoneCore.getCurrentCall());
			}
			finish();
			break;
		case R.id.videocall_menu_toggle_camera:
			recordManager.toggleMute();
			rewriteToggleCameraItem(item);
			break;
		default:
			Log.e(LinphoneService.TAG, "Unknown menu item ["+item+"]");
			break;
		}

		return false;
	}
	protected void startprefActivity() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClass(this, LinphonePreferencesActivity.class);
		startActivity(intent);
	}
	
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
	

}
