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
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;



/**
 * Manage the video capture, only one for all cameras.
 *
 * @author Guillaume Beraudo
 *
 */
public class AndroidCameraRecordManager {
	private static final String tag = "Linphone";
	private static AndroidCameraRecordManager instance;

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
	private final AndroidCameraConf cc;
	private SurfaceView surfaceView;
	private boolean muted;
	private int cameraId;

	private AndroidCameraRecord recorder;
	private List<Size> supportedVideoSizes;
	private int phoneOrientation;
	public int getPhoneOrientation() {return phoneOrientation;}
	public void setPhoneOrientation(int degrees) {this.phoneOrientation = degrees;}

	private int frontCameraId;
	private int rearCameraId;

	// singleton
	private AndroidCameraRecordManager() {
		cc = Version.sdkAbove(9) ? new AndroidCameraConf9() : new AndroidCameraConf();

		int[] fId = {-1};int[] rId = {-1};int[] cId = {-1};
		cc.findFrontAndRearCameraIds(fId, rId, cId);
		frontCameraId=fId[0];rearCameraId=rId[0];cameraId=cId[0];
	}

	


	public boolean hasSeveralCameras() {
		return frontCameraId != rearCameraId;
	}

	
	public void setUseFrontCamera(boolean value) {
		if (cc.isFrontCamera(cameraId) == value) return; // already OK

		toggleUseFrontCamera();
	}

	public boolean isUseFrontCamera() {return cc.isFrontCamera(cameraId);}
	public boolean toggleUseFrontCamera() {
		boolean previousUseFront = cc.isFrontCamera(cameraId);

		cameraId = previousUseFront ? rearCameraId : frontCameraId;

		if (parameters != null) {
			parameters.cameraId = cameraId;
			if (isRecording()) {
				stopVideoRecording();
				tryToStartVideoRecording();
			}
		}

		return !previousUseFront;
	}


	
	public void setParametersFromFilter(long filterDataPtr, int height, int width, float fps) {
		stopVideoRecording();
		RecorderParams p = new RecorderParams(filterDataPtr);
		p.fps = fps;
		p.width = width;
		p.height = height;
		p.cameraId = cameraId;
		parameters = p;
		tryToStartVideoRecording();
	} 
	
	
	public final void setSurfaceView(final SurfaceView sv, final int phoneOrientation) {
		this.phoneOrientation = phoneOrientation;
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
		
		parameters.rotation = bufferRotationForCorrectImageOrientation();

		parameters.surfaceView = surfaceView;
		if (Version.sdkAbove(9)) {
			recorder = new AndroidCameraRecord9Impl(parameters);
		} else if (Version.sdkAbove(8)) {
			recorder = new AndroidCameraRecord8Impl(parameters);
		} else if (Version.sdkAbove(5)) {
			recorder = new AndroidCameraRecord5Impl(parameters);
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

		if (Version.sdkAbove(5)) {
			supportedVideoSizes = AndroidCameraRecord5Impl.oneShotSupportedVideoSizes();
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

	public boolean outputIsPortrait() {
		final int rotation = bufferRotationForCorrectImageOrientation();
		final boolean isPortrait = (rotation % 180) == 90;
		
		Log.d(tag, "Camera sensor in portrait orientation ?" + isPortrait);
		return isPortrait;
	}

	
	
	


	public boolean isCameraOrientationPortrait() {
		return (cc.getCameraOrientation(cameraId) % 180) == 90;
	}



	private int bufferRotationForCorrectImageOrientation() {
		final int cameraOrientation = cc.getCameraOrientation(cameraId);
		final int rotation = Version.sdkAbove(8) ?
				(360 - cameraOrientation + 90 - phoneOrientation) % 360
				: 0;
		Log.d(tag, "Capture video buffer will need a rotation of " + rotation
				+ " degrees : camera " + cameraOrientation
				+ ", phone " + phoneOrientation);
		return rotation;
	}
}
