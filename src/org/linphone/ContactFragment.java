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
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.ui.AvatarWithShadow;

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
	private TextView editContact;
	private LayoutInflater inflater;
	private View view;
	private boolean displayChatAddressOnly = false;

	private OnClickListener dialListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated())
				LinphoneActivity.instance().setAddresGoToDialerAndCall(v.getTag().toString(), contact.getName(), contact.getPhotoUri());
		}
	};
	
	private OnClickListener chatListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated())
				LinphoneActivity.instance().displayChat(v.getTag().toString());
		}
	};
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		contact = (Contact) getArguments().getSerializable("Contact");
		
		this.inflater = inflater;
		view = inflater.inflate(R.layout.contact, container, false);
		
		if (getArguments() != null) {
			displayChatAddressOnly = getArguments().getBoolean("ChatAddressOnly");
		}
		
		editContact = (TextView) view.findViewById(R.id.editContact);
		editContact.setOnClickListener(this);
		
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
			InputStream input = Compatibility.getContactPictureInputStream(LinphoneActivity.instance().getContentResolver(), contact.getID());
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
			
			String displayednumberOrAddress = numberOrAddress;
			if (numberOrAddress.startsWith("sip:")) {
				displayednumberOrAddress = displayednumberOrAddress.substring(4);
			}
			
			TextView tv = (TextView) v.findViewById(R.id.numeroOrAddress);
			tv.setText(displayednumberOrAddress);
			tv.setSelected(true);
			
			if (!displayChatAddressOnly) {
				v.findViewById(R.id.dial).setOnClickListener(dialListener);
				v.findViewById(R.id.dial).setTag(displayednumberOrAddress);
			} else {
				v.findViewById(R.id.dial).setVisibility(View.GONE);
			}

			v.findViewById(R.id.chat).setOnClickListener(chatListener);
			if (LinphoneUtils.isSipAddress(numberOrAddress)) {
				v.findViewById(R.id.chat).setTag(numberOrAddress);
			} else {
				LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
				if (lpc != null) {
					if (!numberOrAddress.startsWith("sip:")) {
						numberOrAddress = "sip:" + numberOrAddress;
					}
					v.findViewById(R.id.chat).setTag(numberOrAddress + "@" + lpc.getDomain());
				}
			}
			
			final String finalNumberOrAddress = numberOrAddress;
			ImageView friend = (ImageView) v.findViewById(R.id.addFriend);
			if (getResources().getBoolean(R.bool.enable_linphone_friends) && !displayChatAddressOnly) {
				friend.setVisibility(View.VISIBLE);
				
				boolean isAlreadyAFriend = LinphoneManager.getLc().findFriendByAddress(finalNumberOrAddress) != null;
				if (!isAlreadyAFriend) {
					friend.setImageResource(R.drawable.friend_add);
					friend.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (LinphoneActivity.instance().newFriend(contact, finalNumberOrAddress)) {
								displayContact(ContactFragment.this.inflater, ContactFragment.this.view);
							}
						}
					});
				} else {
					friend.setImageResource(R.drawable.friend_remove);
					friend.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (LinphoneActivity.instance().removeFriend(contact, finalNumberOrAddress)) {
								displayContact(ContactFragment.this.inflater, ContactFragment.this.view);
							}
						}
					});
				}
			}
			
			if (getResources().getBoolean(R.bool.disable_chat)) {
				v.findViewById(R.id.chat).setVisibility(View.GONE);
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
		if (contact.getName() == null || contact.getName().equals("")) {
			//Contact has been deleted, return
			LinphoneActivity.instance().displayContacts(false);
		}
		displayContact(inflater, view);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
			
		if (id == R.id.editContact) {
			LinphoneActivity.instance().editContact(contact);
		}
	}
}
