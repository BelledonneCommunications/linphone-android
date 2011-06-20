/*
AndroidCameraConf9.java
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

import org.linphone.core.Log;

import android.hardware.Camera;

class AndroidCameraConf9 implements AndroidCameraConf {
	private AndroidCameras foundCameras;
	public AndroidCameras getFoundCameras() {return foundCameras;}

	public AndroidCameraConf9() {
		foundCameras = new AndroidCameras();

		for (int id=0; id < getNumberOfCameras(); id++) {
			if (foundCameras.defaultC == null)
				foundCameras.defaultC = id;

			if (isFrontCamera(id)) {
				foundCameras.front = id;
			} else {
				foundCameras.rear = id;
			}
		}
	}
	
	public int getNumberOfCameras() {
		return Camera.getNumberOfCameras();
	}

	public int getCameraOrientation(int cameraId) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		Log.d("Camera info for ",cameraId,": orientation=",info.orientation);
		return info.orientation;
	}
	
	public boolean isFrontCamera(int cameraId) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		return info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT ? true : false;
	}

}
