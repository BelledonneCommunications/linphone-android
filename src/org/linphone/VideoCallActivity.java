/*
VideoCallActivity.java
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
package org.linphone;



import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.Numpad;
import org.linphone.ui.ToggleImageButton;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

/**
 * For Android SDK >= 5
 * @author Guillaume Beraudo
 *
 */
public class VideoCallActivity extends Activity implements LinphoneOnCallStateChangedListener, OnClickListener {
	private final static int DELAY_BEFORE_HIDING_CONTROLS = 2000;
	private static final int numpadDialogId = 1;
	
	private SurfaceView mVideoViewReady;
	private SurfaceView mVideoCaptureViewReady;
	public static boolean launched = false;
	private LinphoneCall videoCall;
	private WakeLock mWakeLock;
	private Handler refreshHandler = new Handler();
	private Handler controlsHandler = new Handler();
	AndroidVideoWindowImpl androidVideoWindowImpl;
	private Runnable mCallQualityUpdater, mControls;
	private LinearLayout mControlsLayout;
	private boolean shouldRestartVideoOnResume = false;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!LinphoneManager.isInstanciated() || LinphoneManager.getLc().getCallsNb() == 0) {
			Log.e("No service running: avoid crash by finishing ", getClass().getName());
			// super.onCreate called earlier
			finish();
			return;
		}

		setContentView(R.layout.videocall);

		SurfaceView videoView = (SurfaceView) findViewById(R.id.video_surface); 

		//((FrameLayout) findViewById(R.id.video_frame)).bringChildToFront(findViewById(R.id.imageView1));
		
		SurfaceView captureView = (SurfaceView) findViewById(R.id.video_capture_surface);
		captureView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		/* force surfaces Z ordering */
		fixZOrder(videoView, captureView);
	
		androidVideoWindowImpl = new AndroidVideoWindowImpl(videoView, captureView);
		androidVideoWindowImpl.setListener(new AndroidVideoWindowImpl.VideoWindowListener() {
			
			public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				LinphoneManager.getLc().setVideoWindow(vw);
				mVideoViewReady = surface;
			}
			
			public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {
				Log.d("VIDEO WINDOW destroyed!\n");
				LinphoneManager.getLc().setVideoWindow(null);
			}
			
			public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				mVideoCaptureViewReady = surface;
				LinphoneManager.getLc().setPreviewWindow(mVideoCaptureViewReady);
			}
			
			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {
				// Remove references kept in jni code and restart camera
				 LinphoneManager.getLc().setPreviewWindow(null);
			}
		});
		
		androidVideoWindowImpl.init();

		videoCall = LinphoneManager.getLc().getCurrentCall();		
		if (videoCall != null) {
			LinphoneManager lm = LinphoneManager.getInstance();
			if (!lm.shareMyCamera() && !lm.isVideoInitiator() && videoCall.cameraEnabled()) {
				lm.sendStaticImage(true);
			}
			
			updatePreview(videoCall.cameraEnabled());
		}
			
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,Log.TAG);
		mWakeLock.acquire();
		
		mControlsLayout = (LinearLayout) findViewById(R.id.incall_controls_layout);
		videoView.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (mControlsLayout != null && mControlsLayout.getVisibility() == View.GONE)
				{
					mControlsLayout.setVisibility(View.VISIBLE);
					controlsHandler.postDelayed(mControls = new Runnable(){
						public void run() {
							mControlsLayout.setVisibility(View.GONE);
						}
					},DELAY_BEFORE_HIDING_CONTROLS);
					
					return true;
				}
				return false;
			}
		});
		
		if (!AndroidCameraConfiguration.hasSeveralCameras()) {
			findViewById(R.id.switch_camera).setVisibility(View.GONE);
		}
			
		if (Version.isXLargeScreen(this))
		{
			findViewById(R.id.toggleMuteMic).setOnClickListener(this);
			findViewById(R.id.toggleSpeaker).setOnClickListener(this);
			findViewById(R.id.incallNumpadShow).setOnClickListener(this);
			findViewById(R.id.addCall).setOnClickListener(this);
			findViewById(R.id.incallHang).setOnClickListener(this);
			findViewById(R.id.switch_camera).setOnClickListener(this);
			findViewById(R.id.conf_simple_pause).setOnClickListener(this);
			findViewById(R.id.conf_simple_video).setOnClickListener(this);
			
			if (LinphoneManager.getInstance().isSpeakerOn())
			{
				ToggleImageButton speaker = (ToggleImageButton) findViewById(R.id.toggleSpeaker);
				speaker.setChecked(true);
				speaker.setEnabled(false);
			}
		}
		
	}
	
	void updateQualityOfSignalIcon(float quality)
	{
		ImageView qos = (ImageView) findViewById(R.id.QoS);
		if (quality >= 4) // Good Quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_4));
		}
		else if (quality >= 3) // Average quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_3));
		}
		else if (quality >= 2) // Low quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_2));
		}
		else if (quality >= 1) // Very low quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_1));
		}
		else // Worst quality
		{
			qos.setImageDrawable(getResources().getDrawable(R.drawable.stat_sys_signal_0));
		}
	}
	
	void updatePreview(boolean cameraCaptureEnabled) {
		mVideoCaptureViewReady = null;
		if (cameraCaptureEnabled) {
			findViewById(R.id.imageView1).setVisibility(View.INVISIBLE);
			findViewById(R.id.video_capture_surface).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.video_capture_surface).setVisibility(View.INVISIBLE);
			findViewById(R.id.imageView1).setVisibility(View.VISIBLE);
		}
		findViewById(R.id.video_frame).requestLayout();
	}
	
	void fixZOrder(SurfaceView video, SurfaceView preview) {
		video.setZOrderOnTop(false);
		preview.setZOrderOnTop(true);
	}

 
	@Override
	protected void onResume() {
		super.onResume();
		if (mVideoViewReady != null)
			((GLSurfaceView)mVideoViewReady).onResume();
		synchronized (androidVideoWindowImpl) {
			LinphoneManager.getLc().setVideoWindow(androidVideoWindowImpl);
		}
		launched=true;
		LinphoneManager.addListener(this);
		
		refreshHandler.postDelayed(mCallQualityUpdater=new Runnable(){
			LinphoneCall mCurrentCall=LinphoneManager.getLc().getCurrentCall();
			public void run() {
				if (mCurrentCall==null){
					mCallQualityUpdater=null;
					return;
				}
				int oldQuality = 0;
				float newQuality = mCurrentCall.getCurrentQuality();
				if ((int) newQuality != oldQuality){
					updateQualityOfSignalIcon(newQuality);
				}
				if (launched){
					refreshHandler.postDelayed(this, 1000);
				}else mCallQualityUpdater=null;
			}
		},1000);
		
		if (mControlsLayout != null)
			mControlsLayout.setVisibility(View.GONE);
		
		if (shouldRestartVideoOnResume) {
			LinphoneManager.getLc().getCurrentCall().enableCamera(true);
			shouldRestartVideoOnResume = false;
		}
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeSoftAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onDestroy() {
		if (androidVideoWindowImpl != null) { // Prevent linphone from crashing if correspondent hang up while you are rotating
			androidVideoWindowImpl.release();
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d("onPause VideoCallActivity (isFinishing:", isFinishing(), ", inCall:", LinphoneManager.getLc().isIncall(),")");
		LinphoneManager.removeListener(this);
		if (isFinishing()) {
			videoCall = null; // release reference
		} else {
			// Send NoWebcam since Android 4.0 can't get the video from the webcam if the activity is not in foreground
			shouldRestartVideoOnResume = true;
			LinphoneManager.getLc().getCurrentCall().enableCamera(false);
			
		}
		launched=false;
		synchronized (androidVideoWindowImpl) {
			/* this call will destroy native opengl renderer
			 * which is used by androidVideoWindowImpl
			 */
			LinphoneManager.getLc().setVideoWindow(null);
		}

		if (mCallQualityUpdater!=null){
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater=null;
		}
		if (mControls != null) {
			controlsHandler.removeCallbacks(mControls);
			mControls = null;
		}
		
		if (mWakeLock.isHeld())	mWakeLock.release();
		super.onPause();
		if (mVideoViewReady != null)
			((GLSurfaceView)mVideoViewReady).onPause();
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, State state,
			String message) {
		if (call == videoCall && state == State.CallEnd) {
			BandwidthManager.getInstance().setUserRestriction(false);
			LinphoneManager.getInstance().resetCameraFromPreferences();
			finish();
		}
	}
	
	private void resizePreview() {
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int rotation = display.getRotation();
		LayoutParams params;
		
		int w, h;
		if (Version.isXLargeScreen(this)) {
			w = 176;
			h = 148;
		} else {
			w = 88;
			h = 74;
		}
		
		if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
			params = new LayoutParams(h, w);
		} else {
			params = new LayoutParams(w, h);
		}
		params.setMargins(0, 0, 15, 15);
		params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		mVideoCaptureViewReady.setLayoutParams(params);
	}
	
	public void onConfigurationChanged(Configuration newConfig) {
		resizePreview();
		super.onConfigurationChanged(null);
	}
	
	private void resetControlsLayoutExpiration() {
		if (mControls != null) {
			controlsHandler.removeCallbacks(mControls);
		}
		
		controlsHandler.postDelayed(mControls = new Runnable(){
			public void run() {
				mControlsLayout.setVisibility(View.GONE);
			}
		},DELAY_BEFORE_HIDING_CONTROLS);
	}

	public void onClick(View v) {
		resetControlsLayoutExpiration();
		switch (v.getId()) {
			case R.id.incallHang:
				terminateCurrentCallOrConferenceOrAll();
				break;
			case R.id.toggleSpeaker:
				if (((Checkable) v).isChecked()) {
					LinphoneManager.getInstance().routeAudioToSpeaker();
				} else {
					LinphoneManager.getInstance().routeAudioToReceiver();
				}
				break;
			case R.id.incallNumpadShow:
				showDialog(numpadDialogId);
				break;
			case R.id.toggleMuteMic:
				LinphoneManager.getLc().muteMic(((Checkable) v).isChecked());
				break;
			case R.id.switch_camera:
				int id = LinphoneManager.getLc().getVideoDevice();
				id = (id + 1) % AndroidCameraConfiguration.retrieveCameras().length;
				LinphoneManager.getLc().setVideoDevice(id);
				CallManager.getInstance().updateCall();
				// previous call will cause graph reconstruction -> regive preview window
				if (mVideoCaptureViewReady != null)
					LinphoneManager.getLc().setPreviewWindow(mVideoCaptureViewReady);
				break;
			case R.id.conf_simple_pause:
				finish();
				LinphoneActivity.instance().startIncallActivity();
				LinphoneManager.getLc().pauseCall(videoCall);
				break;
			case R.id.conf_simple_video:
				LinphoneCallParams params = videoCall.getCurrentParamsCopy();
				params.setVideoEnabled(false);
				LinphoneManager.getLc().updateCall(videoCall, params);
				break;	
			case R.id.addCall:
				finish();
				break;
		}
	}
	
	protected Dialog onCreateDialog(final int id) {
		switch (id) {
		case numpadDialogId:
			Numpad numpad = new Numpad(this, true);
			return new AlertDialog.Builder(this).setView(numpad)
					 .setPositiveButton(R.string.close_button_text, new
					 DialogInterface.OnClickListener() {
						 public void onClick(DialogInterface dialog, int whichButton)
							 {
							 	dismissDialog(id);
							 }
						 })
					.create();
		default:
			throw new IllegalArgumentException("unkown dialog id " + id);
		}
	}
	
	private void terminateCurrentCallOrConferenceOrAll() {
		LinphoneCall currentCall = LinphoneManager.getLc().getCurrentCall();
		if (currentCall != null) {
			LinphoneManager.getLc().terminateCall(currentCall);
		} else if (LinphoneManager.getLc().isInConference()) {
			LinphoneManager.getLc().terminateConference();
		} else {
			LinphoneManager.getLc().terminateAllCalls();
		}
	}
}
