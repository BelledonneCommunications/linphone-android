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
package org.linphone.core;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;

/**
 * Record from Android camera.
 *
 * @author Guillaume Beraudo
 *
 */
public class AndroidCameraRecordImpl extends AndroidCameraRecord implements PreviewCallback {

	private long filterCtxPtr;
	private double timeElapsedBetweenFrames = 0;
	private long lastFrameTime = 0;
	private final double expectedTimeBetweenFrames;
	protected final int rotation;

	public AndroidCameraRecordImpl(RecorderParams parameters) {
		super(parameters);
		expectedTimeBetweenFrames = 1d / Math.round(parameters.fps);
		filterCtxPtr = parameters.filterDataNativePtr;
		rotation = parameters.rotation;

		storePreviewCallBack(this);
	}

	
	private native void putImage(long filterCtxPtr, byte[] buffer, int rotate);


	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data == null) {
			Log.e("Linphone", "onPreviewFrame Called with null buffer");
			return;
		}
		if (filterCtxPtr == 0l) {
			Log.e("Linphone", "onPreviewFrame Called with no filterCtxPtr set");
			return;
		}
		
		int expectedBuffLength = getExpectedBufferLength();
		if (expectedBuffLength != data.length) {
			Log.e("Linphone", "onPreviewFrame called with bad buffer length " + data.length
					+ " whereas expected is " + expectedBuffLength + " don't calling putImage");
			return;
		}
		
		long curTime = System.currentTimeMillis();
		if (lastFrameTime == 0) {
			lastFrameTime = curTime;
			putImage(filterCtxPtr, data, rotation);
			return;
		}

		double currentTimeElapsed = 0.8 * (curTime - lastFrameTime) / 1000 + 0.2 * timeElapsedBetweenFrames;
		if (currentTimeElapsed < expectedTimeBetweenFrames) {
//			Log.d("Linphone", "Clipping frame " + Math.round(1 / currentTimeElapsed) + " > " + fps);
			return;
		}
		lastFrameTime = curTime;
		timeElapsedBetweenFrames = currentTimeElapsed;

		//		Log.d("onPreviewFrame: ", Integer.toString(data.length));
		putImage(filterCtxPtr, data, rotation);
	}



	@Override
	protected void lowLevelSetPreviewCallback(Camera camera, PreviewCallback cb) {
		camera.setPreviewCallback(cb);
	}

}
