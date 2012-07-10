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
import org.linphone.core.LinphoneCoreFactory;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class AudioCallFragment extends Fragment {
	private static AudioCallFragment instance;
	private LinearLayout callsList;
	private LayoutInflater inflater;
	private ViewGroup container;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		instance = this;
		this.inflater = inflater;
		this.container = container;
		
        View view = inflater.inflate(R.layout.audio, container, false);
        callsList = (LinearLayout) view.findViewById(R.id.calls);
        
        return view;
    }
	
	private void displayCall(Resources resources, LinearLayout callView, LinphoneCall call) {
		String sipUri = call.getRemoteAddress().asStringUriOnly();
        LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
//        Uri pictureUri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, callView.getContext().getContentResolver());
		
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
		
//		ImageView contactPicture = (ImageView) callView.findViewById(R.id.contactPicture);
//		if (pictureUri != null) {
//        	LinphoneUtils.setImagePictureFromUri(callView.getContext(), contactPicture, Uri.parse(pictureUri.toString()), R.drawable.unknown_small);
//        }
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
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
		callsList.removeAllViews();
		
		boolean first = true;
        for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
        	LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.active_call, container, false);
        	displayCall(resources, callView, call);
        	
        	if (first) {
        		callView.setBackgroundResource(R.drawable.sel_call_first);
        		first = false;
        	} else {
        		callView.setBackgroundResource(R.drawable.sel_call);
        	}
        	
        	callsList.addView(callView);
        }
        callsList.invalidate();
	}
}
