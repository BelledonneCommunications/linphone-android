/*
AndroidCameraRecordManager.java
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
package org.linphone.core;

import java.util.List;

import org.linphone.core.AndroidCameraRecord.RecorderParams;

import android.hardware.Camera.Size;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;



/**
 * Manage the video capture; one instance per camera.
 *
 * @author Guillaume Beraudo
 *
 */
public class AndroidCameraRecordManager {
	private static final int version = Integer.parseInt(Build.VERSION.SDK);
	private static final String tag = "Linphone";
	private static AndroidCameraRecordManager instance;

	// singleton
	private AndroidCameraRecordManager() {}

	
	/**
	 * @return instance
	 */
	public static final synchronized AndroidCameraRecordManager getInstance() {
		if (instance == null) {
			instance = new AndroidCameraRecordManager();
		}
		return instance;
	}

	private AndroidCameraRecord.RecorderParams parameters;
	private SurfaceView surfaceView;
	private boolean muted;
	

	private AndroidCameraRecord recorder;
	

	private List<Size> supportedVideoSizes;
	private int rotation;

	private boolean useFrontCamera;
	public void setUseFrontCamera(boolean value) {
		if (useFrontCamera == value) return;
		this.useFrontCamera = value;
		
		if (parameters != null) {
			parameters.cameraId = cameraId();
			if (isRecording()) {
				stopVideoRecording();
				tryToStartVideoRecording();
			}
		}
	}
	public boolean isUseFrontCamera() {return useFrontCamera;}


	
	public void setParametersFromFilter(long filterDataPtr, int height, int width, float fps) {
		stopVideoRecording();
		RecorderParams p = new RecorderParams(filterDataPtr);
		p.fps = fps;
		p.width = width;
		p.height = height;
		p.cameraId = cameraId();
		p.videoDimensionsInverted = width < height;
		// width and height will be inverted in Recorder on startPreview
		parameters = p;
		tryToStartVideoRecording();
	} 
	
	
	public final void setSurfaceView(final SurfaceView sv, final int rotation) {
		this.rotation = useFrontCamera ? 1 : rotation;
		SurfaceHolder holder = sv.getHolder();
	    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		holder.addCallback(new Callback() {
			public void surfaceDestroyed(SurfaceHolder holder) {
				surfaceView = null;
				Log.d(tag , "Video capture surface destroyed");
				stopVideoRecording();
			}

			public void surfaceCreated(SurfaceHolder holder) {
				surfaceView = sv;
				Log.d(tag , "Video capture surface created");
				tryToStartVideoRecording();
			}

			public void surfaceChanged(SurfaceHolder holder, int format, int width,
					int height) {
				Log.d(tag , "Video capture surface changed");
			}
		});
	}
	
	public void setMuted(boolean muteState) {
		if (muteState == muted) return;
		muted = muteState;
		if (muted) {
			stopVideoRecording();
		} else {
			tryToStartVideoRecording();
		}
	}
	public void toggleMute() {
		setMuted(!muted);
	}
	public boolean isMuted() {
		return muted;
	}

	
	private void tryToStartVideoRecording() {
		if (muted || surfaceView == null || parameters == null) return;
		
		parameters.rotation = rotation;
		parameters.surfaceView = surfaceView;
		if (version >= 8) {
			recorder = new AndroidCameraRecordBufferedImpl(parameters);
		} else if (version >= 5) {
			recorder = new AndroidCameraRecordImplAPI5(parameters);
		} else {
			recorder = new AndroidCameraRecordImpl(parameters);
		}

		recorder.startPreview();
	}

	public void stopVideoRecording() {
		if (recorder != null) {
			recorder.stopPreview();
			recorder = null;
		}
	}

	
	// FIXME select right camera
	/**
	 * Eventually null if API < 5.
	 * 
	 */
	public List<Size> supportedVideoSizes() {
		if (supportedVideoSizes != null) {
			return supportedVideoSizes;
		}

		if (recorder != null) {
			supportedVideoSizes = recorder.getSupportedVideoSizes();
			if (supportedVideoSizes != null) return supportedVideoSizes;
		}

		if (version >= 5) {
			supportedVideoSizes = AndroidCameraRecordImplAPI5.oneShotSupportedVideoSizes();
		}
		
		// eventually null
		
		return supportedVideoSizes;
	}


	public boolean isRecording() {
		if (recorder != null) {
			return recorder.isStarted();
		}

		return false;
	}

	
	public void invalidateParameters() {
		stopVideoRecording();
		parameters = null;
	}

	public int[] doYouSupportThisVideoSize(int[] askedSize) {
		final int askedW = askedSize[0];
		final int askedH = askedSize[1];
		Log.d(tag, "w"+askedW);
		Log.d(tag, "h"+askedH);
		if (useFrontCamera && isPortraitSize(askedW, askedH)) {
			return new int[] {askedH, askedW}; // only landscape supported
		} else {
			return askedSize;
		}
	}
	private boolean isPortraitSize(int width, int height) {
		return width < height;
	}

	private static final int rearCamId() {return 1;}
	private static final int frontCamId() {return 2;}
	private final int cameraId() {return useFrontCamera? frontCamId() : rearCamId(); }
}
