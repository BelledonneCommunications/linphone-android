package org.linphone;
/*
AudioCallFragment.java
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
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.ui.AvatarWithShadow;

import android.app.Activity;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

/**
 * @author Sylvain Berfini
 */
public class AudioCallFragment extends Fragment implements OnClickListener {
	private static final int rowHeight = 75; // Value set in active_call.xml
	private static final int rowImageHeight = 75; // Value set in avatar.xml
	private static final int botMarginIfImage = 25;
	private static final int rowThickRatio = 85; // Ratio dependent from the image
	private static final int topMargin = (int) ((rowHeight * rowThickRatio) / 100);
	private static final int conferenceMargin = 20;
	private static final int topMarginWithImage = topMargin + rowImageHeight + botMarginIfImage;
	
    private static final int FLIPPER_AVATAR_VIEW = 0;
    private static final int FLIPPER_AUDIO_STATS_VIEW = 1;
	
    private Handler mHandler = new Handler();
	private RelativeLayout callsList;
	private LayoutInflater inflater;
	private ViewGroup container;
	private boolean previousCallIsActive = false;
	private boolean isConferenceRunning = false;
	
	private InCallActivity incallActvityInstance;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		
		this.inflater = inflater;
		this.container = container;
		
        View view = inflater.inflate(R.layout.audio, container, false);
        callsList = (RelativeLayout) view.findViewById(R.id.calls);
        
