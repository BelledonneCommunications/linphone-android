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

import org.linphone.ui.AvatarWithShadow;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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
	private ImageView dialBack, chat, addToContacts;
	private AvatarWithShadow contactPicture;
	private View view;
	private TextView contactName, contactAddress, callDirection, time, date;
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
		
		contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
		
		dialBack = (ImageView) view.findViewById(R.id.dialBack);
		dialBack.setOnClickListener(this);
		
		chat = (ImageView) view.findViewById(R.id.chat);
		chat.setOnClickListener(this);
		if (getResources().getBoolean(R.bool.disable_chat))
			view.findViewById(R.id.chatRow).setVisibility(View.GONE);
		
		addToContacts = (ImageView) view.findViewById(R.id.addToContacts);
		addToContacts.setOnClickListener(this);
		
		contactName = (TextView) view.findViewById(R.id.contactName);
		if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
			displayName = LinphoneUtils.getUsernameFromAddress(sipUri);
		}
		
		contactAddress = (TextView) view.findViewById(R.id.contactAddress);
		
		callDirection = (TextView) view.findViewById(R.id.callDirection);
		
		time = (TextView) view.findViewById(R.id.time);
		date = (TextView) view.findViewById(R.id.date);
		
		displayHistory(status, callTime, callDate);
		
		return view;
	}
	
	private void displayHistory(String status, String callTime, String callDate) {
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture.getView(), Uri.parse(pictureUri), R.drawable.unknown_small);
        	view.findViewById(R.id.addContactRow).setVisibility(View.GONE);
        }
		
		contactName.setText(displayName == null ? sipUri : displayName);
		contactAddress.setText(sipUri);
		
		if (status.equals("Missed")) {
			callDirection.setText(getString(R.string.call_state_missed));
		} else if (status.equals("Incoming")) {
			callDirection.setText(getString(R.string.call_state_incoming));
		} else if (status.equals("Outgoing")) {
			callDirection.setText(getString(R.string.call_state_outgoing));
		} else {
			callDirection.setText(status);
		}
		
		time.setText(callTime == null ? "" : callTime);
		date.setText(timestampToHumanDate(callDate));
	}
	
	public void changeDisplayedHistory(String sipUri, String displayName, String pictureUri, String status, String callTime, String callDate) {		
		if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
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
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.dialBack) {
			LinphoneActivity.instance().setAddresGoToDialerAndCall(sipUri, displayName, pictureUri == null ? null : Uri.parse(pictureUri));
		} else if (id == R.id.chat) {
			LinphoneActivity.instance().displayChat(sipUri);
		} else if (id == R.id.addToContacts) {
			LinphoneActivity.instance().displayContactsForEdition(sipUri);
		}
	}
	
	@SuppressLint("SimpleDateFormat")
	private String timestampToHumanDate(String timestamp) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(Long.parseLong(timestamp));
		
		SimpleDateFormat dateFormat;
		dateFormat = new SimpleDateFormat(getResources().getString(R.string.history_detail_date_format));
		return dateFormat.format(cal.getTime());
	}
}
