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
import android.view.SurfaceView;

public class VideoCallActivity extends Activity {
	SurfaceView mVideoView;
	SurfaceView mVideoCaptureView;
	private Handler mHandler = new Handler() ;

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videocall);
		mVideoView = (SurfaceView) findViewById(R.id.video_surface);
		LinphoneService.instance().getLinphoneCore().setVideoWindow((Object) mVideoView);
		
//		mVideoCaptureView = new SurfaceView(getApplicationContext());
		mVideoCaptureView = (SurfaceView) findViewById(R.id.video_capture_surface);
		AndroidCameraRecord.setSurfaceView(mVideoCaptureView, mHandler);
	}
}
