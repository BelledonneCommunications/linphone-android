/*
ChatCreationFragment.java
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

package org.linphone.chat;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.SearchContactsListAdapter;
import org.linphone.ui.ContactSelectView;
import org.linphone.receivers.ContactsUpdatedListener;
import org.linphone.activities.LinphoneActivity;
import org.linphone.R;

import java.util.ArrayList;
import java.util.List;

public class ChatCreationFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private LinearLayout contactsSelectedLayout;
	private HorizontalScrollView contactsSelectLayout;
	private ArrayList<ContactAddress> contactsSelected;
	private ImageView allContacts, linphoneContacts;
	private boolean onlyDisplayLinphoneContacts;
	private View allContactsSelected, linphoneContactsSelected;
	private RelativeLayout searchLayout;
	private ImageView clearSearchField;
	private EditText searchField;
	private ProgressBar contactsFetchInProgress;
	private SearchContactsListAdapter searchAdapter;
	private ImageView back, next;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.chat_create, container, false);

		if (getArguments() != null && getArguments().getSerializable("selectedContacts") != null) {
			contactsSelected = (ArrayList<ContactAddress>) getArguments().getSerializable("selectedContacts");
		} else {
			contactsSelected = new ArrayList<>();
		}

		contactsList = view.findViewById(R.id.contactsList);
		contactsSelectedLayout = view.findViewById(R.id.contactsSelected);
		contactsSelectLayout = view.findViewById(R.id.layoutContactsSelected);

		allContacts = view.findViewById(R.id.all_contacts);
		allContacts.setOnClickListener(this);

		linphoneContacts = view.findViewById(R.id.linphone_contacts);
		linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);

		back = view.findViewById(R.id.back);
		back.setOnClickListener(this);

		next = view.findViewById(R.id.next);
		next.setOnClickListener(this);
		next.setEnabled(false);
		searchLayout = view.findViewById(R.id.layoutSearchField);

		clearSearchField = view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);

		contactsFetchInProgress = view.findViewById(R.id.contactsFetchInProgress);
		contactsFetchInProgress.setVisibility(View.VISIBLE);

		searchAdapter = new SearchContactsListAdapter(null, mInflater, contactsFetchInProgress);

		searchField = view.findViewById(R.id.searchField);
		searchField.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
			}
		});

		contactsList.setAdapter(searchAdapter);
		contactsList.setOnItemClickListener(this);
		if (savedInstanceState != null && savedInstanceState.getStringArrayList("contactsSelected") != null) {
			// We need to get all contacts not only sip
			for (String uri : savedInstanceState.getStringArrayList("contactsSelected")) {
				for (ContactAddress ca : searchAdapter.getContactsList()) {
					if (ca.getAddress().compareTo(uri) == 0) {
						updateContactsClick(ca, searchAdapter.getContactsSelectedList());
						break;
					}
				}
			}
			updateList();
			updateListSelected();
		}

		if (savedInstanceState != null ) {
			onlyDisplayLinphoneContacts = savedInstanceState.getBoolean("onlySipContact");
			updateList();
		}
		searchAdapter.setOnlySipContact(onlyDisplayLinphoneContacts);

		displayChatCreation();

		return view;
	}

	private void displayChatCreation() {
		next.setVisibility(View.VISIBLE);
		next.setEnabled(contactsSelected.size() > 0);

		contactsList.setVisibility(View.VISIBLE);
		searchLayout.setVisibility(View.VISIBLE);
		allContacts.setVisibility(View.VISIBLE);
		linphoneContacts.setVisibility(View.VISIBLE);
		if (onlyDisplayLinphoneContacts) {
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
		} else {
			allContactsSelected.setVisibility(View.VISIBLE);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		}

		allContacts.setEnabled(onlyDisplayLinphoneContacts);
		linphoneContacts.setEnabled(!allContacts.isEnabled());

		if (contactsSelected.size() > 0) {
			searchAdapter.setContactsSelectedList(contactsSelected);
			for (ContactAddress ca : contactsSelected) {
				addSelectedContactAddress(ca);
			}
		}
	}

	private void updateList() {
		searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
		searchAdapter.notifyDataSetChanged();
	}

	private void updateListSelected() {
		if (contactsSelected.size() > 0) {
			contactsSelectLayout.invalidate();
			next.setEnabled(true);
		} else {
			next.setEnabled(false);
		}
	}

	private int getIndexOfCa(ContactAddress ca, List<ContactAddress> caList) {
		for (int i = 0 ; i < caList.size() ; i++) {
			if (caList.get(i).getAddress().compareTo(ca.getAddress()) == 0)
				return i;
		}
		return -1;
	}

	private void addSelectedContactAddress(ContactAddress ca) {
		View viewContact = LayoutInflater.from(LinphoneActivity.instance()).inflate(R.layout.contact_selected, null);
		if (ca.getContact() != null) {
			((TextView) viewContact.findViewById(R.id.sipUri)).setText(ca.getContact().getFullName());
		} else {
			((TextView) viewContact.findViewById(R.id.sipUri)).setText(ca.getAddress());
		}
		View removeContact = viewContact.findViewById(R.id.contactChatDelete);
		removeContact.setTag(ca);
		removeContact.setOnClickListener(this);
		viewContact.setOnClickListener(this);
		ca.setView(viewContact);
		contactsSelectedLayout.addView(viewContact);
		contactsSelectedLayout.invalidate();
	}

	private void updateContactsClick(ContactAddress ca, List<ContactAddress> caSelectedList) {
		ca.setSelect((getIndexOfCa(ca, caSelectedList) == -1));
		if (ca.isSelect()) {
			ContactSelectView csv = new ContactSelectView(LinphoneActivity.instance());
			csv.setListener(this);
			csv.setContactName(ca);
			contactsSelected.add(ca);
			addSelectedContactAddress(ca);
		} else {
			contactsSelected.remove(getIndexOfCa(ca, contactsSelected));
			contactsSelectedLayout.removeAllViews();
			for (ContactAddress contactAddress : contactsSelected) {
				if (contactAddress.getView() != null)
					contactsSelectedLayout.addView(contactAddress.getView());
			}
		}
		searchAdapter.setContactsSelectedList(contactsSelected);
		contactsSelectedLayout.invalidate();

	}

	private void removeContactFromSelection(ContactAddress ca) {
		updateContactsClick(ca, searchAdapter.getContactsSelectedList());
		searchAdapter.notifyDataSetInvalidated();
		updateListSelected();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (contactsSelected != null && contactsSelected.size() > 0) {
			ArrayList<String> listUri = new ArrayList<String>();
			for (ContactAddress ca : contactsSelected) {
				listUri.add(ca.getAddress());
			}
			outState.putStringArrayList("contactsSelected", listUri);
		}

		outState.putBoolean("onlySipContact", onlyDisplayLinphoneContacts);

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.all_contacts) {
			searchAdapter.setOnlySipContact(onlyDisplayLinphoneContacts = false);
			allContactsSelected.setVisibility(View.VISIBLE);
			allContacts.setEnabled(false);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
			updateList();
		} else if (id == R.id.linphone_contacts) {
			searchAdapter.setOnlySipContact(true);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(false);
			allContacts.setEnabled(onlyDisplayLinphoneContacts = true);
			allContactsSelected.setVisibility(View.INVISIBLE);
			updateList();
		} else if (id == R.id.back) {
			contactsSelectedLayout.removeAllViews();
			LinphoneActivity.instance().popBackStack();
		} else if (id == R.id.next) {
			if (contactsSelected.size() == 1) {
				contactsSelectedLayout.removeAllViews();
				LinphoneActivity.instance().displayChat(contactsSelected.get(0).getAddress(), "", "");
				//TODO create group chat room with only two participants ?
			} else {
				contactsSelectedLayout.removeAllViews();
				LinphoneActivity.instance().goToChatGroupInfos(contactsSelected, null, false, true);
			}
		} else if (id == R.id.clearSearchField) {
			searchField.setText("");
			searchAdapter.searchContacts("", contactsList);
		} else if (id == R.id.contactChatDelete) {
			ContactAddress ca = (ContactAddress) view.getTag();
			removeContactFromSelection(ca);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		ContactAddress ca = searchAdapter.getContacts().get(i);
		removeContactFromSelection(ca);
	}

	@Override
	public void onContactsUpdated() {
		searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
	}
}
