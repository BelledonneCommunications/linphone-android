/*
AndroidCameraRecord9Impl.java
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

import android.hardware.Camera;

/**
 * 
 * Android >= 9 (2.3) version.
 * @author Guillaume Beraudo
 *
 */
class AndroidCameraRecord9 extends AndroidCameraRecord8 {


	public AndroidCameraRecord9(RecorderParams parameters) {
		super(parameters);
	}

	@Override
	protected Camera openCamera(int cameraId) {
		return Camera.open(cameraId);
	}

	@Override
	protected String selectFocusMode(final List<String> supportedFocusModes) {
		if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
			return Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
		} else
			return super.selectFocusMode(supportedFocusModes);
	}
}
