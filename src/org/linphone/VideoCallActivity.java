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



import org.linphone.core.AndroidCameraRecord;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

public class VideoCallActivity extends Activity {
	SurfaceView mVideoView;
	SurfaceView mVideoCaptureView;
	private Handler mHandler = new Handler() ;
	private static boolean firstLaunch = true;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videocall);

		mVideoView = (SurfaceView) findViewById(R.id.video_surface); 
		LinphoneService.instance().getLinphoneCore().setVideoWindow((Object) mVideoView);
		
		mVideoCaptureView = (SurfaceView) findViewById(R.id.video_capture_surface);
		
		final int rotation = getWindowManager().getDefaultDisplay().getRotation();
		AndroidCameraRecord.setOrientationCode(rotation);
		
		if (!firstLaunch) workaroundCapturePreviewHiddenOnSubsequentRotations();
		
		AndroidCameraRecord.setSurfaceView(mVideoCaptureView, mHandler);
		firstLaunch = false;
	}
	

	
	private void workaroundCapturePreviewHiddenOnSubsequentRotations() {
		View view = findViewById(R.id.video_frame);
		if (view == null) {
			Log.e("Linphone", "Android BUG: video frame not found; mix with landscape???");
			return;
		}

		FrameLayout frame = (FrameLayout) view;
		frame.removeAllViews();
		frame.addView(mVideoCaptureView);
		frame.addView(mVideoView);
	}
	
}