        return view;
    }
	
	private void displayConferenceHeader() {
		LinearLayout conferenceHeader = (LinearLayout) inflater.inflate(R.layout.conference_header, container, false);
		
		ImageView conferenceState = (ImageView) conferenceHeader.findViewById(R.id.conferenceStatus);
		conferenceState.setOnClickListener(this);
		if (LinphoneManager.getLc().isInConference()) {
			conferenceState.setImageResource(R.drawable.play);
		} else {
			conferenceState.setImageResource(R.drawable.pause);
		}
		
		callsList.addView(conferenceHeader);
	}
	
	private void displayCall(Resources resources, LinearLayout callView, LinphoneCall call, int index) {
		String sipUri = call.getRemoteAddress().asStringUriOnly();
        LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
        Uri pictureUri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, callView.getContext().getContentResolver());

		setContactName(callView, lAddress, sipUri, resources);
		boolean hide = displayCallStatusIconAndReturnCallPaused(callView, call);
		displayOrHideContactPictureAndStats(callView, pictureUri, call, hide);
		setRowBackgroundAndPadding(callView, resources, index, call, !hide);
		registerCallDurationTimer(callView, call);
		previousCallIsActive = !hide;

    	callsList.addView(callView);
	}
	
	private void setContactName(LinearLayout callView, LinphoneAddress lAddress, String sipUri, Resources resources) {
		TextView contact = (TextView) callView.findViewById(R.id.contactNameOrNumber);
		if (lAddress.getDisplayName() == null) {
	        if (resources.getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
	        	contact.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
			} else {
				contact.setText(sipUri);
			}
		} else {
			contact.setText(lAddress.getDisplayName());
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
			callState.setImageResource(R.drawable.call_state_ringing_default);
			isCallPaused = false;
			isInConference = false;
		} else {
			if (isConferenceRunning && call.isInConference()) {
				callState.setImageResource(R.drawable.remove);
				isInConference = true;
			} else {
				callState.setImageResource(R.drawable.play);
				isInConference = false;
			}
			isCallPaused = false;
		}
		
		return isCallPaused || isInConference;
	}
	
	private void displayOrHideContactPictureAndStats(LinearLayout callView, Uri pictureUri, LinphoneCall call, boolean hide) {
		ViewFlipper flipper = (ViewFlipper) callView.findViewById(R.id.flipper);
		flipper.setDisplayedChild(FLIPPER_AVATAR_VIEW);
		
		AvatarWithShadow contactPicture = (AvatarWithShadow) callView.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(callView.getContext(), contactPicture.getView(), Uri.parse(pictureUri.toString()), R.drawable.unknown_small);
        }
		if (hide) {
			flipper.setVisibility(View.GONE);
		}

		if (getActivity().getResources().getBoolean(R.bool.display_call_stats)) {
			View audioCallstats = callView.findViewById(R.id.audioCallStats);
			if (call != null) {
				flipper.setEnabled(true);
				initAudioStatsRefresher(call, audioCallstats);
				initFlipperListeners(flipper);
			}
		} else {
			flipper.setEnabled(false);
		}
	}
	
	private void initAudioStatsRefresher(final LinphoneCall call, final View view) {
		new Thread(new Runnable() {
			@Override
			public void run() {
			    final Timer timer = new Timer();
				TimerTask lTask = new TimerTask() {
					@Override
					public void run() {
						if (call == null) {
							timer.cancel();
							return;
						}
						final LinphoneCallStats audioStats = call.getAudioStats();
						if (audioStats != null) {
							mHandler.post(new Runnable() {
								@Override
								public void run() {
									TextView codec = (TextView) view.findViewById(R.id.audioCodec);
									TextView dl = (TextView) view.findViewById(R.id.audioDownloadBandwith);
									TextView ul = (TextView) view.findViewById(R.id.audioUploadBandwith);
									TextView ice = (TextView) view.findViewById(R.id.ice);
									if (codec == null || dl == null || ul == null || ice == null) {
										timer.cancel();
										return;
									}
									codec.setText(call.getCurrentParamsCopy().getUsedAudioCodec().getMime());
									dl.setText(String.valueOf((int) audioStats.getDownloadBandwidth()) + " kbits/s");
									ul.setText(String.valueOf((int) audioStats.getUploadBandwidth()) + " kbits/s");
									ice.setText(audioStats.getIceState().toString());
								}
							});
						}
					}
				};
				timer.scheduleAtFixedRate(lTask, 0, 1500);
			}
		}).start();
	}
	
	private void initFlipperListeners(final ViewFlipper flipper) {
		SwipeListener swipeListener = new SwipeListener() {
			int currentView = FLIPPER_AVATAR_VIEW;
			
			@Override
			public void onLeftToRightSwipe() {
				if (currentView == FLIPPER_AVATAR_VIEW) {
					currentView = FLIPPER_AUDIO_STATS_VIEW;
				} else {
					currentView = FLIPPER_AVATAR_VIEW;
				}
				flipper.setDisplayedChild(currentView);
			}
			
			@Override
			public void onRightToLeftSwipe() {
				if (currentView == FLIPPER_AUDIO_STATS_VIEW) {
					currentView = FLIPPER_AVATAR_VIEW;
				} else {
					currentView = FLIPPER_AUDIO_STATS_VIEW;
				}
				flipper.setDisplayedChild(currentView);
			}
		};
        flipper.setOnTouchListener(new SwipeGestureDetector(swipeListener));
	}
	
	private void setRowBackgroundAndPadding(LinearLayout callView, Resources resources, int index, LinphoneCall call, boolean active) {
		int backgroundResource;
		if (index == 0) {
//			backgroundResource = active ? R.drawable.cell_call_first_highlight : R.drawable.cell_call_first;
			backgroundResource = R.drawable.cell_call_first;
		} else {
//			backgroundResource = active ? R.drawable.cell_call_highlight : R.drawable.cell_call;
			backgroundResource = R.drawable.cell_call;
		}
		callView.findViewById(R.id.row).setBackgroundResource(backgroundResource);
		
		if (index != 0) {
			int marginIfConferenceAndCallNotInside = 0;
			if (isConferenceRunning) {
				if (!call.isInConference()) {
					marginIfConferenceAndCallNotInside = conferenceMargin;
				}
			}
    		if (previousCallIsActive) {
    			callView.setPadding(0, LinphoneUtils.pixelsToDpi(resources, (topMarginWithImage * index) + marginIfConferenceAndCallNotInside), 0, 0);
    		} else {
    			callView.setPadding(0, LinphoneUtils.pixelsToDpi(resources, (topMargin * index) + marginIfConferenceAndCallNotInside), 0, 0);
    		}
    	}
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

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.callStatus:
			LinphoneCall call = (LinphoneCall) v.getTag();
			if (incallActvityInstance != null) {
				incallActvityInstance.pauseOrResumeCall(call, true);
			}
			break;
		case R.id.conferenceStatus:
			if (incallActvityInstance != null) {
				incallActvityInstance.pauseOrResumeConference();
			}
			break;
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		incallActvityInstance = (InCallActivity) activity;
		
		if (incallActvityInstance != null) {
			incallActvityInstance.bindAudioFragment(this);
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();

		// Just to be sure we have incall controls
		if (incallActvityInstance != null) {
			incallActvityInstance.setCallControlsVisibleAndRemoveCallbacks();
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Add calls
		refreshCallList(getResources());
	}
	
	public void refreshCallList(Resources resources) {
		if (callsList == null) {
			return;
		}

		callsList.removeAllViews();
		int index = 0;
        
        if (LinphoneManager.getLc().getCallsNb() == 0) {
        	incallActvityInstance.goBackToDialer();
        	return;
        }
		
        isConferenceRunning = LinphoneManager.getLc().getConferenceSize() > 1;
        if (isConferenceRunning) {
        	displayConferenceHeader();
        	index++;
        }
        for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
        	LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.active_call, container, false);
        	displayCall(resources, callView, call, index);
        	index++;
        }
        
        callsList.invalidate();
	}
	
	class SwipeGestureDetector implements OnTouchListener {
	    static final int MIN_DISTANCE = 100;
	    private float downX, upX;
	    private boolean lock;
	    
		private SwipeListener listener;
		
		public SwipeGestureDetector(SwipeListener swipeListener) {
			super();
			listener = swipeListener;
		}
		
        @Override
    	public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
            	lock = false;
                downX = event.getX();
                return true;
                
            case MotionEvent.ACTION_MOVE:
            	if (lock) {
            		return false;
            	}
                upX = event.getX();

                float deltaX = downX - upX;

                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    lock = true;
                    if (deltaX < 0) { listener.onLeftToRightSwipe(); return true; }
                    if (deltaX > 0) { listener.onRightToLeftSwipe(); return true; }
                }
                break;
            }
            return false;
        }
    }
	
	interface SwipeListener {
		void onRightToLeftSwipe();
		void onLeftToRightSwipe();
	}
}
