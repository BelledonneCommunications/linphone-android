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
import android.hardware.Camera.Size;
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

	public AndroidCameraRecordImpl(long filterCtxPtr) {
		super();

		try {
			this.filterCtxPtr = filterCtxPtr;
			setOrStorePreviewCallBack(this);
		} catch (Throwable e) {
			Log.e("Linphone", "Error");
		}
		
	}

	
	private native void putImage(long filterCtxPtr, byte[] buffer);


	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data == null) {
			Log.e("Linphone", "onPreviewFrame Called with null buffer");
			return;
		}
		
		Size s = camera.getParameters().getPreviewSize();
		int expectedBuffLength = s.width * s.height * 3 /2;
		if (expectedBuffLength != data.length) {
			badBufferLengthReceived(data, expectedBuffLength);
			return;
		}
		
		long curTime = System.currentTimeMillis();
		if (lastFrameTime == 0) {
			lastFrameTime = curTime;
			putImage(filterCtxPtr, data);
			return;
		}

		double currentTimeElapsed = 0.8 * (curTime - lastFrameTime) / 1000 + 0.2 * timeElapsedBetweenFrames;
		if (1 / currentTimeElapsed > fps) {
//			Log.d("Linphone", "Clipping frame " + Math.round(1 / currentTimeElapsed) + " > " + fps);
			addBackCaptureBuffer(data);
			return;
		}
		lastFrameTime = curTime;
		timeElapsedBetweenFrames = currentTimeElapsed;

		//		Log.d("onPreviewFrame: ", Integer.toString(data.length));
		putImage(filterCtxPtr, data);
	}


	// Hook
	protected void badBufferLengthReceived(byte[] data, int expectedBuffLength) {
		Log.e("Linphone", "onPreviewFrame called with bad buffer length " + data.length
				+ " whereas expected is " + expectedBuffLength + " don't calling putImage");
	}

}
