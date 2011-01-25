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
package org.linphone.core;

import android.hardware.Camera;

public class AndroidCameraConf9 extends AndroidCameraConf {

	public void findFrontAndRearCameraIds9(Integer frontCameraId, Integer rearCameraId, Integer cameraId) {
		for (int id=0; id < getNumberOfCameras(); id++) {
			if (isFrontCamera(id)) {
				frontCameraId = id;
			} else {
				rearCameraId = id;
			}
		}
	}
	
	public int getNumberOfCameras() {
		return Camera.getNumberOfCameras();
	}

	public int getCameraOrientation(int cameraId) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		return info.orientation;
	}
	
	public boolean isFrontCamera(int cameraId) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, info);
		return info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT ? true : false;
	}
}
