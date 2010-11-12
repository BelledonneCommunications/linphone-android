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
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public abstract class AndroidCameraRecord implements SurfaceHolder.Callback {

	protected Camera camera;
	private static SurfaceView surfaceView; // should be initialized first...
	protected int rate;
	private int visibility = SurfaceView.GONE; // Automatically hidden
	private boolean visibilityChangeable = false;

	protected final SurfaceView getSurfaceView() {
		return surfaceView;
	}
	
	/**
	 * AndroidCameraRecord.setSurfaceView() should be called first.
	 * @param rate
	 */
	public AndroidCameraRecord(int rate) {
		camera=Camera.open();
		SurfaceHolder holder = surfaceView.getHolder();
		holder.addCallback(this);

		this.rate = rate;
	}

	
	/**
	 * AndroidCameraRecord.setSurfaceView() should be called first.
	 * @param rate
	 * @param visilibity
	 */
	public AndroidCameraRecord(int rate, int visilibity) {
		this(rate);
		this.visibility = visilibity;
	}


	public void surfaceCreated(SurfaceHolder holder) {
		try {
			camera.setPreviewDisplay(holder);
		}
		catch (Throwable t) {
			Log.e("PictureDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);
		}


	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Camera.Parameters parameters=camera.getParameters();

		parameters.setPreviewSize(width, height);
		parameters.setPreviewFrameRate(rate);
		camera.setParameters(parameters);

		camera.startPreview();

		visibilityChangeable = true;
		if (surfaceView.getVisibility() != visibility) {
			updateVisibility();
		}

	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		camera.stopPreview();
		camera.release();
		camera=null;
	}

	public void setPreviewCallBack(PreviewCallback cb) {
		camera.setPreviewCallback(cb);
	}

	private void updateVisibility() {
		if (!visibilityChangeable) {
			throw new IllegalStateException("Visilibity not changeable now");
		}

		surfaceView.setVisibility(visibility);
	}

	public void setVisibility(int visibility) {
		if (visibility == this.visibility) return;

		this.visibility = visibility;
		updateVisibility();
	}

	public static final void setSurfaceView(SurfaceView sv) {
		AndroidCameraRecord.surfaceView = sv;
	}
	
	
	/**
	 * Hook to add back a buffer for reuse in capture.
	 * Override in a version supporting addPreviewCallBackWithBuffer()
	 * @param buffer buffer to reuse
	 */
	public void addBackCaptureBuffer(byte[] buffer) {}
}
