package org.linphone;
/*
InCallActivity.java
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
import java.util.Arrays;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphonePlayer;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.Numpad;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.app.Fragment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class InCallActivity extends Activity implements OnClickListener {
	private final static int SECONDS_BEFORE_HIDING_CONTROLS = 3000;
	private final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;
	
	private static InCallActivity instance;

	private Handler mControlsHandler = new Handler(); 
	private Runnable mControls;
	private ImageView switchCamera;
	private RelativeLayout mActiveCallHeader;
	private ImageView pause, hangUp, dialer, video, micro, speaker, options, addCall, transfer, conference, conferenceStatus;
	private TextView audioRoute, routeSpeaker, routeReceiver, routeBluetooth;
	private LinearLayout routeLayout, mNoCurrentCall;
	private ProgressBar videoProgress;
	private StatusFragment status;
	private AudioCallFragment audioCallFragment;
	private VideoCallFragment videoCallFragment;
	private boolean isSpeakerEnabled = false, isMicMuted = false, isTransferAllowed, isAnimationDisabled;
	private LinearLayout mControlsLayout;
	private Numpad numpad;
	private int cameraNumber;
	private Animation slideOutLeftToRight, slideInRightToLeft, slideInBottomToTop, slideInTopToBottom, slideOutBottomToTop, slideOutTopToBottom;
	private CountDownTimer timer;
	private boolean isVideoCallPaused = false;
	AcceptCallUpdateDialogFragment callUpdateDialog;
	
	private LinearLayout callsList, conferenceList;
	private LayoutInflater inflater;
	private ViewGroup container;
	private boolean isConferenceRunning = false;
	private LinphoneCoreListenerBase mListener;
	
	public static InCallActivity instance() {
		return instance;
	}
	
	public static boolean isInstanciated() {
		return instance != null;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setContentView(R.layout.incall);
        
        isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

		if(!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			BluetoothManager.getInstance().initBluetooth();
		}

        isAnimationDisabled = getApplicationContext().getResources().getBoolean(R.bool.disable_animations) || !LinphonePreferences.instance().areAnimationsEnabled();
        cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;
        
        mListener = new LinphoneCoreListenerBase(){
        	@Override
        	public void callState(LinphoneCore lc, final LinphoneCall call, LinphoneCall.State state, String message) {
        		if (LinphoneManager.getLc().getCallsNb() == 0) {
        			finish();
        			return;
        		}

				Log.w("State " + message);

        		if (state == State.IncomingReceived) {
        			startIncomingCallActivity();
        			return;
        		}
        		
        		if (state == State.Paused || state == State.PausedByRemote ||  state == State.Pausing) {
					if(LinphoneManager.getLc().getCurrentCall() != null)
						Log.w(LinphoneManager.getLc().getCurrentCall().getRemoteContact());
					enabledVideoButton(false);
        			if(isVideoEnabled(call)){
        				showAudioView();
        			}
        		}
        		
        		if (state == State.Resuming) {
        			if(LinphonePreferences.instance().isVideoEnabled()){
        				status.refreshStatusItems(call, isVideoEnabled(call));
        				if(call.getCurrentParamsCopy().getVideoEnabled()){
        					showVideoView();
        				}
        			}
        		}

        		if (state == State.StreamsRunning) {
        			switchVideo(isVideoEnabled(call));
					//Check media in progress
					if(LinphonePreferences.instance().isVideoEnabled() && !call.mediaInProgress()){
						enabledVideoButton(true);
					}

        			LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);

        			isMicMuted = LinphoneManager.getLc().isMicMuted();
        			enableAndRefreshInCallActions();
        			
        			if (status != null) {
        				videoProgress.setVisibility(View.GONE);
        				//status.refreshStatusItems(call, isVideoEnabled(call));
        			}
        		}
        		
        		if (state == State.CallUpdatedByRemote) {
        			// If the correspondent proposes video while audio call
        			boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
        			if (!videoEnabled) {
        				acceptCallUpdate(false);
        				return;
        			}
        			
        			boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
        			boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
        			boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
        			if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
        				showAcceptCallUpdateDialog();
        				
        				timer = new CountDownTimer(SECONDS_BEFORE_DENYING_CALL_UPDATE, 1000) {
        					public void onTick(long millisUntilFinished) { }
        					public void onFinish() {
								if (callUpdateDialog != null)
									callUpdateDialog.dismiss();
        						acceptCallUpdate(false);
        			    	}
        				}.start();
        			} 
//        			else if (remoteVideo && !LinphoneManager.getLc().isInConference() && autoAcceptCameraPolicy) {
//        				mHandler.post(new Runnable() {
//        					@Override
//        					public void run() {
//        						acceptCallUpdate(true);
//        					}
//        				});
//        			}
        		}

				refreshIncallUi();
        		
        		transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
        	}
        	
        	@Override
        	public void callEncryptionChanged(LinphoneCore lc, final LinphoneCall call, boolean encrypted, String authenticationToken) {
        		if (status != null) {
        			status.refreshStatusItems(call, call.getCurrentParamsCopy().getVideoEnabled());
        		}
        	}
        	
        };
        
        if (findViewById(R.id.fragmentContainer) != null) {
            initUI();
            
            if (LinphoneManager.getLc().getCallsNb() > 0) {
            	LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

            	if (LinphoneUtils.isCallEstablished(call)) {
	    			enableAndRefreshInCallActions();
            	}
            }
            
            if (savedInstanceState != null) { 
            	// Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
            	isSpeakerEnabled = savedInstanceState.getBoolean("Speaker");
            	isMicMuted = savedInstanceState.getBoolean("Mic");
            	isVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
            	//refreshInCallActions();
            	return;
            }
            
            Fragment callFragment;
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
            	callFragment = new VideoCallFragment();
            	videoCallFragment = (VideoCallFragment) callFragment;
            	isSpeakerEnabled = true;
            	
            	if (cameraNumber > 1) {
            		switchCamera.setVisibility(View.VISIBLE); 
            	}
            } else {
            	callFragment = new AudioCallFragment();
            	audioCallFragment = (AudioCallFragment) callFragment;
        		switchCamera.setVisibility(View.INVISIBLE);
            }

			if(BluetoothManager.getInstance().isBluetoothHeadsetAvailable()){
				BluetoothManager.getInstance().routeAudioToBluetooth();
			}

            callFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();

        }
	}
	
	private boolean isVideoEnabled(LinphoneCall call) {
		if(call != null){
			return call.getCurrentParamsCopy().getVideoEnabled();
		}
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("Speaker", LinphoneManager.getLc().isSpeakerEnabled());
		outState.putBoolean("Mic", LinphoneManager.getLc().isMicMuted());
		outState.putBoolean("VideoCallPaused", isVideoCallPaused);
		
		super.onSaveInstanceState(outState);
	}
	
	private boolean isTablet() {
		return getResources().getBoolean(R.bool.isTablet);
	}
	
	private void initUI() {
		inflater = LayoutInflater.from(this);
		container = (ViewGroup) findViewById(R.id.topLayout);
        callsList = (LinearLayout) findViewById(R.id.calls);
		conferenceList = (LinearLayout) findViewById(R.id.conferenceCalls);

		//TopBar
		video = (ImageView) findViewById(R.id.video);
		video.setOnClickListener(this);
		enabledVideoButton(false);

		videoProgress =  (ProgressBar) findViewById(R.id.videoInProgress);
		videoProgress.setVisibility(View.GONE);

		micro = (ImageView) findViewById(R.id.micro);
		micro.setOnClickListener(this);

		speaker = (ImageView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);

		options = (ImageView) findViewById(R.id.options);
		options.setOnClickListener(this);
		options.setEnabled(false);

		//BottonBar
		hangUp = (ImageView) findViewById(R.id.hangUp);
		hangUp.setOnClickListener(this);

		dialer = (ImageView) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);

		numpad = (Numpad) findViewById(R.id.numpad);

		//Others

		//Active Call
		pause = (ImageView) findViewById(R.id.pause);
		pause.setOnClickListener(this);
		pause.setEnabled(false);

		mActiveCallHeader = (RelativeLayout) findViewById(R.id.activeCallHeader);
		mNoCurrentCall = (LinearLayout) findViewById(R.id.noCurrentCall);

		/*if(isTablet()){
			speaker.setEnabled(false);
		}*/

