/*
AndroidCameraConf.java
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

import org.linphone.core.Hacks;
import org.linphone.core.Log;


class AndroidCameraConf5 implements AndroidCameraConf {
	private AndroidCameras foundCameras;
	public AndroidCameras getFoundCameras() {return foundCameras;}

	public AndroidCameraConf5() {
		Log.i("Detecting cameras");
		
		// Defaults
		foundCameras = new AndroidCameras();

		if (Hacks.isGalaxySOrTab()) {
			Log.d("Hack Galaxy S : has one or more cameras");
			if (Hacks.isGalaxySOrTabWithFrontCamera()) {
				Log.d("Hack Galaxy S : HAS a front camera with id=2");
				foundCameras.front = 2;
			} else {
				Log.d("Hack Galaxy S : NO front camera");
			}
			Log.d("Hack Galaxy S : HAS a rear camera with id=1");
			foundCameras.rear = 1;
			foundCameras.defaultC = foundCameras.rear;
		} else if (Hacks.hasTwoCamerasRear0Front1()) {
			Log.d("Hack SPHD700 has 2 cameras a rear with id=0 and a front with id=1");
			foundCameras.front = 1;
		}

	}

	public int getNumberOfCameras() {
		Log.i("Detecting the number of cameras");
		if (Hacks.hasTwoCamerasRear0Front1() || Hacks.isGalaxySOrTabWithFrontCamera()) {
			Log.d("Hack: we know this model has 2 cameras");
			return 2;
		} else
			return 1;
	}



	public int getCameraOrientation(int cameraId) {
		// Use hacks to guess orientation of the camera
		if (Hacks.isGalaxySOrTab() && isFrontCamera(cameraId)) {
			Log.d("Hack Galaxy S : front camera mounted landscape");
			// mounted in landscape for a portrait phone orientation
			//  |^^^^^^^^|
			//  |  ____  |
			//  | |____| |
			//  |        |
			//  |        |
			//  | Phone  |
			//  |________|
			return 180;
		}
		return 90;
	}



	public boolean isFrontCamera(int cameraId) {
		// Use hacks to guess facing of the camera
		if (cameraId == 2 && Hacks.isGalaxySOrTab()) {
			Log.d("Hack Galaxy S : front camera has id=2");
			return true;
		} else if (cameraId == 1 && Hacks.hasTwoCamerasRear0Front1()) {
			Log.d("Hack SPHD700 : front camera has id=1");
			return true;
		}

		return false;
	}
	
	

}
