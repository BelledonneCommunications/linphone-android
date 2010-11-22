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
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;


public abstract class AndroidCameraRecord {

	public static final int ANDROID_VERSION = Integer.parseInt(Build.VERSION.SDK);
	protected static Camera camera;
	private static SurfaceView surfaceView;

	protected int fps;
	protected int height;
	protected int width;
	private int longTermVisibility;

	private boolean visibilityChangeable = false;
	private PreviewCallback storedPreviewCallback;

	private static AndroidCameraRecord instance;
	private static Handler handler;
	private static boolean previewStarted;
	protected static int orientationCode;
	
	public AndroidCameraRecord() {
		// TODO check if another instance is loaded and kill it.
		instance = this;
	}
	
	public void setParameters(int height, int width, float fps, boolean hide) {
		this.fps = Math.round(fps);
		this.height = height;
		this.width = width;
		this.longTermVisibility = hide ? SurfaceView.GONE : SurfaceView.VISIBLE;
		
		if (surfaceView != null) {
			Log.d("Linphone", "Surfaceview defined and ready; starting video capture");
			instance.startPreview();
		} else {
			Log.w("Linphone", "Surfaceview not defined; postponning video capture");
		}
	}
	
	
	/*
	 * AndroidCameraRecord.setSurfaceView() should be called first, from the Activity code.
	 * It will start automatically
	 */
	private void startPreview() {
		assert surfaceView != null;

		if (previewStarted) {
			Log.w("Linphone", "Already started");
			return;
		}
		
		if (surfaceView.getVisibility() != SurfaceView.VISIBLE) {
			// Illegal state
			Log.e("Linphone", "Illegal state: video capture surface view is not visible");
			return;
		}
		

		camera=Camera.open();
		camera.setErrorCallback(new ErrorCallback() {
			public void onError(int error, Camera camera) {
				Log.e("Linphone", "Camera error : " + error);
			}
		});
		
		
		Camera.Parameters parameters=camera.getParameters();

		parameters.setPreviewSize(width, height);
		parameters.setPreviewFrameRate(fps);
		if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			Log.w("Linphone", "Auto Focus supported by camera device");
			parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		} else {
			Log.w("Linphone", "Auto Focus not supported by camera device");
			if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
				Log.w("Linphone", "Infinity Focus supported by camera device");
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
			} else {
				Log.w("Linphone", "Infinity Focus not supported by camera device");
			}
		}

		onSettingParameters(parameters);
		camera.setParameters(parameters);

		

		SurfaceHolder holder = surfaceView.getHolder();
		try {
			camera.setPreviewDisplay(holder);
		}
		catch (Throwable t) {
			Log.e("Linphone", "Exception in Video capture setPreviewDisplay()", t);
		}


		try {
			camera.startPreview();
			previewStarted = true;
		} catch (Throwable e) {
			Log.e("Linphone", "Can't start camera preview");
		}

		previewStarted = true;

		
		
		// Register callback to get capture buffer
		if (storedPreviewCallback != null) {
			reallySetPreviewCallback(camera, storedPreviewCallback);
		}
		

		visibilityChangeable = true;
		if (surfaceView.getVisibility() != longTermVisibility) {
			updateVisibility();
		}
		
		onCameraStarted(camera);
	}
	



	protected void onSettingParameters(Parameters parameters) {
		
	}

	/**
	 * Hook.
	 * @param camera
	 */
	public void onCameraStarted(Camera camera) {}

	public void setOrStorePreviewCallBack(PreviewCallback cb) {
		if (camera == null) {
			Log.w("Linphone", "Capture camera not ready, storing callback");
			this.storedPreviewCallback = cb;
			return;
		}
		
		reallySetPreviewCallback(camera, cb);
	}


	
	public static final void setSurfaceView(final SurfaceView sv, Handler mHandler) {
		AndroidCameraRecord.handler = mHandler;
		SurfaceHolder holder = sv.getHolder();
	    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		holder.addCallback(new Callback() {
			public void surfaceDestroyed(SurfaceHolder holder) {
				AndroidCameraRecord.surfaceView = null;
				
				if (camera == null) {
					Log.e("Linphone", "Video capture: illegal state: surface destroyed but camera is already null");
					return;
				}
				camera.setPreviewCallback(null); // TODO check if used whatever the SDK version
				camera.stopPreview();
				camera.release();
				camera=null;
				previewStarted = false;
				Log.w("Linphone", "Video capture Surface destroyed");
			}

			public void surfaceCreated(SurfaceHolder holder) {
				AndroidCameraRecord.surfaceView = sv;
				Log.w("Linphone", "Video capture surface created");
				
				if (instance != null) {
					instance.startPreview();
				}
				
				holder.isCreating();
			}
			
			public void surfaceChanged(SurfaceHolder holder, int format, int width,
					int height) {
				Log.w("Linphone", "Video capture surface changed");
			}
		});
	}
	
	

	private void updateVisibility() {
		if (!visibilityChangeable) {
			throw new IllegalStateException("Visilibity not changeable now");
		}

		handler.post(new Runnable() {
			public void run() {
				Log.d("Linphone", "Changing video capture surface view visibility :" + longTermVisibility);
				surfaceView.setVisibility(longTermVisibility);
			}
		});
	}

	public void setVisibility(int visibility) {
		if (visibility == this.longTermVisibility) return;

		this.longTermVisibility = visibility;
		updateVisibility();
	}

	
	public void stopCaptureCallback() {
		if (camera != null) {
			reallySetPreviewCallback(camera, null);
		}
	}
	
	protected void reallySetPreviewCallback(Camera camera, PreviewCallback cb) {
		camera.setPreviewCallback(cb);
	}

	public static void setOrientationCode(int orientation) {
		AndroidCameraRecord.orientationCode = (4 + 1 - orientation) % 4;
	}
	
	protected int getOrientationCode() {
		return orientationCode;
	}
}


