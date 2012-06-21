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
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class AudioCallFragment extends Fragment {
	private static AudioCallFragment instance;
//	private Chronometer timer;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		instance = this;
        View view = inflater.inflate(R.layout.audio, container, false);
//        timer = (Chronometer) view.findViewById(R.id.callTimer);
        
        LinphoneCall currentCall;
        do {
        	currentCall = LinphoneManager.getLc().getCurrentCall();
        } while (currentCall == null);
        
        String sipUri = currentCall.getRemoteAddress().asStringUriOnly();
        LinphoneAddress lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
        Uri pictureUri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(lAddress, view.getContext().getContentResolver());
		
		TextView contact = (TextView) view.findViewById(R.id.contactNameOrNumber);
		contact.setText(lAddress.getDisplayName() == null ? sipUri : lAddress.getDisplayName());
		
		ImageView contactPicture = (ImageView) view.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture, Uri.parse(pictureUri.toString()), R.drawable.unknown_small);
        }
		
        return view;
    }

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		// Just to be sure we have incall controls
		InCallActivity.instance().setCallControlsVisibleAndRemoveCallbacks();
	}
	
	/**
	 * @return null if not ready yet
	 */
	public static AudioCallFragment instance() { 
		return instance;
	}
}
