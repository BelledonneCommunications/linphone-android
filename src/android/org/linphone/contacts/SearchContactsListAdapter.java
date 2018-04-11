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

package org.linphone.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.Address;
import org.linphone.core.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class SearchContactsListAdapter extends BaseAdapter {

	private class ViewHolder {
		public TextView name;
		public TextView address;
		public ImageView linphoneContact;
		public ImageView isSelect;
		public ImageView avatar;

		public ViewHolder(View view) {
			name = view.findViewById(R.id.contact_name);
			address = view.findViewById(R.id.contact_address);
			linphoneContact = view.findViewById(R.id.contact_linphone);
			isSelect = view.findViewById(R.id.contact_is_select);
			avatar = view.findViewById(R.id.contact_picture);
		}
	}

	private List<ContactAddress> contacts;
	private List<ContactAddress> contactsSelected;
	private LayoutInflater mInflater;
	private ProgressBar progressBar;
	private boolean mOnlySipContact = false;
	private View.OnClickListener listener;
	private int oldSize;

	public List<ContactAddress> getContacts() {
		return contacts;
	}

	public void setOnlySipContact(boolean enable) {
		mOnlySipContact = enable;
	}

	public void setListener(View.OnClickListener listener) {
		this.listener = listener;
	}

	public SearchContactsListAdapter(List<ContactAddress> contactsList, LayoutInflater inflater, ProgressBar pB) {
		mInflater = inflater;
		progressBar = pB;
		setContactsSelectedList(null);
		setContactsList(contactsList);
		oldSize = 0;
	}

	private boolean contactIsSelected(ContactAddress ca) {
		for (ContactAddress c : contactsSelected) {
			Address addr = c.getAddress();
			if (addr == null) continue;
			if (addr.asStringUriOnly().compareTo(ca.getAddress().asStringUriOnly()) == 0) return true;
		}
		return false;
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
			contactsSelected = new ArrayList<>();
		} else {
			contactsSelected = contactsList;
		}
	}

	public List<ContactAddress> getContactsSelectedList() {
		return contactsSelected;
	}

	public List<ContactAddress> getContactsList() {
		List<ContactAddress> list = new ArrayList<>();
		if (ContactsManager.getInstance().hasContacts()) {
			List<LinphoneContact> contacts = mOnlySipContact ? ContactsManager.getInstance().getSIPContacts() : ContactsManager.getInstance().getContacts();
			for (LinphoneContact contact : contacts) {
				for (LinphoneNumberOrAddress noa : contact.getNumbersOrAddresses()) {
					if (!mOnlySipContact || (mOnlySipContact && (noa.isSIPAddress() || contact.getPresenceModelForUriOrTel(noa.getValue()) != null))) {
						Address address = LinphoneManager.getLc().interpretUrl(noa.getValue());
						if (address != null) {
							ContactAddress ca = new ContactAddress(contact, address.asString(), contact.isFriend());
							list.add(ca);
						}
					}
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
		return contacts.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	public void searchContacts(String search, ListView resultContactsSearch) {
		if (search == null || search.length() == 0 || search.trim().length() == 0) {
			contacts = getContactsList();
			resultContactsSearch.setAdapter(this);
			if (ContactsManager.getInstance() != null) {
				ContactsManager.getInstance().getMagicSearch().resetSearchCache();
			}
			oldSize = 0;
			return;
		}

		search = search.trim();
		List<ContactAddress> result = new ArrayList<>();

		SearchResult[] results = ContactsManager.getInstance().getMagicSearch().getContactListFromFilter(search, "");
		for (SearchResult sr : results) {
			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(sr.getAddress());
			if (contact == null) {
				contact = new LinphoneContact();
				if (sr.getFriend() != null) {
					contact.setFriend(sr.getFriend());
					contact.refresh();
				}
			}
			if (sr.getAddress() != null) {
				if (contact.getFullName() == null) {
					contact.setFullName(search);
				}

				boolean found = false;
				for (ContactAddress ca : result) {
					if (ca.getAddress().asStringUriOnly().equals(sr.getAddress().asStringUriOnly())) {
						found = true;
						break;
					}
				}
				if (!found) {
					result.add(new ContactAddress(contact, sr.getAddress().asStringUriOnly(), contact.isFriend()));
				}
			}
		}

		oldSize = search.length();
		contacts = result;
		resultContactsSearch.setAdapter(this);
		this.notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view;
		ViewHolder holder;

		if (convertView != null) {
			view = convertView;
			holder = (ViewHolder) view.getTag();
		} else {
			view = mInflater.inflate(R.layout.search_contact_cell, parent, false);
			holder = new ViewHolder(view);
			view.setTag(holder);
		}

		ContactAddress contact = getItem(position);
		final String a = contact.getAddressAsDisplayableString();
		LinphoneContact c = contact.getContact();

		holder.avatar.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
		if (c != null && c.hasPhoto()) {
			LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.avatar, c.getThumbnailUri());
		}

		String address = null;
		if (c != null && c.getFullName() != null) {
			address = c.getPresenceModelForUriOrTel(a);
			holder.name.setVisibility(View.VISIBLE);
			holder.name.setText(c.getFullName());
		} else {
			holder.name.setVisibility(View.GONE);
		}
		holder.address.setText(a);
		if (holder.linphoneContact != null) {
			if (contact.isLinphoneContact() && c != null && c.isInFriendList() && address != null) {
				holder.linphoneContact.setVisibility(View.VISIBLE);
			} else {
				holder.linphoneContact.setVisibility(View.GONE);
			}
		}
		if (holder.isSelect != null) {
			if (contactIsSelected(contact)) {
				holder.isSelect.setVisibility(View.VISIBLE);
			} else {
				holder.isSelect.setVisibility(View.INVISIBLE);
			}
		}
		view.setTag(R.id.contact_search_name, address != null ? address : a);
		if (listener != null)
			view.setOnClickListener(listener);
		return view;
	}
}

