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

import android.util.Log;

class AndroidCameraConf {
	private static final String tag = "Linphone";

	public void findFrontAndRearCameraIds(int[] frontCameraId, int[] rearCameraId, int[] cameraId) {
		Log.i(tag, "Detecting cameras");
		if (Hacks.isGalaxyS()) {
			Log.d(tag, "Hack Galaxy S : has 2 cameras front=2; rear=1");
			frontCameraId[0] = 2;
			rearCameraId[0] = 1;
			cameraId[0] = rearCameraId[0];
			return;
		}

		// default to 0/0
	}

	public int getNumberOfCameras() {
		Log.i(tag, "Detecting the number of cameras");
		// Use hacks to guess the number of cameras
		if (Hacks.isGalaxyS()) {
			Log.d(tag, "Hack Galaxy S : has 2 cameras");
			return 2;
		} else
			return 1;
	}



	public int getCameraOrientation(int cameraId) {
		// Use hacks to guess orientation of the camera
		if (cameraId == 2 && Hacks.isGalaxyS()) {
			Log.d(tag, "Hack Galaxy S : rear camera id=2 ; mounted landscape");
			// mounted in landscape for a portrait phone orientation
			return 90;
		}
		return 0;
	}




	public boolean isFrontCamera(int cameraId) {
		// Use hacks to guess facing of the camera
		if (cameraId == 2 && Hacks.isGalaxyS()) {
			Log.d(tag, "Hack Galaxy S : front camera has id=2");
			return true;
		}

		return false;
	}
	
	

}
