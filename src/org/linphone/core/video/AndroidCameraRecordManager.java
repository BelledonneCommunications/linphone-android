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

import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.Version;
import org.linphone.core.VideoSize;
import org.linphone.core.video.AndroidCameraRecord.RecorderParams;

import android.content.Context;
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
	private int mAlwaysChangingPhoneOrientation=0;


	// singleton
	private AndroidCameraRecordManager() {
		cc = Version.sdkAboveOrEqual(9) ? new AndroidCameraConf9() : new AndroidCameraConf5();
		Log.i("=== Detected " + cc.getFoundCameras()+ " ===");
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
			Log.e("setUseFrontCamera(true) while no front camera detected on device: using rear");
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
			Log.w("Recorder should not be running");
			stopVideoRecording();
		}
		RecorderParams p = new RecorderParams(filterDataPtr);
		p.fps = fps;
		p.width = width;
		p.height = height;
		p.cameraId = cameraId;
		p.isFrontCamera = isUseFrontCamera();
		parameters = p;

		// Mirror the sent frames in order to make them readable
		// (otherwise it is mirrored and thus unreadable)
		if (p.isFrontCamera) {
			if (!isCameraMountedPortrait()) {
				// Code for Nexus S
				if (isFrameToBeShownPortrait())
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
				Log.d("Video capture surface destroyed");
				stopVideoRecording();
			}

			public void surfaceCreated(SurfaceHolder holder) {
				surfaceView = sv;
				Log.d("Video capture surface created");
				tryToStartVideoRecording();
			}

			public void surfaceChanged(SurfaceHolder holder, int format, int width,
					int height) {
				Log.d("Video capture surface changed");
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
			Log.e("Recorder already present");
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

	
	
	public List<VideoSize> supportedVideoSizes() {
		Log.d("Using supportedVideoSizes of camera ",cameraId);
		return cc.getSupportedPreviewSizes(cameraId);
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

	/** Depends on currently selected camera, camera mounted portrait/landscape, current phone orientation */
	public boolean isFrameToBeShownPortrait() {
		final int rotation = bufferRotationToCompensateCameraAndPhoneOrientations();

		boolean isPortrait;
		if (isCameraMountedPortrait()) {
			// Nexus S
			isPortrait = (rotation % 180) == 0;
		} else {
			isPortrait = (rotation % 180) == 90;
		}

		Log.d("The frame to be shown and sent to remote is ", isPortrait? "portrait":"landscape"," orientation.");
		return isPortrait;
	}

	




	public boolean isCameraMountedPortrait() {
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
		int frontCameraCorrection = 0;
		if (cc.isFrontCamera(cameraId)) {
			frontCameraCorrection=180; // hack that "just works" on Galaxy S and Nexus S.
			// See also magic with mirrors in setParametersFromFilter
		}
		final int rotation = (cameraOrientation + phoneOrientation + frontCameraCorrection) % 360;
		Log.d("Capture video buffer of cameraId=",cameraId,
				" will need a rotation of ",rotation,
				" degrees: camera_orientation=",cameraOrientation,
				" phone_orientation=", phoneOrientation);
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

			Log.i("Phone orientation changed to ", degrees);
			mAlwaysChangingPhoneOrientation = degrees;
		}
	}

	/**
	 * @return true if linphone core configured to send a A buffer while phone orientation induces !A buffer (A=landscape or portrait)
	 */
	public boolean isOutputOrientationMismatch(LinphoneCore lc) {
		final boolean currentlyPortrait = lc.getPreferredVideoSize().isPortrait();
		final boolean shouldBePortrait = isFrameToBeShownPortrait();
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
