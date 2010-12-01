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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linphone.core.AndroidCameraRecord.RecorderParams;

import android.hardware.Camera;
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
	public static final int CAMERA_ID_FIXME_USE_PREFERENCE = 0;
	private static final int version = Integer.parseInt(Build.VERSION.SDK);
	private static Map<Integer, AndroidCameraRecordManager> instances = new HashMap<Integer, AndroidCameraRecordManager>();


	// singleton
	private AndroidCameraRecordManager(int cameraId) {
		this.cameraId = cameraId;
	}

	/**
	 * @param cameraId : see max_camera_id
	 * @return
	 */
	public static final synchronized AndroidCameraRecordManager getInstance(int cameraId) {
		if (cameraId < 0) {
			Log.e("Linphone", "Asking unmanageable camera " + cameraId);
			return null;
		}

		AndroidCameraRecordManager m = instances.get(cameraId);
		if (m == null) {
			m = new AndroidCameraRecordManager(cameraId);
			instances.put(cameraId, m);
		}
		return m;
	}
	
	public static final synchronized AndroidCameraRecordManager getInstance() {
		return getInstance(0);
	}

	private AndroidCameraRecord.RecorderParams parameters;
	private SurfaceView surfaceView;
	private boolean muted;
	

	private AndroidCameraRecord recorder;
	private final Integer cameraId;

	private List<Size> supportedVideoSizes;
	private int rotation;
	private static final String tag = "Linphone";

	
	public void setParametersFromFilter(long filterDataPtr, int height, int width, float fps) {
		stopVideoRecording();
		RecorderParams p = new RecorderParams(filterDataPtr);
		p.fps = fps;
		p.width = width;
		p.height = height;
		p.cameraId = cameraId;
		p.videoDimensionsInverted = width < height;
		parameters = p;
		tryToStartVideoRecording();
	} 
	
	
	public final void setSurfaceView(final SurfaceView sv, final int rotation) {
		this.rotation = rotation;
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
	public List<Size> supportedVideoSizes() {
		if (supportedVideoSizes != null) {
			return supportedVideoSizes;
		}

		if (recorder != null) {
			supportedVideoSizes = recorder.getSupportedVideoSizes();
			if (supportedVideoSizes != null) return supportedVideoSizes;
		}

		Camera camera = Camera.open();
		supportedVideoSizes = camera.getParameters().getSupportedPreviewSizes();
		camera.release();
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

}
