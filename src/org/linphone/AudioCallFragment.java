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
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCoreFactory;

import android.app.Activity;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class AudioCallFragment extends Fragment {
	private static AudioCallFragment instance;
	private RelativeLayout callsList;
	private LayoutInflater inflater;
	private ViewGroup container;
	private static final int rowHeight = 75; // Value set in active_call.xml
	private static final int rowImageHeight = 100; // Value set in active_call.xml
	private static final int rowThickRatio = 85; // Ratio dependent from the image
	private static final int topMargin = (int) ((rowHeight * rowThickRatio) / 100);
	private static final int topMarginWithImage = topMargin + rowImageHeight;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		instance = this;
		this.inflater = inflater;
		this.container = container;
		
        View view = inflater.inflate(R.layout.audio, container, false);
        callsList = (RelativeLayout) view.findViewById(R.id.calls);
        
        return view;
    }
	
	private void displayCall(Resources resources, LinearLayout callView, LinphoneCall call, int index) {
		String sipUri = call.getRemoteAddress().asStringUriOnly();
        LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
        Uri pictureUri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, callView.getContext().getContentResolver());

		setContactName(callView, lAddress, sipUri, resources);
		boolean hide = displayCallStatusIconAndReturnCallPaused(callView, call);
		displayOrHideContactPicture(callView, pictureUri, hide);
		setRowBackgroundAndPadding(callView, resources, index);
		registerCallDurationTimer(callView, call);
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
		boolean isCallPaused;
		ImageView callState = (ImageView) callView.findViewById(R.id.callStatus);
		if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
			callState.setImageResource(R.drawable.pause_default);
			isCallPaused = true;
		} else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
			callState.setImageResource(R.drawable.call_state_ringing_default);
			isCallPaused = false;
		} else {
			callState.setImageResource(R.drawable.play_default);
			isCallPaused = false;
		}
		return isCallPaused;
	}
	
	private void displayOrHideContactPicture(LinearLayout callView, Uri pictureUri, boolean hide) {
		ImageView contactPicture = (ImageView) callView.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(callView.getContext(), contactPicture, Uri.parse(pictureUri.toString()), R.drawable.unknown_small);
        }
		if (hide) {
			contactPicture.setVisibility(View.GONE);
		}
	}
	
	private void setRowBackgroundAndPadding(LinearLayout callView, Resources resources, int index) {
		if (index == 0) {
    		callView.findViewById(R.id.row).setBackgroundResource(R.drawable.sel_call_first);
    	} else {
    		callView.findViewById(R.id.row).setBackgroundResource(R.drawable.sel_call);
    		callView.setPadding(0, LinphoneUtils.pixelsToDpi(resources, topMargin * index), 0, 0);
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
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (InCallActivity.instance() == null) {
			return;
		}
		
		InCallActivity.instance().bindAudioFragment(this);
		
		// Just to be sure we have incall controls
		InCallActivity.instance().setCallControlsVisibleAndRemoveCallbacks();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Add calls
		refreshCallList(getResources());
	}
	
	/**
	 * @return null if not ready yet
	 */
	public static AudioCallFragment instance() { 
		return instance;
	}
	
	public void refreshCallList(Resources resources) {
		if (callsList == null) {
			return;
		}

		callsList.removeAllViews();
		int index = 0;
		
        for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
        	LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.active_call, container, false);
        	displayCall(resources, callView, call, index);
        	callsList.addView(callView);
        	index++;
        }
        
        callsList.invalidate();
	}
}
