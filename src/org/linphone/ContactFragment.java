package org.linphone;
/*
ContactFragment.java
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
import java.io.InputStream;

import org.linphone.compatibility.Compatibility;
import org.linphone.ui.AvatarWithShadow;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ContactFragment extends Fragment implements OnClickListener {
	private Contact contact;
	private ImageView back, editContact, newContact;
	private LayoutInflater inflater;
	private View view;

	private OnClickListener dialListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			LinphoneActivity.instance().setAddresGoToDialerAndCall(v.getTag().toString(), contact.getName(), contact.getPhotoUri());
		}
	};
	
	private OnClickListener chatListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			LinphoneActivity.instance().displayChat(v.getTag().toString());
		}
	};
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		contact = (Contact) getArguments().getSerializable("Contact");
		
		this.inflater = inflater;
		view = inflater.inflate(R.layout.contact, container, false);
		
		back = (ImageView) view.findViewById(R.id.back);
		back.setOnClickListener(this);
		editContact = (ImageView) view.findViewById(R.id.editContact);
		editContact.setOnClickListener(this);
		newContact = (ImageView) view.findViewById(R.id.newContact);
		newContact.setOnClickListener(this);
		
		return view;
	}
	
	public void changeDisplayedContact(Contact newContact) {
		contact = newContact;
		contact.refresh(getActivity().getContentResolver());
		displayContact(inflater, view);
	}
	
	private void displayContact(LayoutInflater inflater, View view) {
		AvatarWithShadow contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
		if (contact.getPhotoUri() != null) {
			InputStream input = Compatibility.getContactPictureInputStream(getActivity().getContentResolver(), contact.getID());
			contactPicture.setImageBitmap(BitmapFactory.decodeStream(input));
        } else {
        	contactPicture.setImageResource(R.drawable.unknown_small);
        }
		
		TextView contactName = (TextView) view.findViewById(R.id.contactName);
		contactName.setText(contact.getName());	
		
		TableLayout controls = (TableLayout) view.findViewById(R.id.controls);
		controls.removeAllViews();
		for (String numberOrAddress : contact.getNumerosOrAddresses()) {
			View v = inflater.inflate(R.layout.contact_control_row, null);
			
			((TextView) v.findViewById(R.id.numeroOrAddress)).setText(numberOrAddress);
			
			v.findViewById(R.id.dial).setOnClickListener(dialListener);
			v.findViewById(R.id.dial).setTag(numberOrAddress);

			if (LinphoneUtils.isSipAddress(numberOrAddress)) {
				v.findViewById(R.id.chat).setOnClickListener(chatListener);
				v.findViewById(R.id.chat).setTag(numberOrAddress);
			} else {
				v.findViewById(R.id.chat).setVisibility(View.INVISIBLE);
			}
			
			controls.addView(v);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACT);
		}
		contact.refresh(getActivity().getContentResolver());
		displayContact(inflater, view);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		Intent intent;
			
		switch (id) {
		case R.id.back:
			LinphoneActivity.instance().onBackPressed();
			break;
			
		case R.id.editContact:
			intent = Compatibility.prepareEditContactIntent(Integer.parseInt(contact.getID()));
			startActivity(intent);
			break;
			
		case R.id.newContact:
			intent = Compatibility.prepareAddContactIntent("", "");
			startActivity(intent);
			break;
		}
	}
}
