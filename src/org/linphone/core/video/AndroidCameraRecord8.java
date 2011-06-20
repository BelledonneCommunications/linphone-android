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
package org.linphone.core.video;

import java.util.List;

import org.linphone.core.Log;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;

/**
 * 
 * Android >= 8 (2.2) version.
 * @author Guillaume Beraudo
 *
 */
class AndroidCameraRecord8 extends AndroidCameraRecord5 {


	public AndroidCameraRecord8(RecorderParams parameters) {
		super(parameters);
	}

	@Override
	protected void lowLevelSetPreviewCallback(Camera camera, PreviewCallback cb) {
		if (cb != null) {
			Log.d("Setting optimized callback with buffer (Android >= 8). Remember to manage the pool of buffers!!!");
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
		camera.setDisplayOrientation(rotation);
	}

	@Override
	protected String selectFocusMode(final List<String> supportedFocusModes) {
		if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_EDOF)) {
			return Camera.Parameters.FOCUS_MODE_EDOF;
		} else
			return super.selectFocusMode(supportedFocusModes);
	}
}
