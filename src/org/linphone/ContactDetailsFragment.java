package org.linphone;
/*
ContactDetailsFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class ContactDetailsFragment extends Fragment implements OnClickListener {
	private LinphoneContact contact;
	private ImageView editContact, deleteContact, back;
	private LayoutInflater inflater;
	private View view;
	private boolean displayChatAddressOnly = false;

	private OnClickListener dialListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
				if (lc != null) {
					LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
					String to;
					if (lpc != null) {
						String address = v.getTag().toString();
						if (!address.contains("@")) {
							to = lpc.normalizePhoneNumber(address);
						} else {
							to = v.getTag().toString();
						}
					} else {
						to = v.getTag().toString();
					}
					LinphoneActivity.instance().setAddresGoToDialerAndCall(to, contact.getFullName(), contact.getPhotoUri());
				}
			}
		}
	};
	
	private OnClickListener chatListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().displayChat(v.getTag().toString());
			}
		}
	};
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		contact = (LinphoneContact) getArguments().getSerializable("Contact");
		
		this.inflater = inflater;
		view = inflater.inflate(R.layout.contact, container, false);
		
		if (getArguments() != null) {
			displayChatAddressOnly = getArguments().getBoolean("ChatAddressOnly");
		}
		
		editContact = (ImageView) view.findViewById(R.id.editContact);
		editContact.setOnClickListener(this);
		
		deleteContact = (ImageView) view.findViewById(R.id.deleteContact);
		deleteContact.setOnClickListener(this);

		back = (ImageView) view.findViewById(R.id.back);
		if(getResources().getBoolean(R.bool.isTablet)){
			back.setVisibility(View.INVISIBLE);
		} else {
			back.setOnClickListener(this);
		}

		return view;
	}
	
	public void changeDisplayedContact(LinphoneContact newContact) {
		contact = newContact;
		displayContact(inflater, view);
	}
	
	@SuppressLint("InflateParams")
	private void displayContact(LayoutInflater inflater, View view) {
		ImageView contactPicture = (ImageView) view.findViewById(R.id.contact_picture);
		if (contact.hasPhoto()) {
			LinphoneUtils.setImagePictureFromUri(getActivity(), contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
        } else {
        	contactPicture.setImageResource(R.drawable.avatar);
        }
		
		TextView contactName = (TextView) view.findViewById(R.id.contact_name);
		contactName.setText(contact.getFullName());
		
		TableLayout controls = (TableLayout) view.findViewById(R.id.controls);
		controls.removeAllViews();
		for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
			boolean skip = false;
			View v = inflater.inflate(R.layout.contact_control_row, null);
			
			String displayednumberOrAddress = noa.getValue();
			if (displayednumberOrAddress.startsWith("sip:")) {
				displayednumberOrAddress = displayednumberOrAddress.replace("sip:", "");
			}

			TextView label = (TextView) v.findViewById(R.id.address_label);
			if (noa.isSIPAddress()) {
				label.setText(R.string.sip_address);
				skip |= getResources().getBoolean(R.bool.hide_contact_sip_addresses);
			} else {
				label.setText(R.string.phone_number);
				skip |= getResources().getBoolean(R.bool.hide_contact_phone_numbers);
			}
			
			TextView tv = (TextView) v.findViewById(R.id.numeroOrAddress);
			tv.setText(displayednumberOrAddress);
			tv.setSelected(true);
			
			if (!displayChatAddressOnly) {
				v.findViewById(R.id.contact_call).setOnClickListener(dialListener);
				v.findViewById(R.id.contact_call).setTag(displayednumberOrAddress);
			} else {
				v.findViewById(R.id.contact_call).setVisibility(View.GONE);
			}

			v.findViewById(R.id.contact_chat).setOnClickListener(chatListener);
			LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
			if (lpc != null) {
				displayednumberOrAddress = lpc.normalizePhoneNumber(displayednumberOrAddress);
				String tag = noa.getValue();
				if (!tag.startsWith("sip:")) {
					tag = "sip:" + tag;
				}
				
				if (!tag.contains("@")) {
					tag = tag + "@" + lpc.getDomain();
				}
				v.findViewById(R.id.contact_chat).setTag(tag);
			} else {
				v.findViewById(R.id.contact_chat).setTag(noa.getValue());
			}
			
			/*ImageView friend = (ImageView) v.findViewById(R.id.addFriend);
			if (getResources().getBoolean(R.bool.enable_linphone_friends) && !displayChatAddressOnly) {
				friend.setVisibility(View.VISIBLE);
				
				boolean isAlreadyAFriend = LinphoneManager.getLc().findFriendByAddress(finalNumberOrAddress) != null;
				if (!isAlreadyAFriend) {
					friend.setImageResource(R.drawable.contact_add);
					friend.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (ContactsManager.getInstance().createNewFriend(contact, finalNumberOrAddress)) {
								displayContact(ContactFragment.this.inflater, ContactFragment.this.view);
							}
						}
					});
				} else {
					friend.setImageResource(R.drawable.delete);
					friend.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (ContactsManager.getInstance().removeFriend(finalNumberOrAddress)) {
								displayContact(ContactFragment.this.inflater, ContactFragment.this.view);
							}
						}
					});
				}
			}*/
			
			if (getResources().getBoolean(R.bool.disable_chat)) {
				v.findViewById(R.id.contact_chat).setVisibility(View.GONE);
			}
			
			if (!skip) {
				controls.addView(v);
			}
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACT_DETAIL);
			LinphoneActivity.instance().hideTabBar(false);
		}
		if (contact.getFullName() == null || contact.getFullName().equals("")) {
			//Contact has been deleted, return
			LinphoneActivity.instance().displayContacts(false);
		} else {
			contact.refresh();
			displayContact(inflater, view);
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
			
		if (id == R.id.editContact) {
			LinphoneActivity.instance().editContact(contact);
		}
		if (id == R.id.deleteContact) {
			final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.delete_text));
			Button delete = (Button) dialog.findViewById(R.id.delete_button);
			Button cancel = (Button) dialog.findViewById(R.id.cancel);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					contact.delete();
					LinphoneActivity.instance().displayContacts(false);
					dialog.dismiss();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					dialog.dismiss();

				}
			});
			dialog.show();
		}
		if (id == R.id.back) {
			LinphoneActivity.instance().displayContacts(false);
		}
	}
}
