/*
AndroidCameraRecordImpl.java
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
package org.linphone.core.video;

import java.util.Collections;
import java.util.List;

import org.linphone.core.Log;
import org.linphone.core.Version;
import org.linphone.core.VideoSize;

import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;


public abstract class AndroidCameraRecord implements AutoFocusCallback {

	protected Camera camera;
	private RecorderParams params;

	private PreviewCallback storedPreviewCallback;
	private boolean previewStarted;
	private List <VideoSize> supportedVideoSizes;
	private Size currentPreviewSize;
	
	public AndroidCameraRecord(RecorderParams parameters) {
		this.params = parameters;
	}
	
	protected List<Size> getSupportedPreviewSizes(Camera.Parameters parameters) {
		return Collections.emptyList();
	}
	
	private int[] findClosestEnclosingFpsRange(int expectedFps, List<int[]> fpsRanges) {
		Log.d("Searching for closest fps range from ",expectedFps);
		int measure = Integer.MAX_VALUE;
		int[] closestRange = fpsRanges.get(0);
		for (int[] curRange : fpsRanges) {
			if (curRange[0] > expectedFps || curRange[1] < expectedFps) continue;
			int curMeasure = Math.abs(curRange[0] - expectedFps)
					+ Math.abs(curRange[1] - expectedFps);
			if (curMeasure < measure) {
				closestRange=curRange;
				Log.d("a better range has been found: w=",closestRange[0],",h=",closestRange[1]);
			}
		}
		Log.d("The closest fps range is w=",closestRange[0],",h=",closestRange[1]);
		return closestRange;
	}

	public synchronized void startPreview() { // FIXME throws exception?
		if (previewStarted) {
			Log.w("Already started");
			throw new RuntimeException("Video recorder already started");
			// return
		}
		
		if (params.surfaceView.getVisibility() != SurfaceView.VISIBLE) {
			// Illegal state
			Log.e("Illegal state: video capture surface view is not visible");
			return;
		}
		

		Log.d("Trying to open camera with id ", params.cameraId);
		if (camera != null) {
			Log.e("Camera is not null, ?already open? : aborting");
			return;
		}
		camera = openCamera(params.cameraId);
		camera.setErrorCallback(new ErrorCallback() {
			public void onError(int error, Camera camera) {
				Log.e("Camera error : ", error);
			}
		});
		
		
		Camera.Parameters parameters=camera.getParameters();
		if (Version.sdkStrictlyBelow(Version.API09_GINGERBREAD_23)) {
			parameters.set("camera-id",params.cameraId);
		}
		
		if (supportedVideoSizes == null) {
			supportedVideoSizes = VideoUtil.createList(getSupportedPreviewSizes(parameters));
		}


		if (params.width >= params.height) {
			parameters.setPreviewSize(params.width, params.height);
		} else {
			// invert height and width
			parameters.setPreviewSize(params.height, params.width);
		}
		// should setParameters and get again to have the real one??
		currentPreviewSize = parameters.getPreviewSize(); 

		// Frame rate
		if (Version.sdkStrictlyBelow(Version.API09_GINGERBREAD_23)) {
			// Select the supported fps just faster than the target rate
			List<Integer> supportedFrameRates=parameters.getSupportedPreviewFrameRates();
			if (supportedFrameRates != null && supportedFrameRates.size() > 0) {
				Collections.sort(supportedFrameRates);
				int selectedRate = -1;
				for (Integer rate : supportedFrameRates) {
					selectedRate=rate;
					if (rate >= params.fps) break; 
				}
				parameters.setPreviewFrameRate(selectedRate);
			}
		} else {
			List<int[]> supportedRanges = parameters.getSupportedPreviewFpsRange();
			int[] range=findClosestEnclosingFpsRange((int)(1000*params.fps), supportedRanges);
			parameters.setPreviewFpsRange(range[0], range[1]);
		}


		onSettingCameraParameters(parameters);
		camera.setParameters(parameters);

		SurfaceHolder holder = params.surfaceView.getHolder();
		try {
			camera.setPreviewDisplay(holder);
		}
		catch (Throwable t) {
			Log.e(t,"Exception in Video capture setPreviewDisplay()");
		}


		try {
			camera.startPreview();
		} catch (Throwable e) {
			Log.e("Error, can't start camera preview. Releasing camera!");
			camera.release();
			camera = null;
			return;
		}

		previewStarted = true;

		// Activate autofocus
		if (Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode())) {
			OnClickListener svClickListener = new OnClickListener() {
				public void onClick(View v) {
					Log.i("Auto focus requested");
					camera.autoFocus(AndroidCameraRecord.this);
				}
			};
			params.surfaceView.setOnClickListener(svClickListener);
		//	svClickListener.onClick(null);
		} else {
			params.surfaceView.setOnClickListener(null);
		}
		
		// Register callback to get capture buffer
		lowLevelSetPreviewCallback(camera, storedPreviewCallback);
		
		
		onPreviewStarted(camera);
	}
	



	protected Camera openCamera(int cameraId) {
		return Camera.open();
	}

	protected void onSettingCameraParameters(Parameters parameters) {}

	/**
	 * Hook.
	 * @param camera
	 */
	public void onPreviewStarted(Camera camera) {}

	public void storePreviewCallBack(PreviewCallback cb) {
		this.storedPreviewCallback = cb;
		if (camera == null) {
			Log.w("Capture camera not ready, storing preview callback");
			return;
		}
		
		lowLevelSetPreviewCallback(camera, cb);
	}


	public void stopPreview() {
		if (!previewStarted) return;
		lowLevelSetPreviewCallback(camera, null);
		camera.stopPreview();
		camera.release();
		camera=null;
		Log.d("Camera released");
		currentPreviewSize = null;
		previewStarted = false;
	}
	
	
	public void stopCaptureCallback() {
		if (camera != null) {
			lowLevelSetPreviewCallback(camera, null);
		}
	}
	
	protected abstract void lowLevelSetPreviewCallback(Camera camera, PreviewCallback cb);



	public static class RecorderParams {
		public static enum MirrorType {NO, HORIZONTAL, CENTRAL, VERTICAL};

		public float fps;
		public int height;
		public int width;

		final long filterDataNativePtr;
		public int cameraId;
		public boolean isFrontCamera;
		public int rotation;
		public SurfaceView surfaceView;

		public MirrorType mirror = MirrorType.NO;
        public int phoneOrientation;

		public RecorderParams(long ptr) {
			filterDataNativePtr = ptr;
		}
	}



	public boolean isStarted() {
		return previewStarted;
	}

	public List<VideoSize> getSupportedVideoSizes() {
		return supportedVideoSizes;
	}
	
	
	protected int getExpectedBufferLength() {
		if (currentPreviewSize == null) return -1;

		return currentPreviewSize.width * currentPreviewSize.height * 3 /2;
	}

	public void onAutoFocus(boolean success, Camera camera) {
		if (success) Log.i("Autofocus success");
		else Log.i("Autofocus failure");
	}

	public int getStoredPhoneOrientation() {
		return params.phoneOrientation;
	}
}
