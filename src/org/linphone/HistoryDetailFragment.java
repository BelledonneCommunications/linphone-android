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
import org.linphone.compatibility.Compatibility;

import android.content.Intent;
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
	private ImageView contactPicture, dialBack, chat, addToContacts;
	private TextView contactName, callDirection, time, date, dialBackUri;
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
		
		View view = inflater.inflate(R.layout.history_detail, container, false);
		
		contactPicture = (ImageView) view.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(view.getContext(), contactPicture, Uri.parse(pictureUri), R.drawable.unknown_small);
        }
		
		dialBack = (ImageView) view.findViewById(R.id.dialBack);
		dialBack.setOnClickListener(this);
		
		chat = (ImageView) view.findViewById(R.id.chat);
		chat.setOnClickListener(this);
		
		addToContacts = (ImageView) view.findViewById(R.id.addToContacts);
		addToContacts.setOnClickListener(this);
		
		contactName = (TextView) view.findViewById(R.id.contactName);
		contactName.setText(displayName == null ? sipUri : displayName);
		
		dialBackUri = (TextView) view.findViewById(R.id.dialBackUri);
		dialBackUri.setText(sipUri);
		
		callDirection = (TextView) view.findViewById(R.id.callDirection);
		callDirection.setText(status);
		
		time = (TextView) view.findViewById(R.id.time);
		time.setText(callTime == null ? "" : callTime);
		date = (TextView) view.findViewById(R.id.date);
		date.setText(callDate == null ? "" : callDate);
		
		
		return view;
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
			LinphoneActivity.instance().setAddressAndGoToDialer(sipUri, displayName, pictureUri == null ? null : Uri.parse(pictureUri));
		} else if (id == R.id.chat) {
			LinphoneActivity.instance().displayChat(sipUri);
		} else if (id == R.id.addToContacts) {
			Intent intent = Compatibility.prepareAddContactIntent(displayName, sipUri);
			startActivity(intent);
		}
	}
}
