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
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.ui.Numpad;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class InCallActivity extends FragmentActivity implements
									LinphoneOnCallStateChangedListener,
									LinphoneOnCallEncryptionChangedListener,
									OnClickListener {
	private final static int SECONDS_BEFORE_HIDING_CONTROLS = 3000;
	private final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;
	
	private static InCallActivity instance;
	
	private Handler mHandler = new Handler();
	private Handler mControlsHandler = new Handler();
	private Runnable mControls;
	private ImageView pause, hangUp, dialer, switchCamera, conference;
	private TextView video, micro, speaker, options, addCall, transfer;
	private StatusFragment status;
	private AudioCallFragment audioCallFragment;
	private VideoCallFragment videoCallFragment;
	private boolean isSpeakerEnabled = false, isMicMuted = false, isVideoEnabled, isTransferAllowed, isAnimationDisabled;
	private ViewGroup mControlsLayout;
	private Numpad numpad;
	private int cameraNumber;
	private Animation slideOutLeftToRight, slideInRightToLeft, slideInBottomToTop, slideInTopToBottom, slideOutBottomToTop, slideOutTopToBottom;
	private CountDownTimer timer;
	private AcceptCallUpdateDialog callUpdateDialog;
	
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
        
        isVideoEnabled = getIntent().getExtras() != null && getIntent().getExtras().getBoolean("VideoEnabled");
        isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isAnimationDisabled = getApplicationContext().getResources().getBoolean(R.bool.disable_animations) || !prefs.getBoolean(getString(R.string.pref_animation_enable_key), false);
        cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;
        
        if (findViewById(R.id.fragmentContainer) != null) {
            initUI();
            
            if (LinphoneManager.getLc().getCallsNb() > 0) {
            	LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

            	if (LinphoneUtils.isCallEstablished(call)) {
	    			isVideoEnabled = call.getCurrentParamsCopy().getVideoEnabled();
	    			enableAndRefreshInCallActions();
            	}
            }
            
            if (savedInstanceState != null) { 
            	// Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
            	return;
            }
            
            Fragment callFragment;
            if (isVideoEnabled) {
            	callFragment = new VideoCallFragment();
            	videoCallFragment = (VideoCallFragment) callFragment;
            	
            	if (cameraNumber > 1) {
            		switchCamera.setVisibility(View.VISIBLE); 
            	}
            } else {
            	callFragment = new AudioCallFragment();
            	audioCallFragment = (AudioCallFragment) callFragment;
        		switchCamera.setVisibility(View.INVISIBLE);
            }
            callFragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();
        }
	}
	
	private void initUI() {
		video = (TextView) findViewById(R.id.video);
		video.setOnClickListener(this);
		video.setEnabled(false);
		micro = (TextView) findViewById(R.id.micro);
		micro.setOnClickListener(this);
		micro.setEnabled(false);
		speaker = (TextView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);
		speaker.setEnabled(false);
		addCall = (TextView) findViewById(R.id.addCall);
		addCall.setOnClickListener(this);
		addCall.setEnabled(false);
		transfer = (TextView) findViewById(R.id.transfer);
		transfer.setOnClickListener(this);
		transfer.setEnabled(false);
		options = (TextView) findViewById(R.id.options);
		options.setOnClickListener(this);
		options.setEnabled(false);
		pause = (ImageView) findViewById(R.id.pause);
		pause.setOnClickListener(this);
		pause.setEnabled(false);
		hangUp = (ImageView) findViewById(R.id.hangUp);
		hangUp.setOnClickListener(this);
		conference = (ImageView) findViewById(R.id.conference);
		conference.setOnClickListener(this);
		dialer = (ImageView) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);
		dialer.setEnabled(false);
		numpad = (Numpad) findViewById(R.id.numpad);
		
		switchCamera = (ImageView) findViewById(R.id.switchCamera);
		switchCamera.setOnClickListener(this);
		
		mControlsLayout = (ViewGroup) findViewById(R.id.menu);
		
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
	}
	
	private void refreshInCallActions() {
		if (mHandler == null) {
			mHandler = new Handler();
		}
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (!isVideoActivatedInSettings()) {
					video.setEnabled(false);
				} else {
					if (isVideoEnabled) {
			        	video.setBackgroundResource(R.drawable.video_on);
					} else {
						video.setBackgroundResource(R.drawable.video_off);
					}
				}
				
				if (isSpeakerEnabled) {
					speaker.setBackgroundResource(R.drawable.speaker_on);
				} else {
					speaker.setBackgroundResource(R.drawable.speaker_off);
				}
				
				if (isMicMuted) {
					micro.setBackgroundResource(R.drawable.micro_off);
				} else {
					micro.setBackgroundResource(R.drawable.micro_on);
				}
				
				if (LinphoneManager.getLc().getCallsNb() > 1) {
					conference.setVisibility(View.VISIBLE);
					pause.setVisibility(View.GONE);
				} else {
					conference.setVisibility(View.GONE);
					pause.setVisibility(View.VISIBLE);
					
					List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.Paused));
					if (pausedCalls.size() == 1) {
						pause.setImageResource(R.drawable.pause_on);
					} else {
						pause.setImageResource(R.drawable.pause_off);
					}
				}
			}
		});
	}
	
	private void enableAndRefreshInCallActions() {
		if (mHandler == null) {
			mHandler = new Handler();
		}
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				options.setEnabled(true);
				video.setEnabled(true);
				micro.setEnabled(true);
				speaker.setEnabled(true);
				addCall.setEnabled(true);
				transfer.setEnabled(true);
				pause.setEnabled(true);
				dialer.setEnabled(true);
				conference.setEnabled(true);

				refreshInCallActions();
			}
		});
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}
	
	private boolean isVideoActivatedInSettings() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean settingsVideoEnabled = prefs.getBoolean(getString(R.string.pref_video_enable_key), false);
		return settingsVideoEnabled;
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
			goBackToDialer();
		} 
		else if (id == R.id.pause) {
			pauseOrResumeCall();
		} 
		else if (id == R.id.hangUp) {
			hangUp();
		} 
		else if (id == R.id.dialer) {
			hideOrDisplayNumpad();
		}
		else if (id == R.id.conference) {
			enterConference();
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
					video.setBackgroundResource(R.drawable.video_on);
					
					replaceFragmentVideoByAudio();
					setCallControlsVisibleAndRemoveCallbacks();
				} else {
					LinphoneManager.getInstance().addVideo();
					isSpeakerEnabled = true;
					LinphoneManager.getInstance().routeAudioToSpeaker();
					speaker.setBackgroundResource(R.drawable.speaker_on);
					video.setBackgroundResource(R.drawable.video_off);
					
					replaceFragmentAudioByVideo();
					displayVideoCallControlsIfHidden();
				}
			}
		});
	}
	
	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new AudioCallFragment();
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
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
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}
	
	private void toogleMicro() {
		LinphoneCore lc = LinphoneManager.getLc();
		isMicMuted = !isMicMuted;
		lc.muteMic(isMicMuted);
		if (isMicMuted) {
			micro.setBackgroundResource(R.drawable.micro_off);
		} else {
			micro.setBackgroundResource(R.drawable.micro_on);
		}
	}
	
	private void toogleSpeaker() {
		isSpeakerEnabled = !isSpeakerEnabled;
		if (isSpeakerEnabled) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			speaker.setBackgroundResource(R.drawable.speaker_on);
		} else {
			LinphoneManager.getInstance().routeAudioToReceiver();
			speaker.setBackgroundResource(R.drawable.speaker_off);
		}
		LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
	}
	
	private void pauseOrResumeCall() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCall call = lc.getCurrentCall();
		pauseOrResumeCall(call, false);
	}
	
	public void pauseOrResumeCall(LinphoneCall call, boolean leaveConference) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (call != null && LinphoneUtils.isCallRunning(call)) {
			if (call.isInConference()) {
				lc.removeFromConference(call);
				if (lc.getConferenceSize() <= 1) {
					lc.leaveConference();
				}
			} else {
				lc.pauseCall(call);
				pause.setImageResource(R.drawable.pause_on);
			}
		} else {
			List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(lc, Arrays.asList(State.Paused));
			if (pausedCalls.size() == 1) {
				LinphoneCall callToResume = pausedCalls.get(0);
				if ((call != null && callToResume.equals(call)) || call == null) {
					lc.resumeCall(callToResume);
					pause.setImageResource(R.drawable.pause_off);
				}
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
	
	private void enterConference() {
		LinphoneManager.getLc().addAllToConference();
	}
	
	public void pauseOrResumeConference() {
		LinphoneCore lc = LinphoneManager.getLc();
		if (lc.isInConference()) {
			lc.leaveConference();
		} else {
			lc.enterConference();
		}
	}
	
	public void displayVideoCallControlsIfHidden() {
		if (mControlsLayout != null) {
			if (mControlsLayout.getVisibility() != View.VISIBLE) {
				if (isAnimationDisabled) {
					mControlsLayout.setVisibility(View.VISIBLE);
					if (cameraNumber > 1) {
	            		switchCamera.setVisibility(View.VISIBLE); 
	            	}
				} else {
					Animation animation = slideInBottomToTop;
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
							mControlsLayout.setVisibility(View.VISIBLE);
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
		
		if (isVideoEnabled) {
			mControlsHandler.postDelayed(mControls = new Runnable() {
				public void run() {
					hideNumpad();
					
					if (isAnimationDisabled) {
						transfer.setVisibility(View.INVISIBLE);
						addCall.setVisibility(View.INVISIBLE);
						mControlsLayout.setVisibility(View.GONE);
						switchCamera.setVisibility(View.INVISIBLE);
						numpad.setVisibility(View.GONE);
						options.setBackgroundResource(R.drawable.options);
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
								switchCamera.setVisibility(View.INVISIBLE);
								numpad.setVisibility(View.GONE);
								options.setBackgroundResource(R.drawable.options);
								
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
			
		dialer.setImageResource(R.drawable.dialer_alt);
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
				options.setBackgroundResource(R.drawable.options_alt);
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
				options.setBackgroundResource(R.drawable.options_alt);
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
	
	private void hideOrDisplayCallOptions() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		
		if (addCall.getVisibility() == View.VISIBLE) {
			options.setBackgroundResource(R.drawable.options);
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.INVISIBLE);
				}
				addCall.setVisibility(View.INVISIBLE);
			} else {
				if (isOrientationLandscape) {
					hideAnimatedLandscapeCallOptions();
				} else {
					hideAnimatedPortraitCallOptions();
				}
			}
		} else {		
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.VISIBLE);
				}
				addCall.setVisibility(View.VISIBLE);
				options.setBackgroundResource(R.drawable.options_alt);
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
	
	private void acceptCallUpdate(boolean accept) {
		if (timer != null) {
			timer.cancel();
		}
		
		if (callUpdateDialog != null) {
			callUpdateDialog.dismissAllowingStateLoss();
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
		 
		if (accept) {
			isSpeakerEnabled = true;
			LinphoneManager.getInstance().routeAudioToSpeaker();
			speaker.setBackgroundResource(R.drawable.speaker_on);
			
			replaceFragmentAudioByVideo();
			video.setBackgroundResource(R.drawable.video_off);
			displayVideoCallControlsIfHidden();
		}
	}

	@Override
	public void onCallStateChanged(final LinphoneCall call, State state, String message) {		
		if (LinphoneManager.getLc().getCallsNb() == 0) {
			finish();
			return;
		}
		
		if (state == State.StreamsRunning) {
			boolean isVideoEnabledInCall = call.getCurrentParamsCopy().getVideoEnabled();
			if (isVideoEnabledInCall != isVideoEnabled) {
				isVideoEnabled = isVideoEnabledInCall;
				switchVideo(isVideoEnabled);
			} else {
				//Hack to fix Galaxy S issue
				if (Hacks.needGalaxySAudioHack()) {
					if (isSpeakerEnabled) {
						LinphoneManager.getInstance().routeAudioToSpeaker();
					} else {
						LinphoneManager.getInstance().routeAudioToReceiver();
					}
					LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
				}
			}
			
			isMicMuted = LinphoneManager.getLc().isMicMuted();
			enableAndRefreshInCallActions();
			
			if (status != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						status.refreshStatusItems(call, isVideoEnabled);
					}
				});
			}
		}
		refreshInCallActions();
		
		if (audioCallFragment != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					audioCallFragment.refreshCallList(getResources());
				}
			});
		}
		
		if (state == State.CallUpdatedByRemote) {
			// If the correspondent proposes video while audio call
			boolean isVideoEnabled = LinphoneManager.getInstance().isVideoEnabled();
			if (!isVideoEnabled) {
				acceptCallUpdate(false);
				return;
			}
			
			boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
			boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
			boolean autoAcceptCameraPolicy = LinphoneManager.getInstance().isAutoAcceptCamera();
			if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
				mHandler.post(new Runnable() {
					public void run() {
						showAcceptCallUpdateDialog();
						
						timer = new CountDownTimer(SECONDS_BEFORE_DENYING_CALL_UPDATE, 1000) {
							public void onTick(long millisUntilFinished) { }
							public void onFinish() {
								acceptCallUpdate(false);
					    	}
						}.start();
					}
				});
			} 
//			else if (remoteVideo && !LinphoneManager.getLc().isInConference() && autoAcceptCameraPolicy) {
//				mHandler.post(new Runnable() {
//					@Override
//					public void run() {
//						acceptCallUpdate(true);
//					}
//				});
//			}
		}
		
		transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
	}
	
	private void showAcceptCallUpdateDialog() {
        FragmentManager fm = getSupportFragmentManager();
        callUpdateDialog = new AcceptCallUpdateDialog();
        callUpdateDialog.show(fm, "Accept Call Update Dialog");
    }

	@Override
	public void onCallEncryptionChanged(final LinphoneCall call, boolean encrypted, String authenticationToken) {
		if (status != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					status.refreshStatusItems(call, call.getCurrentParamsCopy().getVideoEnabled());
				}
			});
		}
	}
	
	@Override
	protected void onResume() {
		instance = this;
		LinphoneManager.addListener(this);
		LinphoneManager.startProximitySensorForActivity(this);
		
		if (isVideoEnabled) {
			displayVideoCallControlsIfHidden();
		} else {
			setCallControlsVisibleAndRemoveCallbacks();
		}
		
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		
		LinphoneManager.stopProximitySensorForActivity(this);
		LinphoneManager.removeListener(this);
	}
	
	@Override
	protected void onDestroy() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		mControlsHandler = null;
		mHandler = null;
		
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
// 		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
 		return super.onKeyDown(keyCode, event);
 	}

	public void bindAudioFragment(AudioCallFragment fragment) {
		audioCallFragment = fragment;
	}

	public void bindVideoFragment(VideoCallFragment fragment) {
		videoCallFragment = fragment;
	}
	
	class AcceptCallUpdateDialog extends DialogFragment {

	    public AcceptCallUpdateDialog() {
	        // Empty constructor required for DialogFragment
	    }

	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	        View view = inflater.inflate(R.layout.accept_call_update_dialog, container);

	        getDialog().setTitle(R.string.call_update_title);
	        
	        Button yes = (Button) view.findViewById(R.id.yes);
	        yes.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
			    	Log.d("Call Update Accepted");
			    	acceptCallUpdate(true);
				}
			});
	        
	        Button no = (Button) view.findViewById(R.id.no);
	        no.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
			    	Log.d("Call Update Denied");
			    	acceptCallUpdate(false);
				}
			});
	        
	        return view;
	    }
	}
}
