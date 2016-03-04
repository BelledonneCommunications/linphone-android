package org.linphone;
/*
HistoryDetailFragment.java
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
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class HistoryDetailFragment extends Fragment implements OnClickListener {
	private ImageView dialBack, chat, addToContacts, back;
	private View view;
	private ImageView contactPicture, callDirection;
	private TextView contactName, contactAddress, time, date;
	private String sipUri, displayName, pictureUri;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		displayName = getArguments().getString("DisplayName");
		pictureUri = getArguments().getString("PictureUri");
		String status = getArguments().getString("CallStatus");
		String callTime = getArguments().getString("CallTime");
		String callDate = getArguments().getString("CallDate");

		view = inflater.inflate(R.layout.history_detail, container, false);
		
		dialBack = (ImageView) view.findViewById(R.id.call);
		dialBack.setOnClickListener(this);

		back = (ImageView) view.findViewById(R.id.back);
		if(getResources().getBoolean(R.bool.isTablet)){
			back.setVisibility(View.INVISIBLE);
		} else {
			back.setOnClickListener(this);
		}
		
		chat = (ImageView) view.findViewById(R.id.chat);
		chat.setOnClickListener(this);
		if (getResources().getBoolean(R.bool.disable_chat))
			view.findViewById(R.id.chat).setVisibility(View.GONE);
		
		addToContacts = (ImageView) view.findViewById(R.id.add_contact);
		addToContacts.setOnClickListener(this);
		
		contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
		
		contactName = (TextView) view.findViewById(R.id.contact_name);
		contactAddress = (TextView) view.findViewById(R.id.contact_address);
		
		callDirection = (ImageView) view.findViewById(R.id.direction);
		
		time = (TextView) view.findViewById(R.id.time);
		date = (TextView) view.findViewById(R.id.date);
		
		displayHistory(status, callTime, callDate);
		
		return view;
	}
	
	private void displayHistory(String status, String callTime, String callDate) {
		if (status.equals(getResources().getString(R.string.missed))) {
			callDirection.setImageResource(R.drawable.call_missed);
		} else if (status.equals(getResources().getString(R.string.incoming))) {
			callDirection.setImageResource(R.drawable.call_incoming);
		} else if (status.equals(getResources().getString(R.string.outgoing))) {
			callDirection.setImageResource(R.drawable.call_outgoing);
		}
		
		time.setText(callTime == null ? "" : callTime);
		Long longDate = Long.parseLong(callDate);
		date.setText(LinphoneUtils.timestampToHumanDate(getActivity(),longDate,getString(R.string.history_detail_date_format)));

		LinphoneAddress lAddress = null;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}

		if(lAddress != null) {
			contactAddress.setText(lAddress.asStringUriOnly());
			Contact contact = ContactsManager.getInstance().findContactWithAddress(getActivity().getContentResolver(), lAddress);
			if (contact != null) {
				contactName.setText(contact.getName());
				LinphoneUtils.setImagePictureFromUri(view.getContext(),contactPicture,contact.getPhotoUri(),contact.getThumbnailUri());
				addToContacts.setVisibility(View.INVISIBLE);
			} else {
				contactName.setText(displayName == null ? LinphoneUtils.getAddressDisplayName(sipUri) : displayName);
				contactPicture.setImageResource(R.drawable.avatar);
				addToContacts.setVisibility(View.VISIBLE);
			}
		} else {
			contactAddress.setText(sipUri);
			contactName.setText(displayName == null ? LinphoneUtils.getAddressDisplayName(sipUri) : displayName);
		}
	}
	
	public void changeDisplayedHistory(String sipUri, String displayName, String pictureUri, String status, String callTime, String callDate) {		
		if (displayName == null ) {
			displayName = LinphoneUtils.getUsernameFromAddress(sipUri);
		}

		this.sipUri = sipUri;
		this.displayName = displayName;
		this.pictureUri = pictureUri;
		displayHistory(status, callTime, callDate);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.HISTORY_DETAIL);
			LinphoneActivity.instance().hideTabBar(false);
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.back) {
			getFragmentManager().popBackStackImmediate();
		} if (id == R.id.call) {
			LinphoneActivity.instance().setAddresGoToDialerAndCall(sipUri, displayName, pictureUri == null ? null : Uri.parse(pictureUri));
		} else if (id == R.id.chat) {
			LinphoneActivity.instance().displayChat(sipUri);
		} else if (id == R.id.add_contact) {
			String uriToAdd = sipUri;
			LinphoneActivity.instance().displayContactsForEdition(uriToAdd);
		}
	}
}
