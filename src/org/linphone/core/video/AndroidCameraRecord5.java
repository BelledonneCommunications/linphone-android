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

import java.util.Arrays;
import java.util.List;

import org.linphone.core.video.AndroidCameraRecord.RecorderParams.MirrorType;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;

/**
 * Record from Android camera.
 * Android >= 5 (2.0) version.
 *
 * @author Guillaume Beraudo
 *
 */
class AndroidCameraRecord5 extends AndroidCameraRecord implements PreviewCallback {

	private long filterCtxPtr;
	private double timeElapsedBetweenFrames = 0;
	private long lastFrameTime = 0;
	private final double expectedTimeBetweenFrames;
	protected final int rotation;
	private MirrorType mirror;
//	private boolean isUsingFrontCamera;

	public AndroidCameraRecord5(RecorderParams parameters) {
		super(parameters);
		expectedTimeBetweenFrames = 1d / Math.round(parameters.fps);
		filterCtxPtr = parameters.filterDataNativePtr;
		rotation = parameters.rotation;
		mirror = parameters.mirror;
//		isUsingFrontCamera = parameters.isFrontCamera;

		storePreviewCallBack(this);
	}

	
	private native void putImage(long filterCtxPtr, byte[] buffer, int rotate, int mirror);


	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data == null) {
			Log.e(tag, "onPreviewFrame Called with null buffer");
			return;
		}
		if (filterCtxPtr == 0l) {
			Log.e(tag, "onPreviewFrame Called with no filterCtxPtr set");
			return;
		}
		
		int expectedBuffLength = getExpectedBufferLength();
		if (expectedBuffLength != data.length) {
			Log.e(tag, "onPreviewFrame called with bad buffer length " + data.length
					+ " whereas expected is " + expectedBuffLength + " don't calling putImage");
			return;
		}

		long curTime = System.currentTimeMillis();
		if (lastFrameTime == 0) {
			lastFrameTime = curTime;
			putImage(filterCtxPtr, data, rotation, mirror.ordinal());
			return;
		}

		double currentTimeElapsed = 0.8 * (curTime - lastFrameTime) / 1000 + 0.2 * timeElapsedBetweenFrames;
		if (currentTimeElapsed < expectedTimeBetweenFrames) {
//			Log.d(tag, "Clipping frame " + Math.round(1 / currentTimeElapsed) + " > " + fps);
			return;
		}
		lastFrameTime = curTime;
		timeElapsedBetweenFrames = currentTimeElapsed;

		//		Log.d("onPreviewFrame: ", Integer.toString(data.length));
		putImage(filterCtxPtr, data, rotation, mirror.ordinal());
	}

	protected String selectFocusMode(final List<String> supportedFocusModes) {/*
		if (isUsingFrontCamera && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
			return Camera.Parameters.FOCUS_MODE_FIXED;
		}
		if (!isUsingFrontCamera && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
			return Camera.Parameters.FOCUS_MODE_INFINITY;
		}*/

		if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			return Camera.Parameters.FOCUS_MODE_AUTO;
		}
		
		return null; // Should not occur?
	}
	
	@Override
	protected void onSettingCameraParameters(Parameters parameters) {
		super.onSettingCameraParameters(parameters);
		List<String> supportedFocusModes = parameters.getSupportedFocusModes();
		String focusMode = selectFocusMode(supportedFocusModes);
		if (focusMode != null) {
			Log.w(tag, "Selected focus mode: " + focusMode);
			parameters.setFocusMode(focusMode);
		} else {
			Log.i(tag, "No suitable focus mode found in : " + Arrays.toString(supportedFocusModes.toArray()));
		}
	}

	@Override
	protected List<Size> getSupportedPreviewSizes(Parameters parameters) {
		return parameters.getSupportedPreviewSizes();
	}

	@Override
	protected void lowLevelSetPreviewCallback(Camera camera, PreviewCallback cb) {
		camera.setPreviewCallback(cb);
	}

}
