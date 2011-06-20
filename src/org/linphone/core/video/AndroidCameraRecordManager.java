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
package org.linphone.core.video;

import java.util.List;

import org.linphone.LinphoneManager;
import org.linphone.core.Version;
import org.linphone.core.video.AndroidCameraRecord.RecorderParams;

import android.content.Context;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.OrientationEventListener;
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
    private OrientationEventListener orientationEventListener;
    private OnCapturingStateChangedListener capturingStateChangedListener;

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
	private int mAlwaysChangingPhoneOrientation=0;


	// singleton
	private AndroidCameraRecordManager() {
		cc = Version.sdkAboveOrEqual(9) ? new AndroidCameraConf9() : new AndroidCameraConf5();
		Log.i(tag, "=== Detected " + cc.getFoundCameras()+ " ===");
		cameraId = cc.getFoundCameras().defaultC;
	}

	


	public boolean hasSeveralCameras() {
		return cc.getFoundCameras().hasSeveralCameras();
	}
	public boolean hasFrontCamera() {
		return cc.getFoundCameras().front != null;
	}

	
	public void setUseFrontCamera(boolean value) {
		if (!hasFrontCamera()) {
			Log.e(tag, "setUseFrontCamera(true) while no front camera detected on device: using rear");
			value = false;
		}
		if (cc.isFrontCamera(cameraId) == value) return; // already OK
		toggleUseFrontCamera();
	}

	public boolean isUseFrontCamera() {return cc.isFrontCamera(cameraId);}
	public boolean toggleUseFrontCamera() {
		boolean previousUseFront = cc.isFrontCamera(cameraId);

		cameraId = previousUseFront ? cc.getFoundCameras().rear : cc.getFoundCameras().front;

		if (parameters != null) {
			parameters.cameraId = cameraId;
			parameters.isFrontCamera = !previousUseFront;
			if (isRecording()) {
				stopVideoRecording();
				tryToStartVideoRecording();
			}
		}

		return !previousUseFront;
	}


	
	public void setParametersFromFilter(long filterDataPtr, int height, int width, float fps) {
		if (recorder != null) {
			Log.w(tag, "Recorder should not be running");
			stopVideoRecording();
		}
		RecorderParams p = new RecorderParams(filterDataPtr);
		p.fps = fps;
		p.width = width;
		p.height = height;
		p.cameraId = cameraId;
		p.isFrontCamera = isUseFrontCamera();
		parameters = p;
		
		if (p.isFrontCamera) {
			if (!isCameraOrientationPortrait()) {
				// Code for Nexus S: to be tested
				p.mirror = RecorderParams.MirrorType.CENTRAL;
			} else {
				// Code for Galaxy S like: camera mounted landscape when phone hold portrait
				p.mirror = RecorderParams.MirrorType.HORIZONTAL;
			}
		}

		tryToStartVideoRecording();
	} 
	
	
	public final void setSurfaceView(final SurfaceView sv) {
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
	
	/**
	 * @param muteState
	 * @return true if mute state changed
	 */
	public boolean setMuted(boolean muteState) {
		if (muteState == muted) return false;
		muted = muteState;
		if (muted) {
			stopVideoRecording();
		} else {
			tryToStartVideoRecording();
		}
		return true;
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

	private synchronized void tryToStartVideoRecording() {
		if (orientationEventListener == null) {
			throw new RuntimeException("startOrientationSensor was not called");
		}

		if (muted || surfaceView == null || parameters == null) return;

		if (recorder != null) {
			Log.e(tag, "Recorder already present");
			stopVideoRecording();
		}

		parameters.rotation = bufferRotationToCompensateCameraAndPhoneOrientations();

		parameters.surfaceView = surfaceView;
		if (Version.sdkAboveOrEqual(9)) {
			recorder = new AndroidCameraRecord9(parameters);
		} else if (Version.sdkAboveOrEqual(8)) {
			recorder = new AndroidCameraRecord8(parameters);
		} else if (Version.sdkAboveOrEqual(5)) {
			recorder = new AndroidCameraRecord5(parameters);
		} else {
			throw new RuntimeException("SDK version unsupported " + Version.sdk());
		}

		recorder.startPreview();

		if (capturingStateChangedListener != null) {
			capturingStateChangedListener.captureStarted();
		}
	}

	public synchronized void stopVideoRecording() {
		if (recorder != null) {
			recorder.stopPreview();
			recorder = null;
			if (capturingStateChangedListener != null) {
				capturingStateChangedListener.captureStopped();
			}
		}
	}

	
	
	/**
	 * FIXME select right camera
	 */
	public List<Size> supportedVideoSizes() {
		if (supportedVideoSizes != null) {
			return supportedVideoSizes;
		}

		if (recorder != null) {
			supportedVideoSizes = recorder.getSupportedVideoSizes();
			if (supportedVideoSizes != null) return supportedVideoSizes;
		}

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

	public boolean isOutputPortraitDependingOnCameraAndPhoneOrientations() {
		final int rotation = bufferRotationToCompensateCameraAndPhoneOrientations();
		final boolean isPortrait = (rotation % 180) == 90;
		
		Log.d(tag, "Camera sensor in " + (isPortrait? "portrait":"landscape") + " orientation.");
		return isPortrait;
	}

	
	
	


	public boolean isCameraOrientationPortrait() {
		return (cc.getCameraOrientation(cameraId) % 180) == 0;
	}



	private int bufferRotationToCompensateCameraAndPhoneOrientations() {
		if (Version.sdkStrictlyBelow(Version.API08_FROYO_22)) {
			// Don't perform any rotation
			// Phone screen should use fitting orientation
			return 0;
		}

		final int phoneOrientation = mAlwaysChangingPhoneOrientation;
		final int cameraOrientation = cc.getCameraOrientation(cameraId);
		final int rotation = (cameraOrientation + phoneOrientation) % 360;
		Log.d(tag, String.format(
				"Capture video buffer of cameraId=%d will need a rotation of "
				+ "%d degrees: camera_orientation=%d, phone_orientation=%d",
				cameraId, rotation, cameraOrientation, phoneOrientation));
		return rotation;
	}


	/**
	 * Register a sensor to track phoneOrientation changes
	 */
	public void startOrientationSensor(Context c) {
		if (orientationEventListener == null) {
			orientationEventListener = new LocalOrientationEventListener(c);
			orientationEventListener.enable();
		}
	}

	private class LocalOrientationEventListener extends OrientationEventListener {
		public LocalOrientationEventListener(Context context) {
			super(context);
		}
		@Override
		public void onOrientationChanged(final int o) {
			if (o == OrientationEventListener.ORIENTATION_UNKNOWN) return;

			int degrees=270;
			if (o < 45 || o >315) degrees=0;
			else if (o<135) degrees=90;
			else if (o<225) degrees=180;

			if (mAlwaysChangingPhoneOrientation == degrees) return;

			Log.i(tag, "Phone orientation changed to " + degrees);
			mAlwaysChangingPhoneOrientation = degrees;
		}
	}

	/**
	 * @return true if linphone core configured to send a A buffer while phone orientation induces !A buffer (A=landscape or portrait)
	 */
	public boolean isOutputOrientationMismatch() {
		final boolean currentlyPortrait = LinphoneManager.getLc().getPreferredVideoSize().isPortrait();
		final boolean shouldBePortrait = isOutputPortraitDependingOnCameraAndPhoneOrientations();
		return currentlyPortrait ^ shouldBePortrait;
	}

	public void setOnCapturingStateChanged(OnCapturingStateChangedListener listener) {
		this.capturingStateChangedListener=listener;
	}

	public static interface OnCapturingStateChangedListener {
		void captureStarted();
		void captureStopped();
	}


}
