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
import org.linphone.core.ChatRoomSecurityLevel;
import org.linphone.core.Factory;
import org.linphone.core.PresenceActivity;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.SearchResult;
import org.linphone.core.ZrtpPeerStatus;

import java.util.ArrayList;
import java.util.List;

import static org.linphone.LinphoneUtils.getSecurityLevelForSipUri;
import static org.linphone.LinphoneUtils.getZrtpStatus;

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
	}

	private boolean contactIsSelected(ContactAddress ca) {
		for (ContactAddress c : contactsSelected) {
			Address addr = c.getAddress();
			if (addr.getUsername() != null && ca.getAddress() != null) {
				if (addr.asStringUriOnly().compareTo(ca.getAddress().asStringUriOnly()) == 0) return true;
			} else {
				if (c.getPhoneNumber() != null && ca.getPhoneNumber() != null) {
					if (c.getPhoneNumber().compareTo(ca.getPhoneNumber()) == 0) return true;
				}
			}
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
						ContactAddress ca = null;
						if (noa.isSIPAddress()) {
							Address address = LinphoneManager.getLc().interpretUrl(noa.getValue());
							if (address != null) {
								ca = new ContactAddress(contact, address.asString(), "", contact.isFriend());
							}
						} else {
							ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
							String number = (prx != null) ? prx.normalizePhoneNumber(noa.getValue()) : noa.getValue();
							ca = new ContactAddress(contact, "", number, contact.isFriend());
						}
						if (ca != null) list.add(ca);
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
		List<ContactAddress> result = new ArrayList<>();

		String domain = "";
		ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
		if (prx != null) domain = prx.getDomain();
		if (ContactsManager.getInstance() != null && ContactsManager.getInstance().getMagicSearch() != null) {
			SearchResult[] results = ContactsManager.getInstance().getMagicSearch().getContactListFromFilter(search, mOnlySipContact ? domain : "");
			for (SearchResult sr : results) {
				boolean found = false;
				LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(sr.getAddress());
				if (contact == null) {
					contact = new LinphoneContact();
					if (sr.getFriend() != null) {
						contact.setFriend(sr.getFriend());
						contact.refresh();
					}
				}
				if (sr.getAddress() != null || sr.getPhoneNumber() != null) {
					for (ContactAddress ca : result) {
						String normalizedPhoneNumber = (ca != null && ca.getPhoneNumber() != null && prx != null) ? prx.normalizePhoneNumber(ca.getPhoneNumber()) : null;
						if ((sr.getAddress() != null && ca.getAddress() != null
								&& ca.getAddress().asStringUriOnly().equals(sr.getAddress().asStringUriOnly()))
								|| (sr.getPhoneNumber() != null && normalizedPhoneNumber != null
								&& sr.getPhoneNumber().equals(normalizedPhoneNumber))) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					result.add(new ContactAddress(contact,
							(sr.getAddress() != null) ? sr.getAddress().asStringUriOnly() : "",
							sr.getPhoneNumber(),
							contact.isFriend()));
				}
			}
		}

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
		final String a = (contact.getAddressAsDisplayableString().isEmpty()) ? contact.getPhoneNumber() : contact.getAddressAsDisplayableString();
		LinphoneContact c = contact.getContact();

		holder.avatar.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
		if (c != null && c.hasPhoto()) {
			LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.avatar, c.getThumbnailUri());
		}

		String address = contact.getAddressAsDisplayableString();
		if (c != null && c.getFullName() != null) {
			if (address == null)
				address = c.getPresenceModelForUriOrTel(a);
			holder.name.setVisibility(View.VISIBLE);
			holder.name.setText(c.getFullName());
		} else if (contact.getAddress() != null) {
			if (contact.getAddress().getUsername() != null) {
				holder.name.setVisibility(View.VISIBLE);
				holder.name.setText(contact.getAddress().getUsername());
			} else if (contact.getAddress().getDisplayName() != null) {
				holder.name.setVisibility(View.VISIBLE);
				holder.name.setText(contact.getAddress().getDisplayName());
			}
		} else if (address != null) {
			Address tmpAddr = Factory.instance().createAddress(address);
			holder.name.setVisibility(View.VISIBLE);
			holder.name.setText((tmpAddr.getDisplayName() != null) ? tmpAddr.getDisplayName() : tmpAddr.getUsername()) ;
		} else {
			holder.name.setVisibility(View.GONE);
		}
		holder.address.setText(a);
		// Obiane
		/*if (holder.linphoneContact != null) {
			if (contact.isLinphoneContact() && c != null && c.isInFriendList() && address != null) {
				holder.linphoneContact.setVisibility(View.VISIBLE);
			} else {
				holder.linphoneContact.setVisibility(View.GONE);
			}
		}*/
		if (holder.isSelect != null) {
			if (contactIsSelected(contact)) {
				holder.isSelect.setVisibility(View.VISIBLE);
			} else {
				holder.isSelect.setVisibility(View.INVISIBLE);
			}
		}

		ProxyConfig prx = LinphoneManager.getLc().getDefaultProxyConfig();
		Address ourUri = (prx != null) ? prx.getIdentityAddress() : null;
		ChatRoomSecurityLevel securityLevel = getSecurityLevelForSipUri(LinphoneManager.getLc(), ourUri, contact.getAddress());
		if (securityLevel == ChatRoomSecurityLevel.Safe) {
			holder.avatar.setImageResource(R.drawable.avatar_big_secure2);
		} else if (securityLevel == ChatRoomSecurityLevel.Unsafe) {
			holder.avatar.setImageResource(R.drawable.avatar_big_unsecure);
		} else if (securityLevel == ChatRoomSecurityLevel.Encrypted) {
			holder.avatar.setImageResource(R.drawable.avatar_big_secure1);
		} else {
			ZrtpPeerStatus zrtpStatus = getZrtpStatus(LinphoneManager.getLc(), contact.getAddress().asStringUriOnly());
			if (zrtpStatus == ZrtpPeerStatus.Valid) {
				holder.avatar.setImageResource(R.drawable.avatar_medium_secure2);
			} else if (zrtpStatus == ZrtpPeerStatus.Invalid) {
				holder.avatar.setImageResource(R.drawable.avatar_medium_unsecure);
			} else {
				if (!ContactsManager.getInstance().isContactPresenceDisabled() && c != null && c.getFriend() != null) {
					PresenceModel presenceModel = c.getFriend().getPresenceModel();
					if (presenceModel != null) {
						holder.avatar.setImageResource(R.drawable.avatar_medium_secure1);
					} else {
						holder.avatar.setImageResource(R.drawable.avatar_medium_unregistered);
					}
				} else {
					holder.avatar.setImageResource(R.drawable.avatar_medium_unregistered);
				}
			}
		}
		view.setTag(R.id.contact_search_name, address != null ? address : a);
		if (listener != null)
			view.setOnClickListener(listener);
		return view;
	}
}

