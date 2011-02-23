/*
TutorialHelloWorldActivity.java
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
package org.linphone.core.tutorials;

import java.util.Stack;

import org.linphone.R;
import org.linphone.core.VideoSize;
import org.linphone.core.video.AndroidCameraRecord;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;
import static org.linphone.core.VideoSize.*;

/**
 * Activity for displaying and starting the HelloWorld example on Android phone.
 *
 * @author Guillaume Beraudo
 *
 */
public class TestVideoActivity extends Activity implements Callback, OnClickListener {

	private SurfaceView surfaceView;
	private static final int rate = 7;
	private JavaCameraRecordImpl recorder;
	private static String tag = "Linphone";
	private TextView debugView;
	private Button nextSize;
	private Button changeCamera;
	private Button changeOrientation;
	private AndroidCameraRecord.RecorderParams params;

	private Stack<VideoSize> videoSizes = createSizesToTest();
	private int currentCameraId = 2;
	private boolean currentOrientationIsPortrait = false;
	private int width;
	private int height;
	private boolean started;



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videotest);

		surfaceView=(SurfaceView)findViewById(R.id.videotest_surfaceView);

		nextSize = (Button) findViewById(R.id.test_video_size);
		nextSize.setOnClickListener(this);

		changeCamera = (Button) findViewById(R.id.test_video_camera);
		changeCamera.setText("Cam"+otherCameraId(currentCameraId));
		changeCamera.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				changeCamera.setText("Cam"+currentCameraId);
				currentCameraId = otherCameraId(currentCameraId);
				updateRecording();
			}
		});
		
		changeOrientation = (Button) findViewById(R.id.test_video_orientation);
		changeOrientation.setText(orientationToString(!currentOrientationIsPortrait));
		changeOrientation.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				currentOrientationIsPortrait = !currentOrientationIsPortrait;
				changeOrientation.setText(orientationToString(!currentOrientationIsPortrait));

				if (width == 0 || height == 0) return;
				int newWidth = currentOrientationIsPortrait? Math.min(height, width) : Math.max(height, width);
				int newHeight = currentOrientationIsPortrait? Math.max(height, width) : Math.min(height, width);
				changeSurfaceViewLayout(newWidth, newHeight); // will change width and height on surfaceChanged
			}
		});
		
		SurfaceHolder holder = surfaceView.getHolder();
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.addCallback(this);


		debugView = (TextView) findViewById(R.id.videotest_debug);
	}

	protected void updateRecording() {
		if (width == 0 || height == 0) return;
		if (recorder != null) recorder.stopPreview();

		params = new AndroidCameraRecord.RecorderParams(0);
		params.surfaceView = surfaceView;
		params.width = width;
		params.height = height;
		params.fps = rate;
		params.cameraId = currentCameraId;

		recorder = new JavaCameraRecordImpl(params);
//		recorder.setDebug(debugView);
		debugView.setText(orientationToString(currentOrientationIsPortrait)
				+ " w="+width + " h="+height+ " cam"+currentCameraId);
		recorder.startPreview();
		
	}

	private String orientationToString(boolean orientationIsPortrait) {
		return orientationIsPortrait? "Por" : "Lan";
	}
	private int otherCameraId(int currentId) {
		return (currentId == 2) ? 1 : 2;
	}
	public void onClick(View v) {
		nextSize.setText("Next");
		started=true;
		if (videoSizes.isEmpty()) {
			videoSizes = createSizesToTest();
		}

		VideoSize size = videoSizes.pop();
		changeSurfaceViewLayout(size.width, size.height);

		// on surface changed the recorder will be restarted with new values
		// and the surface will be resized
	}


	private void changeSurfaceViewLayout(int width, int height) {
		LayoutParams params = surfaceView.getLayoutParams();
		params.height = height;
		params.width = width;
		surfaceView.setLayoutParams(params);
		
	}

	private Stack<VideoSize> createSizesToTest() {
		Stack<VideoSize> stack = new Stack<VideoSize>();

		stack.push(VideoSize.createStandard(QCIF, false));
		stack.push(VideoSize.createStandard(CIF, false));
		stack.push(VideoSize.createStandard(QVGA, false));
		stack.push(VideoSize.createStandard(HVGA, false));
		stack.push(new VideoSize(640,480));
		stack.push(new VideoSize(800,480));
		return stack;
	}




	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceView = null;
		Log.d(tag , "Video capture surface destroyed");
		if (recorder != null) recorder.stopPreview();
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(tag , "Video capture surface created");
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,	int height) {
		if (!started) return;
		if (recorder != null) recorder.stopPreview();
		
		this.width = width;
		this.height = height;

		updateRecording();
	}
}
