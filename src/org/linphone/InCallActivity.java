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

import org.linphone.LinphoneSimpleListener.LinphoneOnCallEncryptionChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;

//FIXME : Speaker
//FIXME : Chronometer for calls
/**
 * @author Sylvain Berfini
 */
public class InCallActivity extends FragmentActivity implements
									LinphoneOnCallStateChangedListener,
									LinphoneOnCallEncryptionChangedListener,
									OnClickListener {
	private final static int SECONDS_BEFORE_HIDING_CONTROLS = 3000;
	
	private static InCallActivity instance;
	private Handler mHandler = new Handler();
	private Handler controlsHandler = new Handler();
	private Runnable mControls;
	private ImageView video, micro, speaker, addCall, pause, hangUp, dialer, switchCamera;
	private StatusFragment status;
	private AudioCallFragment audioCallFragment;
	private VideoCallFragment videoCallFragment;
	private boolean isSpeakerEnabled, isMicMuted, isVideoEnabled;
	private LinearLayout mControlsLayout;
	
	static final boolean isInstanciated() {
		return instance != null;
	}
	
	public static final InCallActivity instance() {
		return instance;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.incall);
        instance = this;      
        
        if (findViewById(R.id.fragmentContainer) != null) {
            if (savedInstanceState != null) {
                return;
            }

            isSpeakerEnabled = LinphoneManager.getInstance().isSpeakerOn();
            isMicMuted = LinphoneManager.getLc().isMicMuted();
            isVideoEnabled = getIntent().getBooleanExtra("VideoEnabled", false);
            initUI();
            
            Fragment callFragment;            
            if (isVideoEnabled) {
            	callFragment = new VideoCallFragment();
            	videoCallFragment = (VideoCallFragment) callFragment;
        		switchCamera.setVisibility(View.VISIBLE);
            } else {
            	callFragment = new AudioCallFragment();
            	audioCallFragment = (AudioCallFragment) callFragment;
        		switchCamera.setVisibility(View.GONE);
            }
            callFragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commit();
        }
		
        LinphoneManager.addListener(this);
	}
	
	private void initUI() {
		video = (ImageView) findViewById(R.id.video);
		video.setOnClickListener(this);
		micro = (ImageView) findViewById(R.id.micro);
		micro.setOnClickListener(this);
		speaker = (ImageView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);
		addCall = (ImageView) findViewById(R.id.addCall);
		addCall.setOnClickListener(this);
		pause = (ImageView) findViewById(R.id.pause);
		pause.setOnClickListener(this);
		hangUp = (ImageView) findViewById(R.id.hangUp);
		hangUp.setOnClickListener(this);
		dialer = (ImageView) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);
		
        switchCamera = (ImageView) findViewById(R.id.switchCamera);
        switchCamera.setOnClickListener(this);
		
		mControlsLayout = (LinearLayout) findViewById(R.id.menu);
		
		if (isVideoEnabled) {
        	video.setImageResource(R.drawable.video_off);
		}
		if (isSpeakerEnabled) {
			speaker.setImageResource(R.drawable.speaker_off_over);
		}
		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_on);
		}
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (isVideoEnabled) {
			displayVideoCallControlsIfHidden();
		}
		
		if (id == R.id.video) {
			isVideoEnabled = !isVideoEnabled;
			switchVideo(isVideoEnabled);
		} 
		else if (id == R.id.micro) {
			toogleMicro();
		} 
		else if (id == R.id.speaker) {
			toogleSpeaker();
		} 
		else if (id == R.id.addCall) {
			setResult(Activity.RESULT_FIRST_USER);
			finish();
		} 
		else if (id == R.id.pause) {
			pause();
		} 
		else if (id == R.id.hangUp) {
			hangUp();
		} 
		else if (id == R.id.dialer) {
			
		}
		else if (id == R.id.switchCamera) {
			if (videoCallFragment != null) {
				videoCallFragment.switchCamera();
			}
		}
	}

	
	private void switchVideo(final boolean displayVideo) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (!displayVideo) {
					LinphoneCallParams params = call.getCurrentParamsCopy();
					params.setVideoEnabled(false);
					LinphoneManager.getLc().updateCall(call, params);
					replaceFragmentVideoByAudio();
					
					video.setImageResource(R.drawable.video_on);
					setCallControlsVisibleAndRemoveCallbacks();
					
				} else {
					if (!call.getCurrentParamsCopy().getVideoEnabled()) {
						LinphoneManager.getInstance().addVideo();
					}
					
					replaceFragmentAudioByVideo();
					video.setImageResource(R.drawable.video_off);
					displayVideoCallControlsIfHidden();
				}
			}
		});
	}
	
	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new AudioCallFragment();
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, audioCallFragment);
		transaction.commit();
	}
	
	private void replaceFragmentAudioByVideo() {
		switchCamera.setVisibility(View.VISIBLE);
		videoCallFragment = new VideoCallFragment();
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		transaction.commitAllowingStateLoss();
	}
	
	private void toogleMicro() {
		LinphoneCore lc = LinphoneManager.getLc();
		isMicMuted = !isMicMuted;
		lc.muteMic(isMicMuted);
		if (isMicMuted) {
			micro.setImageResource(R.drawable.micro_on);
		} else {
			micro.setImageResource(R.drawable.micro_off);
		}
	}
	
	private void toogleSpeaker() {
		isSpeakerEnabled = !isSpeakerEnabled;
		if (isSpeakerEnabled) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			speaker.setImageResource(R.drawable.speaker_off);
		} else {
			LinphoneManager.getInstance().routeAudioToReceiver();
			speaker.setImageResource(R.drawable.speaker_on);
		}
	}
	
	private void pause() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCall call = lc.getCurrentCall();
		if (call != null && isCallRunning(call)) {
			lc.pauseCall(call);
			pause.setImageResource(R.drawable.pause_on);
		} else {
			List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(lc, Arrays.asList(State.Paused));
			if (pausedCalls.size() == 1) {
				LinphoneCall callToResume = pausedCalls.get(0);
				lc.resumeCall(callToResume);
				pause.setImageResource(R.drawable.pause_off);
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
			if (mControlsLayout.getVisibility() == View.GONE) {
				if (InCallActivity.this.getResources().getBoolean(R.bool.disable_animations)) {
					mControlsLayout.setVisibility(View.VISIBLE);
					switchCamera.setVisibility(View.VISIBLE);
				} else {
					Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom_to_top);
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
							mControlsLayout.setVisibility(View.VISIBLE);
							switchCamera.setVisibility(View.VISIBLE);
						}
						
						@Override
						public void onAnimationRepeat(Animation animation) {
						}
						
						@Override
						public void onAnimationEnd(Animation animation) {
						}
					});
					mControlsLayout.startAnimation(animation);
					switchCamera.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_top_to_bottom));
				}
			}
			
			if (mControls != null) {
				controlsHandler.removeCallbacks(mControls);
			}
			
			controlsHandler.postDelayed(mControls = new Runnable() {
				public void run() {
					if (InCallActivity.this.getResources().getBoolean(R.bool.disable_animations)) {
						mControlsLayout.setVisibility(View.GONE);
						switchCamera.setVisibility(View.GONE);
					} else {					
						Animation animation = AnimationUtils.loadAnimation(InCallActivity.this, R.anim.slide_out_top_to_bottom);
						animation.setAnimationListener(new AnimationListener() {
							@Override
							public void onAnimationStart(Animation animation) {
							}
							
							@Override
							public void onAnimationRepeat(Animation animation) {
							}
							
							@Override
							public void onAnimationEnd(Animation animation) {
								mControlsLayout.setVisibility(View.GONE);
								switchCamera.setVisibility(View.GONE);
							}
						});
						mControlsLayout.startAnimation(animation);
						switchCamera.startAnimation(AnimationUtils.loadAnimation(InCallActivity.this, R.anim.slide_out_bottom_to_top));
					}
				}
			}, SECONDS_BEFORE_HIDING_CONTROLS);
		}		
	}

	public void setCallControlsVisibleAndRemoveCallbacks() {
		if (controlsHandler != null && mControls != null) {
			controlsHandler.removeCallbacks(mControls);
			mControls = null;
		}
		
		mControlsLayout.setVisibility(View.VISIBLE);
		switchCamera.setVisibility(View.GONE);
	}
	
	private boolean isCallRunning(LinphoneCall call)
	{
		LinphoneCall.State state = call.getState();
		
		return state == LinphoneCall.State.Connected ||
				state == LinphoneCall.State.CallUpdated ||
				state == LinphoneCall.State.CallUpdatedByRemote ||
				state == LinphoneCall.State.StreamsRunning ||
				state == LinphoneCall.State.Resuming;
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, State state,
			String message) {
		if (LinphoneManager.getLc().getCallsNb() == 0) {
			finish();
		}
		
		if (state == State.StreamsRunning) {
			boolean isVideoEnabledInCall = call.getCurrentParamsCopy().getVideoEnabled();
			if (isVideoEnabledInCall != isVideoEnabled) {
				isVideoEnabled = isVideoEnabledInCall;
				switchVideo(isVideoEnabled);
			}
		}
	}

	@Override
	public void onCallEncryptionChanged(LinphoneCall call, boolean encrypted,
			String authenticationToken) {
		if (status != null) {
			status.refreshEncryptionIcon();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (isVideoEnabled) {
			displayVideoCallControlsIfHidden();
		} else {
			setCallControlsVisibleAndRemoveCallbacks();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		setCallControlsVisibleAndRemoveCallbacks();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
 		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
 		return super.onKeyDown(keyCode, event);
 	}
}
