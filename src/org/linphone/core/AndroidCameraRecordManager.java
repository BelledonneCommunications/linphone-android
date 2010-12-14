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
 * Manage the video capture, only on for all cameras.
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
	public boolean toggleUseFrontCamera() {
		setUseFrontCamera(!useFrontCamera);
		return useFrontCamera;
	}


	
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
	public boolean toggleMute() {
		setMuted(!muted);
		return muted;
	}
	public boolean isMuted() {
		return muted;
	}

	public void tryResumingVideoRecording() {
		if (isRecording()) return;
		tryToStartVideoRecording();
	}
	
	private void tryToStartVideoRecording() {
		if (muted || surfaceView == null || parameters == null) return;
		
		parameters.rotation = rotation;
		parameters.surfaceView = surfaceView;
		if (version >= 8) {
			recorder = new AndroidCameraRecordAPI8Impl(parameters);
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

	/**
	 * Naive simple version.
	 * @param askedSize
	 * @return
	 */
	public VideoSize doYouSupportThisVideoSize(VideoSize askedSize) {
		Log.d(tag, "Asking camera if it supports size "+askedSize);
		if (useFrontCamera && askedSize.isPortrait()) {
			return askedSize.createInverted(); // only landscape supported
		} else {
			return askedSize;
		}
	}

	
	private VideoSize closestVideoSize(VideoSize vSize, int defaultSizeCode, boolean defaultIsPortrait) {
		VideoSize testSize = vSize.isPortrait() ? vSize.createInverted() : vSize;

		for (Size s : AndroidCameraRecordManager.getInstance().supportedVideoSizes()) {
			if (s.height == testSize.getHeight() && s.width == testSize.getWidth()) {
				return vSize;
			}
		}

		return VideoSize.createStandard(defaultSizeCode, defaultIsPortrait);
	}
	
	private static final int rearCamId() {return 1;}
	private static final int frontCamId() {return 2;}
	private final int cameraId() {return useFrontCamera? frontCamId() : rearCamId(); }
}
