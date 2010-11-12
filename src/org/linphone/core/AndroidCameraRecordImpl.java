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
import android.util.Log;

/**
 * Record from Android camera.
 *
 * @author Guillaume Beraudo
 *
 */
public class AndroidCameraRecordImpl extends AndroidCameraRecord implements PreviewCallback {

	private long filterCtxPtr;

	public AndroidCameraRecordImpl(long filterCtxPtr, int rate) {
		super(rate);
		this.filterCtxPtr = filterCtxPtr;
		setPreviewCallBack(this);
	}

	
	private native void putImage(long filterCtxPtr, byte[] buffer);

	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.d("onPreviewFrame: ", Integer.toString(data.length));
		putImage(filterCtxPtr, data);
	}

}
