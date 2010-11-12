/*
JavaCameraRecordImpl.java
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
package org.linphone.core.tutorials;

import org.linphone.core.AndroidCameraRecord;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.widget.TextView;

public class JavaCameraRecordImpl extends AndroidCameraRecord implements PreviewCallback {

	private TextView debug;
	private long count = 0;
	private float averageCalledRate;
	private int averageWindowSize = 2 * rate;

	private long startTime;
	private long endTime;

	
	public JavaCameraRecordImpl(int rate) {
		super(rate);
		setPreviewCallBack(this);
	}

	public JavaCameraRecordImpl(int rate, int visilibity) {
		super(rate, visilibity);
		setPreviewCallBack(this);
	}

	public void setDebug(TextView debug) {
		this.debug = debug;
	}

	public void onPreviewFrame(byte[] data, Camera camera) {

		if ((count % averageWindowSize) == 0) {
			endTime = System.currentTimeMillis();
			averageCalledRate = (100000 * averageWindowSize) / (endTime - startTime);
			averageCalledRate /= 100f;
			startTime = endTime;
		}

		count++;
		
		String msg =  "Frame " + count + ": " + data.length + "bytes (avg="+averageCalledRate+"frames/s)";
		if (debug != null) debug.setText(msg);
		Log.d("onPreviewFrame:", msg);
	}

}
