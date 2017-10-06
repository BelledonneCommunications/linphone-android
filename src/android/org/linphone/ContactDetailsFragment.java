package org.linphone;

/*
ContactDetailsFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

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

public class ContactDetailsFragment extends Fragment implements OnClickListener {
	private LinphoneContact contact;
	private ImageView editContact, deleteContact, back;
	private TextView organization;
	private LayoutInflater inflater;
	private View view;
	private boolean displayChatAddressOnly = false;

	private OnClickListener dialListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().setAddresGoToDialerAndCall(v.getTag().toString(), contact.getFullName(), contact.getPhotoUri());
			}
		}
	};

	private OnClickListener chatListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (LinphoneActivity.isInstanciated()) {
				LinphoneActivity.instance().displayChat(v.getTag().toString(), null, null);
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

		organization = (TextView) view.findViewById(R.id.contactOrganization);
		boolean isOrgVisible = getResources().getBoolean(R.bool.display_contact_organization);
		String org = contact.getOrganization();
		if (org != null && !org.isEmpty() && isOrgVisible) {
			organization.setText(org);
		} else {
			organization.setVisibility(View.GONE);
		}

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
        	contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
        }

		TextView contactName = (TextView) view.findViewById(R.id.contact_name);
		contactName.setText(contact.getFullName());
		organization.setText((contact.getOrganization() != null) ? contact.getOrganization() : "");

		TableLayout controls = (TableLayout) view.findViewById(R.id.controls);
		controls.removeAllViews();
		for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
			boolean skip = false;
			View v = inflater.inflate(R.layout.contact_control_row, null);

			String value = noa.getValue();
			String displayednumberOrAddress = LinphoneUtils.getDisplayableUsernameFromAddress(value);

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


			LinphoneProxyConfig lpc = LinphoneManager.getLc().getDefaultProxyConfig();
			if (lpc != null) {
				String username = lpc.normalizePhoneNumber(displayednumberOrAddress);
				value = LinphoneUtils.getFullAddressFromUsername(username);
			}

			String contactAddress = contact.getPresenceModelForUri(noa.getValue());
			if (contactAddress != null) {
				v.findViewById(R.id.friendLinphone).setVisibility(View.VISIBLE);
			}

			if (!displayChatAddressOnly) {
				v.findViewById(R.id.contact_call).setOnClickListener(dialListener);
				if (contactAddress != null) {
					v.findViewById(R.id.contact_call).setTag(contactAddress);
				} else {
					v.findViewById(R.id.contact_call).setTag(value);
				}
			} else {
				v.findViewById(R.id.contact_call).setVisibility(View.GONE);
			}

			v.findViewById(R.id.contact_chat).setOnClickListener(chatListener);
			if (contactAddress != null) {
				v.findViewById(R.id.contact_chat).setTag(contactAddress);
			} else {
				v.findViewById(R.id.contact_chat).setTag(value);
			}

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
		contact.refresh();
		displayContact(inflater, view);
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
			getFragmentManager().popBackStackImmediate();
		}
	}
}
