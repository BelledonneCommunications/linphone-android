/*
AndroidCameraRecord8Impl.java
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
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;

/**
 * 
 * Android >= 8 (2.2) version.
 * @author Guillaume Beraudo
 *
 */
public class AndroidCameraRecordBufferedImpl extends AndroidCameraRecordImplAPI5 {


	public AndroidCameraRecordBufferedImpl(RecorderParams parameters) {
		super(parameters);
	}

	@Override
	protected void lowLevelSetPreviewCallback(Camera camera, PreviewCallback cb) {
		if (cb != null) {
			Log.d("Linphone", "Setting optimized callback with buffer (Android >= 8). Remember to manage the pool of buffers!!!");
		}
		camera.setPreviewCallbackWithBuffer(cb);
	}
	
	@Override
	public void onPreviewStarted(Camera camera) {
		super.onPreviewStarted(camera);

		Size s = camera.getParameters().getPreviewSize();
		int wishedBufferSize = s.height * s.width * 3 / 2;

		camera.addCallbackBuffer(new byte[wishedBufferSize]);
		camera.addCallbackBuffer(new byte[wishedBufferSize]);
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		super.onPreviewFrame(data, camera);
		camera.addCallbackBuffer(data);
	}

	@Override
	protected void onSettingCameraParameters(Parameters parameters) {
		super.onSettingCameraParameters(parameters);
		// Only on v8 hardware
		camera.setDisplayOrientation(90 * orientationCode);
	}
	
	
}