//		speaker.setEnabled(false);


		//Options
		addCall = (ImageView) findViewById(R.id.addCall);
		addCall.setOnClickListener(this);
		addCall.setEnabled(false);

		transfer = (ImageView) findViewById(R.id.transfer);
		transfer.setOnClickListener(this);
		transfer.setEnabled(false);

		conference = (ImageView) findViewById(R.id.conference);
		conference.setEnabled(false);
		conference.setOnClickListener(this);


		
		
		/*try {
			routeLayout = (LinearLayout) findViewById(R.id.routesLayout);
			audioRoute = (TextView) findViewById(R.id.audioRoute);
			audioRoute.setOnClickListener(this);
			routeSpeaker = (TextView) findViewById(R.id.routeSpeaker);
			routeSpeaker.setOnClickListener(this);
			routeReceiver = (TextView) findViewById(R.id.routeReceiver);
			routeReceiver.setOnClickListener(this);
			routeBluetooth = (TextView) findViewById(R.id.routeBluetooth);
			routeBluetooth.setOnClickListener(this);
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (1)");
		}*/
		
		switchCamera = (ImageView) findViewById(R.id.switchCamera);
		switchCamera.setOnClickListener(this);
		
		mControlsLayout = (LinearLayout) findViewById(R.id.menu);
		
        if (!isTransferAllowed) {
        	addCall.setBackgroundResource(R.drawable.options_add_call);
        }

        if (!isAnimationDisabled) {
	        slideInRightToLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_right_to_left);
	        slideOutLeftToRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_left_to_right);
	        slideInBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom_to_top);
	        slideInTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_top_to_bottom);
	        slideOutBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom_to_top);
	        slideOutTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_out_top_to_bottom);
        }

		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			try {
				if (routeLayout != null)
					routeLayout.setVisibility(View.VISIBLE);
				audioRoute.setVisibility(View.VISIBLE);
				speaker.setVisibility(View.GONE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (2)"); }
		} else {
			try {
				if (routeLayout != null)
					routeLayout.setVisibility(View.GONE);
				audioRoute.setVisibility(View.GONE);
				speaker.setVisibility(View.VISIBLE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (3)"); }
		}
		
		LinphoneManager.getInstance().changeStatusToOnThePhone();
	}


	private void refreshIncallUi(){
		refreshInCallActions();
		refreshCallList(getResources());
	}
	
	private void refreshInCallActions() {
		if (!LinphonePreferences.instance().isVideoEnabled() || isConferenceRunning) {
			enabledVideoButton(false);
		} else {
			if(video.isEnabled()) {
				if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
					video.setImageResource(R.drawable.camera_selected);
					videoProgress.setVisibility(View.INVISIBLE);
				} else {
					video.setImageResource(R.drawable.camera_default);
				}
			}
		}

		if (isSpeakerEnabled) {
			speaker.setImageResource(R.drawable.speaker_selected);
		} else {
			speaker.setImageResource(R.drawable.speaker_default);
		}

		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_selected);
		} else {
			micro.setImageResource(R.drawable.micro_default);
		}




		
		try {

				/*routeSpeaker.setBackgroundResource(R.drawable.route_speaker_on);
				routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
				routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);*/

				/*routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);
				if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
					routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
					routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_on);
				} else {
					routeReceiver.setBackgroundResource(R.drawable.route_receiver_on);
					routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
				}*/
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (4)");
		}
		


		if (LinphoneManager.getLc().getCallsNb() > 1) {
			//pause.setVisibility(View.GONE);
		} else {
			//pause.setVisibility(View.VISIBLE);

			List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.Paused));
			if (pausedCalls.size() == 1) {
				//pause.setBackgroundResource(R.drawable.pa);
			} else {
				//pause.setBackgroundResource(R.drawable.pause_off);
			}
		}
	}
	
	private void enableAndRefreshInCallActions() {
		addCall.setEnabled(LinphoneManager.getLc().getCallsNb() < LinphoneManager.getLc().getMaxCalls());
		transfer.setEnabled(getResources().getBoolean(R.bool.allow_transfers));
		options.setEnabled(!getResources().getBoolean(R.bool.disable_options_in_call) && (addCall.isEnabled() || transfer.isEnabled()));

		if(LinphoneManager.getLc().getCurrentCall() != null && LinphonePreferences.instance().isVideoEnabled() && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()) {
			enabledVideoButton(true);
		}
		micro.setEnabled(true);
		if(!isTablet()){
			speaker.setEnabled(true);
    	}
		transfer.setEnabled(true);
		pause.setEnabled(true);
		dialer.setEnabled(true);
		conference.setEnabled(true);
		refreshInCallActions();
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			displayVideoCallControlsIfHidden();
		}

		if (id == R.id.video) {
			enabledOrDisabledVideo(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()));
		} 
		else if (id == R.id.micro) {
			toggleMicro();
		} 
		else if (id == R.id.speaker) {
			toggleSpeaker();
		} 
		else if (id == R.id.addCall) {
			goBackToDialer();
		} 
		else if (id == R.id.pause) {
			pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
		}
		else if (id == R.id.hangUp) {
			hangUp();
		} 
		else if (id == R.id.dialer) {
			hideOrDisplayNumpad();
		}
		else if (id == R.id.conference) {
			enterConference();
			hideOrDisplayCallOptions();
		}
		else if (id == R.id.switchCamera) {
			if (videoCallFragment != null) {
				videoCallFragment.switchCamera();
			}
		}
		else if (id == R.id.transfer) {
			goBackToDialerAndDisplayTransferButton();
		}
		else if (id == R.id.options) {
			hideOrDisplayCallOptions();
		}
		else if (id == R.id.audioRoute) {
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeBluetooth) {
			if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
				isSpeakerEnabled = false;
				/*routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_on);
				routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
				routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);*/
			}
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeReceiver) {
			LinphoneManager.getInstance().routeAudioToReceiver();
			isSpeakerEnabled = false;
			/*routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
			routeReceiver.setBackgroundResource(R.drawable.route_receiver_on);
			routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);*/
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeSpeaker) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
			/*routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
			routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
			routeSpeaker.setBackgroundResource(R.drawable.route_speaker_on);*/
			hideOrDisplayAudioRoutes();
		}
		
		else if (id == R.id.callStatus) {
			LinphoneCall call = (LinphoneCall) v.getTag();
			pauseOrResumeCall(call);
		}
		else if (id == R.id.conferenceStatus) {
			pauseOrResumeConference();
		}
	}

	private void enabledVideoButton(boolean enabled){
		if(enabled) {
			video.setEnabled(true);
			video.setImageResource(R.drawable.camera_default);
		} else {
			video.setEnabled(false);
			video.setImageResource(R.drawable.camera_disabled);
		}
	}

	private void enabledOrDisabledVideo(final boolean isVideoEnabled) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}
		
		if (isVideoEnabled) {
			LinphoneCallParams params = call.getCurrentParamsCopy();
			params.setVideoEnabled(false);
			LinphoneManager.getLc().updateCall(call, params);
		} else {
			videoProgress.setVisibility(View.VISIBLE);
			if (!call.getRemoteParams().isLowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	public void displayCustomToast(final String message, final int duration) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}
	
	private void switchVideo(final boolean displayVideo) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}
		
		//Check if the call is not terminated
		if(call.getState() == State.CallEnd || call.getState() == State.CallReleased) return;
		
		if (!displayVideo) {
			showAudioView();
		} else {
			if (!call.getRemoteParams().isLowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
				if (videoCallFragment == null || !videoCallFragment.isVisible())
					showVideoView();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}
	
	private void showAudioView() {
		LinphoneManager.startProximitySensorForActivity(InCallActivity.this);
		replaceFragmentVideoByAudio();
		setCallControlsVisibleAndRemoveCallbacks();
	}
	
	private void showVideoView() {
		if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			Log.w("Bluetooth not available, using speaker");
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
		}
		refreshInCallActions();
		
		LinphoneManager.stopProximitySensorForActivity(InCallActivity.this);
		replaceFragmentAudioByVideo();
		displayVideoCallControlsIfHidden();
	}

	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new AudioCallFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, audioCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}
	
	private void replaceFragmentAudioByVideo() {
//		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
		mControlsLayout.setVisibility(View.GONE);
		switchCamera.setVisibility(View.INVISIBLE);
		videoCallFragment = new VideoCallFragment();
		
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void displayOrHideCallsHeader(boolean display){
		if(display){
			callsList.setVisibility(View.VISIBLE);
			mActiveCallHeader.setVisibility(View.VISIBLE);
		} else {
			callsList.setVisibility(View.GONE);
			mActiveCallHeader.setVisibility(View.GONE);
		}
	}

	private void displayNoCurrentCall(){

	}
	
	private void toggleMicro() {
		LinphoneCore lc = LinphoneManager.getLc();
		isMicMuted = !isMicMuted;
		lc.muteMic(isMicMuted);
		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_selected);
		} else {
			micro.setImageResource(R.drawable.micro_default);
		}
	}
	
	private void toggleSpeaker() {
		isSpeakerEnabled = !isSpeakerEnabled;
		if (isSpeakerEnabled) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			speaker.setImageResource(R.drawable.speaker_selected);
			LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
		} else {
			Log.d("Toggle speaker off, routing back to earpiece");
			LinphoneManager.getInstance().routeAudioToReceiver();
			speaker.setImageResource(R.drawable.speaker_default);
		}
	}
	
	private void pauseOrResumeCall() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null && lc.getCallsNb() >= 1) {
			LinphoneCall call = lc.getCalls()[0];
			pauseOrResumeCall(call);
		}
	}
	
	public void pauseOrResumeCall(LinphoneCall call) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (call != null && LinphoneUtils.isCallRunning(call)) {
			lc.pauseCall(call);
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				isVideoCallPaused = true;
			}
			pause.setImageResource(R.drawable.pause_big_over_selected);
		} else if (call != null) {
			if (call.getState() == State.Paused) {
				lc.resumeCall(call);
				if (isVideoCallPaused) {
					isVideoCallPaused = false;
				}
				pause.setImageResource(R.drawable.pause_big_default);
			}
		}
	}
	
	private void hangUp() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCall currentCall = lc.getCurrentCall();
		
		if (currentCall != null) {
			lc.terminateCall(currentCall);
		} else if (lc.isInConference()) {
			lc.terminateConference();
		} else {
			lc.terminateAllCalls();
		}
	}
	
	public void displayVideoCallControlsIfHidden() {
		if (mControlsLayout != null) {
			if (mControlsLayout.getVisibility() != View.VISIBLE) {
				if (isAnimationDisabled) {
					mControlsLayout.setVisibility(View.VISIBLE);
					displayOrHideCallsHeader(false);
					if (cameraNumber > 1) {
	            		switchCamera.setVisibility(View.VISIBLE); 
	            	}
				} else {
					Animation animation = slideInBottomToTop;
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
							mControlsLayout.setVisibility(View.VISIBLE);
							displayOrHideCallsHeader(false);
							if (cameraNumber > 1) {
			            		switchCamera.setVisibility(View.VISIBLE); 
			            	}
						}
						
						@Override
						public void onAnimationRepeat(Animation animation) {
						}
						
						@Override
						public void onAnimationEnd(Animation animation) {
							animation.setAnimationListener(null);
						}
					});
					mControlsLayout.startAnimation(animation);
					if (cameraNumber > 1) {
						switchCamera.startAnimation(slideInTopToBottom);
					}
				}
			}
			resetControlsHidingCallBack();
		}		
	}

	public void resetControlsHidingCallBack() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		
		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
			mControlsHandler.postDelayed(mControls = new Runnable() {
				public void run() {
					hideNumpad();
					
					if (isAnimationDisabled) {
						video.setEnabled(true);
						transfer.setVisibility(View.INVISIBLE);
						addCall.setVisibility(View.INVISIBLE);
						mControlsLayout.setVisibility(View.GONE);
						displayOrHideCallsHeader(false);
						switchCamera.setVisibility(View.INVISIBLE);
						numpad.setVisibility(View.GONE);
						options.setImageResource(R.drawable.options_default);
					} else {					
						Animation animation = slideOutTopToBottom;
						animation.setAnimationListener(new AnimationListener() {
							@Override
							public void onAnimationStart(Animation animation) {
								video.setEnabled(false); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
							}
							
							@Override
							public void onAnimationRepeat(Animation animation) {
							}
							
							@Override
							public void onAnimationEnd(Animation animation) {
								video.setEnabled(true); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
								transfer.setVisibility(View.INVISIBLE);
								addCall.setVisibility(View.INVISIBLE);
								mControlsLayout.setVisibility(View.GONE);
								displayOrHideCallsHeader(false);
								switchCamera.setVisibility(View.INVISIBLE);
								numpad.setVisibility(View.GONE);
								options.setImageResource(R.drawable.options_default);
								
								animation.setAnimationListener(null);
							}
						});
						mControlsLayout.startAnimation(animation);
						if (cameraNumber > 1) {
							switchCamera.startAnimation(slideOutBottomToTop);
						}
					}
				}
			}, SECONDS_BEFORE_HIDING_CONTROLS);
		}
	}

	public void setCallControlsVisibleAndRemoveCallbacks() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		
		mControlsLayout.setVisibility(View.VISIBLE);
		switchCamera.setVisibility(View.INVISIBLE);
	}
	
	private void hideNumpad() {
		if (numpad == null || numpad.getVisibility() != View.VISIBLE) {
			return;
		}

		dialer.setImageResource(R.drawable.footer_dialer);
		if (isAnimationDisabled) {
			numpad.setVisibility(View.GONE);
		} else {
			Animation animation = slideOutTopToBottom;
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
					
				}
				
				@Override
				public void onAnimationRepeat(Animation animation) {
					
				}
				
				@Override
				public void onAnimationEnd(Animation animation) {
					numpad.setVisibility(View.GONE);
					animation.setAnimationListener(null);
				}
			});
			numpad.startAnimation(animation);
		}
	}
	
	private void hideOrDisplayNumpad() {
		if (numpad == null) {
			return;
		}
		
		if (numpad.getVisibility() == View.VISIBLE) {
			hideNumpad();
		} else {	
			dialer.setImageResource(R.drawable.dialer_alt_back);
			if (isAnimationDisabled) {
				numpad.setVisibility(View.VISIBLE);
			} else {
				Animation animation = slideInBottomToTop;
				animation.setAnimationListener(new AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {
						
					}
					
					@Override
					public void onAnimationRepeat(Animation animation) {
						
					}
					
					@Override
					public void onAnimationEnd(Animation animation) {
						numpad.setVisibility(View.VISIBLE);
						animation.setAnimationListener(null);
					}
				});
				numpad.startAnimation(animation);
			}
		}
	}
	
	private void hideAnimatedPortraitCallOptions() {
		Animation animation = slideOutLeftToRight;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.INVISIBLE);
				}
				addCall.setVisibility(View.INVISIBLE);
				animation.setAnimationListener(null);
			}
		});
		if (isTransferAllowed) {
			transfer.startAnimation(animation);
		}
		addCall.startAnimation(animation);
	}
	
	private void hideAnimatedLandscapeCallOptions() {
		Animation animation = slideOutTopToBottom;
		if (isTransferAllowed) {
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}
				
				@Override
				public void onAnimationRepeat(Animation animation) {
				}
				
				@Override
				public void onAnimationEnd(Animation animation) {
					transfer.setAnimation(null);
					transfer.setVisibility(View.INVISIBLE);
					animation = AnimationUtils.loadAnimation(InCallActivity.this, R.anim.slide_out_top_to_bottom); // Reload animation to prevent transfer button to blink
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
						}
						
						@Override
						public void onAnimationRepeat(Animation animation) {
						}
						
						@Override
						public void onAnimationEnd(Animation animation) {
							addCall.setVisibility(View.INVISIBLE);
						}
					});
					addCall.startAnimation(animation);
				}
			});
			transfer.startAnimation(animation);
		} else {
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}
				
				@Override
				public void onAnimationRepeat(Animation animation) {
				}
				
				@Override
				public void onAnimationEnd(Animation animation) {
					addCall.setVisibility(View.INVISIBLE);
				}
			});
			addCall.startAnimation(animation);
		}
	}
	
	private void showAnimatedPortraitCallOptions() {
		Animation animation = slideInRightToLeft;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				options.setBackgroundResource(R.drawable.options_default);
				if (isTransferAllowed) {
					transfer.setVisibility(View.VISIBLE);
				}
				addCall.setVisibility(View.VISIBLE);
				animation.setAnimationListener(null);
			}
		});
		if (isTransferAllowed) {
			transfer.startAnimation(animation);
		}
		addCall.startAnimation(animation);
	}
	
	private void showAnimatedLandscapeCallOptions() {
		Animation animation = slideInBottomToTop;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				addCall.setAnimation(null);
				options.setBackgroundResource(R.drawable.options_default);
				addCall.setVisibility(View.VISIBLE);
				if (isTransferAllowed) {
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
						}
						
						@Override
						public void onAnimationRepeat(Animation animation) {
						}
						
						@Override
						public void onAnimationEnd(Animation animation) {
							transfer.setVisibility(View.VISIBLE);
						}
					});
					transfer.startAnimation(animation);
				}
			}
		});
		addCall.startAnimation(animation);
	}
	
	private void hideOrDisplayAudioRoutes()
	{		
		if (routeSpeaker.getVisibility() == View.VISIBLE) {
			routeSpeaker.setVisibility(View.GONE);
			routeBluetooth.setVisibility(View.GONE);
			routeReceiver.setVisibility(View.GONE);
			audioRoute.setSelected(false);
		} else {
			routeSpeaker.setVisibility(View.VISIBLE);
			routeBluetooth.setVisibility(View.VISIBLE);
			routeReceiver.setVisibility(View.VISIBLE);
			audioRoute.setSelected(true);
		}
	}
	
	private void hideOrDisplayCallOptions() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

		//Hide options
		if (addCall.getVisibility() == View.VISIBLE) {
			options.setImageResource(R.drawable.options_default);
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.INVISIBLE);
				}
				addCall.setVisibility(View.INVISIBLE);
				conference.setVisibility(View.INVISIBLE);
			} else {
				if (isOrientationLandscape) {
					hideAnimatedLandscapeCallOptions();
				} else {
					hideAnimatedPortraitCallOptions();
				}
			}
		//Display options
		} else {		
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.VISIBLE);
				}
				addCall.setVisibility(View.VISIBLE);
				if(LinphoneManager.getLc().getCalls().length > 1)
					conference.setVisibility(View.VISIBLE);
				options.setImageResource(R.drawable.options_selected);
			} else {
				if (isOrientationLandscape) {
					showAnimatedLandscapeCallOptions();
				} else {
					showAnimatedPortraitCallOptions();
				}
			}
			transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
		}
	}
	
	public void goBackToDialer() {
		Intent intent = new Intent();
		intent.putExtra("Transfer", false);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}
	
	private void goBackToDialerAndDisplayTransferButton() {
		Intent intent = new Intent();
		intent.putExtra("Transfer", true);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}
	
	public void acceptCallUpdate(boolean accept) {
		if (timer != null) {
			timer.cancel();
		}
		 
		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}
		 
		LinphoneCallParams params = call.getCurrentParamsCopy();
		if (accept) {
			params.setVideoEnabled(true);
			LinphoneManager.getLc().enableVideo(true, true);
		}
		 
		try {
			LinphoneManager.getLc().acceptCallUpdate(call, params);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}
	
	public void startIncomingCallActivity() {
		startActivity(new Intent(this, IncomingCallActivity.class));
	}

	
	
	private void showAcceptCallUpdateDialog() {
        FragmentManager fm = getFragmentManager();
        callUpdateDialog = new AcceptCallUpdateDialogFragment();
        callUpdateDialog.show(fm, "Accept Call Update Dialog");
    }

	@Override
	protected void onResume() {
		instance = this;
		
		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			displayVideoCallControlsIfHidden();
		} else if(LinphoneManager.getLc().isInConference()) {
			displayConference();
		} else {
			LinphoneManager.startProximitySensorForActivity(this);
			setCallControlsVisibleAndRemoveCallbacks();
		}
		
		super.onResume();
		
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		refreshIncallUi();
		handleViewIntent();
	}
	
	private void handleViewIntent() {
		Intent intent = getIntent();
		if(intent != null && intent.getAction() == "android.intent.action.VIEW") {
			LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			if(call != null && isVideoEnabled(call)) {
				LinphonePlayer player = call.getPlayer();
				String path = intent.getData().getPath();
				Log.i("Openning " + path);
				int openRes = player.open(path, new LinphonePlayer.Listener() {
					
					@Override
					public void endOfFile(LinphonePlayer player) {
						player.close();
					}
				});
				if(openRes == -1) {
					String message = "Could not open " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
					return;
				}
				Log.i("Start playing");
				if(player.start() == -1) {
					player.close();
					String message = "Could not start playing " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
	
	@Override
	protected void onPause() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		
		super.onPause();

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;

		if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			LinphoneManager.stopProximitySensorForActivity(this);
		}
	}
	
	@Override
	protected void onDestroy() {
		LinphoneManager.getInstance().changeStatusToOnline();
		
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		mControlsHandler = null;
		
		unbindDrawables(findViewById(R.id.topLayout));
		instance = null;
		super.onDestroy();
	    System.gc();
	}
	
	private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
        	view.getBackground().setCallback(null);
        }
        if (view instanceof ImageView) {
        	view.setOnClickListener(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            	unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
 		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
 		return super.onKeyDown(keyCode, event);
 	}

	public void bindAudioFragment(AudioCallFragment fragment) {
		audioCallFragment = fragment;
	}

	public void bindVideoFragment(VideoCallFragment fragment) {
		videoCallFragment = fragment;
	}

	private void displayActiveCall(LinphoneCall call){
		if(isVideoEnabled(call)){
			mActiveCallHeader.setVisibility(View.GONE);
		} else {
			mActiveCallHeader.setVisibility(View.VISIBLE);
			mNoCurrentCall.setVisibility(View.GONE);
		}

		if(call == null) return;
		String sipUri = call.getRemoteAddress().asStringUriOnly();
		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		} catch (LinphoneCoreException e) {
			Log.e("Incall activity cannot parse remote address",e);
			lAddress= LinphoneCoreFactory.instance().createLinphoneAddress("unknown","unknown","unknown");
		}

		TextView contact = (TextView) findViewById(R.id.contactNameOrNumber);

		Contact lContact  = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), lAddress);
		if (lContact == null) {
			if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
				contact.setText(lAddress.getUserName());
			} else {
				contact.setText(sipUri);
			}
		} else {
			contact.setText(lContact.getName());
		}

		int callDuration = call.getDuration();
		if (callDuration == 0 && call.getState() != State.StreamsRunning) {
			return;
		}

		Chronometer timer = (Chronometer) findViewById(R.id.callTimer);
		if (timer == null) {
			throw new IllegalArgumentException("no callee_duration view found");
		}

		timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
		timer.start();
	}
	
	private void displayOtherCalls(Resources resources, final LinphoneCall call, int index) {
		String sipUri = call.getRemoteAddress().asStringUriOnly();
        LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		} catch (LinphoneCoreException e) {
			Log.e("Incall activity cannot parse remote address",e);
			lAddress= LinphoneCoreFactory.instance().createLinphoneAddress("uknown","unknown","unkonown");
		}

        // Control Row
		LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.active_call_control_row, container, false);
    	callView.setId(index+1);

		TextView contact = (TextView) callView.findViewById(R.id.contactNameOrNumber);

		Contact lContact  = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), lAddress);
		if (lContact == null) {
			if (getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
				contact.setText(lAddress.getUserName());
			} else {
				contact.setText(sipUri);
			}
		} else {
			contact.setText(lContact.getName());
		}

		displayCallStatusIconAndReturnCallPaused(callView, call);
		registerCallDurationTimer(callView, call);
    	callsList.addView(callView);
    	
		// Image Row
        //Contact contact  = ContactsManager.getInstance().findContactWithAddress(imageView.getContext().getContentResolver(), lAddress);
		/*if(contact != null) {
			displayOrHideContactPicture(imageView, contact.getPhotoUri(), contact.getThumbnailUri(), false);
		} else {
			displayOrHideContactPicture(imageView, null, null, false);
		}
    	callsList.addView(imageView);*/


    //	callView.setTag(imageView);
    /*	callView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (v.getTag() != null) {
					View imageView = (View) v.getTag();
					if (imageView.getVisibility() == View.VISIBLE)
						imageView.setVisibility(View.GONE);
					else
						imageView.setVisibility(View.VISIBLE);
					callsList.invalidate();
				}
			}
		});*/
	}
	
	private void setContactName(LinearLayout callView, LinphoneAddress lAddress, String sipUri, Resources resources) {
		TextView contact = (TextView) callView.findViewById(R.id.contactNameOrNumber);

		Contact lContact  = ContactsManager.getInstance().findContactWithAddress(callView.getContext().getContentResolver(), lAddress);
		if (lContact == null) {
	        if (resources.getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
	        	contact.setText(lAddress.getUserName());
			} else {
				contact.setText(sipUri);
			}
		} else {
			contact.setText(lContact.getName());
		}
	}
	
	private boolean displayCallStatusIconAndReturnCallPaused(LinearLayout callView, LinphoneCall call) {
		boolean isCallPaused, isInConference;
		ImageView callState = (ImageView) callView.findViewById(R.id.callStatus);
		callState.setTag(call);
		callState.setOnClickListener(this);
		
		if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
			callState.setImageResource(R.drawable.pause);
			isCallPaused = true;
			isInConference = false;
		} else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
			//callState.setImageResource(R.drawable.call_state_ringing_default);
			isCallPaused = false;
			isInConference = false;
		} else {
			if (isConferenceRunning && call.isInConference()) {
				//callState.setImageResource(R.drawable.remove);
				isInConference = true;
			} else {
				//callState.setImageResource(R.drawable.play);
				isInConference = false;
			}
			isCallPaused = false;
		}
		
		return isCallPaused || isInConference;
	}
	
	private void displayOrHideContactPicture(LinearLayout callView, Uri pictureUri, Uri thumbnailUri, boolean hide) {
		/*ImageView contactPicture = (ImageView) callView.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(callView.getContext(), contactPicture, Uri.parse(pictureUri.toString()), thumbnailUri, R.drawable.unknown_small);
        }
		callView.setVisibility(hide ? View.GONE : View.VISIBLE);*/
	}
	
	private void registerCallDurationTimer(View v, LinphoneCall call) {
		int callDuration = call.getDuration();
		if (callDuration == 0 && call.getState() != State.StreamsRunning) {
			return;
		}

		Chronometer timer = (Chronometer) v.findViewById(R.id.callTimer);
		if (timer == null) {
			throw new IllegalArgumentException("no callee_duration view found");
		}

		timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
		timer.start();
	}
	
	public void refreshCallList(Resources resources) {
		isConferenceRunning = LinphoneManager.getLc().isInConference();
		if (isConferenceRunning) {
			displayConference();
			mNoCurrentCall.setVisibility(View.GONE);
		} else {
			conferenceList.setVisibility(View.GONE);
		}

		if(callsList != null) {
			callsList.setVisibility(View.VISIBLE);
			callsList.removeAllViews();
			int index = 0;

			if (LinphoneManager.getLc().getCallsNb() == 0) {
				goBackToDialer();
				return;
			}

			for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
				if(call.isInConference()) break;
				if (call != LinphoneManager.getLc().getCurrentCall()) {
					displayOtherCalls(resources, call, index);
					index++;
				} else {
					displayActiveCall(call);
				}
			}

			if (LinphoneManager.getLc().getCurrentCall() == null && !isConferenceRunning ) {
				showAudioView();
				mActiveCallHeader.setVisibility(View.GONE);
				mNoCurrentCall.setVisibility(View.VISIBLE);
				video.setEnabled(false);
			}

			//callsList.invalidate();
		}

	}

	//Conference
	private void exitConference(final LinphoneCall call){
		LinphoneCore lc = LinphoneManager.getLc();

		if (call.isInConference()) {
			lc.removeFromConference(call);
			if (lc.getConferenceSize() <= 1) {
				lc.leaveConference();
			}
		}
		refreshIncallUi();
	}

	private void enterConference() {
		LinphoneManager.getLc().addAllToConference();
		displayConferenceHeader();
		mNoCurrentCall.setVisibility(View.GONE);
		displayOrHideCallsHeader(false);
	}

	public void pauseOrResumeConference() {
		LinphoneCore lc = LinphoneManager.getLc();
		if (lc.isInConference()) {
			conferenceStatus.setImageResource(R.drawable.pause_big_over_selected);
			lc.leaveConference();
		} else {
			conferenceStatus.setImageResource(R.drawable.pause_big_default);
			lc.enterConference();
		}
	}

	private void displayConferenceParticipant(int index, final LinphoneCall call){
		LinearLayout confView = (LinearLayout) inflater.inflate(R.layout.conf_call_control_row, container, false);
		conferenceList.setId(index+1);
		TextView contact = (TextView) confView.findViewById(R.id.contactNameOrNumber);

		Contact lContact  = ContactsManager.getInstance().findContactWithAddress(getContentResolver(),call.getRemoteAddress());
		if (lContact == null) {
			contact.setText(call.getRemoteAddress().getUserName());
		} else {
			contact.setText(lContact.getName());
		}

		registerCallDurationTimer(confView, call);

		ImageView quitConference = (ImageView) confView.findViewById(R.id.quitConference);
		quitConference.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				exitConference(call);
			}
		});
		conferenceList.addView(confView);

	}

	private void displayConferenceHeader(){
		conferenceList.setVisibility(View.VISIBLE);
		RelativeLayout headerConference = (RelativeLayout) inflater.inflate(R.layout.conference_header, container, false);
		conferenceStatus = (ImageView) headerConference.findViewById(R.id.conferenceStatus);
		conferenceStatus.setOnClickListener(this);
		conferenceList.addView(headerConference);

	}

	private void displayConference(){
		mControlsLayout.setVisibility(View.VISIBLE);
		LinphoneManager.startProximitySensorForActivity(InCallActivity.this);
		mActiveCallHeader.setVisibility(View.GONE);
		mNoCurrentCall.setVisibility(View.GONE);
		callsList.setVisibility(View.VISIBLE);
		conferenceList.removeAllViews();

		//Conference Header
		displayConferenceHeader();

		//Conference participant
		int index = 1;
		for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
			if(call.isInConference()) {
				displayConferenceParticipant(index,call);
				index++;
			}
		}

		conferenceList.setVisibility(View.VISIBLE);
	}
}
