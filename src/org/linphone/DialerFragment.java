package org.linphone;
/*
DialerFragment.java
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
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.EraseButton;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author Sylvain Berfini
 */
public class DialerFragment extends Fragment {
	private static DialerFragment instance;
	public boolean mVisible;
	private AddressText mAddress;
	private CallButton mCall;
	private ImageView mAddContact;
	private OnClickListener addContactListener, cancelListener;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		instance = this;
        View view = inflater.inflate(R.layout.dialer, container, false);
        
		mAddress = (AddressText) view.findViewById(R.id.Adress); 
		EraseButton erase = (EraseButton) view.findViewById(R.id.Erase);
		erase.setAddressWidget(mAddress);
		erase.requestFocus();
		
		mCall = (CallButton) view.findViewById(R.id.Call);
		mCall.setAddressWidget(mAddress);
		if (LinphoneActivity.isInstanciated() && LinphoneActivity.instance().isInCallLayout()) {
			mCall.setImageResource(R.drawable.plus);
		} else {
			mCall.setImageResource(R.drawable.appeler);
		}
		
		AddressAware numpad = (AddressAware) view.findViewById(R.id.Dialer);
		if (numpad != null)
			numpad.setAddressWidget(mAddress);
		
		mAddContact = (ImageView) view.findViewById(R.id.addContact);
		addContactListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = ContactHelper.prepareAddContactIntent(mAddress);
				startActivity(intent);
			}
		};
		cancelListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		};
		mAddContact.setOnClickListener(addContactListener);
		
		mAddContact.setEnabled(!(LinphoneActivity.isInstanciated() && LinphoneActivity.instance().isInCallLayout()));
		
		if (getArguments() != null) {
			String number = getArguments().getString("SipUri");
			String displayName = getArguments().getString("DisplayName");
			String photo = getArguments().getString("PhotoUri");
			mAddress.setText(number);
			if (displayName != null) {
				mAddress.setDisplayedName(displayName);
			}
			if (photo != null) {
				mAddress.setPictureUri(Uri.parse(photo));
			}
		}
		
		return view;
    }

	/**
	 * @return null if not ready yet
	 */
	public static DialerFragment instance() { 
		return instance;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.DIALER);
			LinphoneActivity.instance().updateDialerFragment(this);
		}
	}
	
	public void resetLayout() {
		if (LinphoneActivity.instance().isInCallLayout()) {
			mCall.setImageResource(R.drawable.plus);
			mAddContact.setImageResource(R.drawable.cancel);
			mAddContact.setOnClickListener(cancelListener);
		} else {
			mCall.setImageResource(R.drawable.appeler);
			mAddContact.setImageResource(R.drawable.add_contact);
			mAddContact.setOnClickListener(addContactListener);
		}
	}
}
