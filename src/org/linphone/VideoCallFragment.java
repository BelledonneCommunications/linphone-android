package org.linphone;
/*
VideoCallFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

import android.annotation.TargetApi;
import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;

/**
 * @author Sylvain Berfini
 */
@TargetApi(5)
public class VideoCallFragment extends Fragment implements OnGestureListener, OnDoubleTapListener {
	private SurfaceView mVideoView;
	private SurfaceView mCaptureView;
	private AndroidVideoWindowImpl androidVideoWindowImpl;
	private InCallActivity inCallActivity;
	private GestureDetector mGestureDetector;
	private float mZoomFactor = 1;
	private float mZoomCenterX, mZoomCenterY;
	
	@SuppressWarnings("deprecation") // Warning useless because value is ignored and automatically set by new APIs.
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
        View view = inflater.inflate(R.layout.video, container, false);
        
		mGestureDetector = new GestureDetector(getActivity(), this); 
        
		mVideoView = (SurfaceView) view.findViewById(R.id.videoSurface);
		mCaptureView = (SurfaceView) view.findViewById(R.id.videoCaptureSurface);
		mCaptureView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // Warning useless because value is ignored and automatically set by new APIs.

		/* force surfaces Z ordering */
		fixZOrder(mVideoView, mCaptureView);
		
		androidVideoWindowImpl = new AndroidVideoWindowImpl(mVideoView, mCaptureView);
		androidVideoWindowImpl.setListener(new AndroidVideoWindowImpl.VideoWindowListener() {
			public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				LinphoneManager.getLc().setVideoWindow(vw);
				mVideoView = surface;
			}

			public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {
				Log.d("VIDEO WINDOW destroyed!\n");
				LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull(); 
				if (lc != null) {
					lc.setVideoWindow(null);
				}
			}

			public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				mCaptureView = surface;
				LinphoneManager.getLc().setPreviewWindow(mCaptureView);
			}

			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {
				// Remove references kept in jni code and restart camera
				LinphoneManager.getLc().setPreviewWindow(null);
			}
		});
		androidVideoWindowImpl.init();
		
		mVideoView.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				inCallActivity.displayVideoCallControlsIfHidden();
				return mGestureDetector.onTouchEvent(event);
			}
		});
		
		return view;
    }

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		inCallActivity = (InCallActivity) activity;
	}

	private void fixZOrder(SurfaceView video, SurfaceView preview) {
		video.setZOrderOnTop(false);
		preview.setZOrderOnTop(true);
		preview.setZOrderMediaOverlay(true); // Needed to be able to display control layout over
	}
	
	public void switchCamera() {
		try {
			int videoDeviceId = LinphoneManager.getLc().getVideoDevice();
			videoDeviceId = (videoDeviceId + 1) % AndroidCameraConfiguration.retrieveCameras().length;
			LinphoneManager.getLc().setVideoDevice(videoDeviceId);
			CallManager.getInstance().updateCall();
			
			// previous call will cause graph reconstruction -> regive preview
			// window
			if (mCaptureView != null) {
				LinphoneManager.getLc().setPreviewWindow(mCaptureView);
			}
		} catch (ArithmeticException ae) {
			Log.e("Cannot swtich camera : no camera");
		}
	}
	
	@Override
	public void onResume() {		
		super.onResume();
		
		if (mVideoView != null)
			((GLSurfaceView) mVideoView).onResume();
		
		if (androidVideoWindowImpl != null) {
			synchronized (androidVideoWindowImpl) {
				LinphoneManager.getLc().setVideoWindow(androidVideoWindowImpl);
			}
		}
	}

	@Override
	public void onPause() {		
		synchronized (androidVideoWindowImpl) {
			/*
			 * this call will destroy native opengl renderer which is used by
			 * androidVideoWindowImpl
			 */
			LinphoneManager.getLc().setVideoWindow(null);
		}
		
		super.onPause();
		
		if (mVideoView != null)
			((GLSurfaceView) mVideoView).onPause();
	}
	
	@Override
	public boolean onDown(MotionEvent e) {
		return true; // Needed to make the GestureDetector working
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (LinphoneUtils.isCallEstablished(LinphoneManager.getLc().getCurrentCall())) {
			if (mZoomFactor > 1) {
				// Video is zoomed, slide is used to change center of zoom
				if (distanceX > 0 && mZoomCenterX < 1) {
					mZoomCenterX += 0.01;
				} else if(distanceX < 0 && mZoomCenterX > 0) {
					mZoomCenterX -= 0.01;
				}
				
				if (mZoomCenterX > 1)
					mZoomCenterX = 1;
				if (mZoomCenterX < 0)
					mZoomCenterX = 0;
				
				LinphoneManager.getLc().getCurrentCall().zoomVideo(mZoomFactor, mZoomCenterX, mZoomCenterY);
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		if (LinphoneUtils.isCallEstablished(LinphoneManager.getLc().getCurrentCall())) {
			if (mZoomFactor == 1) {
				// Zoom to make the video fill the screen in height
				mZoomFactor = mVideoView.getHeight() / LinphoneManager.getLc().getPreferredVideoSize().height;
			}
			else {
				resetZoom();
			}
			
			LinphoneManager.getLc().getCurrentCall().zoomVideo(mZoomFactor, mZoomCenterX, mZoomCenterY);
			return true;
		}
		
		return false;
	}

	private void resetZoom() {
		mZoomFactor = 1;
		mZoomCenterX = mZoomCenterY = 0.5f;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		return false;
	}
	
	@Override
	public void onDestroy() {
		if (androidVideoWindowImpl != null) { 
			// Prevent linphone from crashing if correspondent hang up while you are rotating
			androidVideoWindowImpl.release();
		}
		super.onDestroy();
	}
}
