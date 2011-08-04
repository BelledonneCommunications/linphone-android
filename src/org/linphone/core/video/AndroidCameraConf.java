/*
AndroidCameraConf.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import org.linphone.core.VideoSize;


/**
 * @author Guillaume Beraudo
 *
 */
interface AndroidCameraConf {

	AndroidCameras getFoundCameras();

	int getNumberOfCameras();

	int getCameraOrientation(int cameraId);

	boolean isFrontCamera(int cameraId);

	List<VideoSize> getSupportedPreviewSizes(int cameraId);

	/**
	 * Default: no front; rear=0; default=rear
	 * @author Guillaume Beraudo
	 *
	 */
	class AndroidCameras {
		Integer front;
		Integer rear = 0;
		Integer defaultC = rear;
		
		boolean hasFrontCamera() { return front != null; }
		boolean hasRearCamera() { return rear != null; }
		boolean hasSeveralCameras() { return front != rear && front != null; }

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Cameras :");
			if (rear != null) sb.append(" rear=").append(rear);
			if (front != null) sb.append(" front=").append(front);
			if (defaultC != null)  sb.append(" default=").append(defaultC);
			return sb.toString();
		}
	}
}