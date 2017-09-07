package org.linphone;

/*
SearchContactsListAdapter.java
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Erwan Croze.
 */

public class SearchContactsListAdapter extends BaseAdapter {

	private class ViewHolder {
		public TextView name;
		public TextView address;
		public ImageView linphoneContact;
		public ImageView isSelect;

		public ViewHolder(View view) {
			name = (TextView) view.findViewById(R.id.contact_name);
			address = (TextView) view.findViewById(R.id.contact_address);
			linphoneContact = (ImageView) view.findViewById(R.id.contact_linphone);
			isSelect = (ImageView) view.findViewById(R.id.contact_is_select);
		}
	}

	private List<ContactAddress> contacts;
	private List<ContactAddress> contactsSelected;
	private LayoutInflater mInflater;
	private ProgressBar progressBar;
	private boolean mOnlySipContact = false;
	private View.OnClickListener listener;

	public List<ContactAddress> getContacts() {
		return contacts;
	}

	public void setOnlySipContact(boolean enable) {
		mOnlySipContact = enable;
	}

	public void setListener(View.OnClickListener listener) {
		this.listener = listener;
	}

	SearchContactsListAdapter(List<ContactAddress> contactsList, LayoutInflater inflater, ProgressBar pB) {
		mInflater = inflater;
		progressBar = pB;
		setContactsSelectedList(null);
		setContactsList(contactsList);
	}

	public void setContactsList(List<ContactAddress> contactsList) {
		if (contactsList == null) {
			contacts = getContactsList();
			if (contacts.size() > 0 && progressBar != null)
				progressBar.setVisibility(View.GONE);
		} else {
			contacts = contactsList;
		}
	}

	public void setContactsSelectedList(List<ContactAddress> contactsList) {
		if (contactsList == null) {
			contactsSelected = new ArrayList<ContactAddress>();
		} else {
			contactsSelected = contactsList;
		}
	}

	public List<ContactAddress> getContactsList() {
		List<ContactAddress> list = new ArrayList<ContactAddress>();
		if(ContactsManager.getInstance().hasContacts()) {
			for (LinphoneContact con : (mOnlySipContact)
					? ContactsManager.getInstance().getSIPContacts() : ContactsManager.getInstance().getContacts()) {
				for (LinphoneNumberOrAddress noa : con.getNumbersOrAddresses()) {
					String value = noa.getValue();
					// Fix for sip:username compatibility issue
					if (value.startsWith("sip:") && !value.contains("@")) {
						value = value.substring(4);
						value = LinphoneUtils.getFullAddressFromUsername(value);
					}
					ContactAddress ca = new ContactAddress(con, value, con.isInLinphoneFriendList());
					list.add(ca);
				}
			}
		}

		for (ContactAddress caS : contactsSelected) {
			for (ContactAddress ca : list) {
				if (ca.equals(caS)) ca.setSelect(true);
			}
		}
		return list;
	}

	public int getCount() {
		return contacts.size();
	}

	public ContactAddress getItem(int position) {
		if (contacts == null || position >= contacts.size()) {
			contacts = getContactsList();
			return contacts.get(position);
		} else {
			return contacts.get(position);
		}
	}

	public long getItemId(int position) {
		return position;
	}

	public void searchContacts(String search, ListView resultContactsSearch) {
		if (search == null || search.length() == 0) {
			contacts = getContactsList();
			resultContactsSearch.setAdapter(this);
			return;
		}

		List<ContactAddress> result = new ArrayList<ContactAddress>();
		if(search != null) {
			for (ContactAddress c : getContacts()) {
				String address = c.getAddress();
				if (address.startsWith("sip:")) address = address.substring(4);
				if (c.getContact().getFullName() != null
						&& c.getContact().getFullName().toLowerCase(Locale.getDefault()).startsWith(search.toLowerCase(Locale.getDefault()))
						|| address.toLowerCase(Locale.getDefault()).startsWith(search.toLowerCase(Locale.getDefault()))) {
					result.add(c);
				}
			}
		}

		setContactsList(result);
		resultContactsSearch.setAdapter(this);
		this.notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view;
		ViewHolder holder;
		ContactAddress contact;

		do {
			contact = getItem(position);
		} while (contact == null);

		if (convertView != null) {
			view = convertView;
			holder = (ViewHolder) view.getTag();
		} else {
			view = mInflater.inflate(R.layout.search_contact_cell, parent, false);
			holder = new ViewHolder(view);
			view.setTag(holder);
		}

		final String a = contact.getAddress();
		LinphoneContact c = contact.getContact();

		holder.name.setText(c.getFullName());
		holder.address.setText(a);
		if (holder.linphoneContact != null) {
			if (contact.isLinphoneContact()) {
				holder.linphoneContact.setVisibility(View.VISIBLE);
			} else {
				holder.linphoneContact.setVisibility(View.GONE);
			}
		}
		if (holder.isSelect != null) {
			if (contact.isSelect()) {
				holder.isSelect.setVisibility(View.VISIBLE);
			} else {
				holder.isSelect.setVisibility(View.INVISIBLE);
			}
		}
		view.setTag(R.id.contact_search_name, a);
		if (listener != null)
			view.setOnClickListener(listener);
		return view;
	}
}

