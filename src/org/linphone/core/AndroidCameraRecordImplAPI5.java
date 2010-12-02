/*
AndroidCameraRecordImplAPI5.java
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

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;


public class AndroidCameraRecordImplAPI5 extends AndroidCameraRecordImpl {

	public AndroidCameraRecordImplAPI5(RecorderParams parameters) {
		super(parameters);
	}

	@Override
	protected void onSettingCameraParameters(Parameters parameters) {
		super.onSettingCameraParameters(parameters);

		if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			Log.w(tag, "Auto Focus supported by camera device");
			parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		} else {
			Log.w(tag, "Auto Focus not supported by camera device");
			if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
				Log.w(tag, "Infinity Focus supported by camera device");
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
			} else {
				Log.w(tag, "Infinity Focus not supported by camera device");
			}
		}
	}

	public static List<Size> oneShotSupportedVideoSizes() {
		Camera camera = Camera.open();
		List<Size> supportedVideoSizes =camera.getParameters().getSupportedPreviewSizes();
		camera.release();
		return supportedVideoSizes;
	}
	
	@Override
	protected List<Size> getSupportedPreviewSizes(Parameters parameters) {
		return parameters.getSupportedPreviewSizes();
	}
}
