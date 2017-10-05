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

package org.linphone;

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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erwan Croze.
 */

public class ChatCreationFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private LinearLayout contactsSelectedLayout;
	private HorizontalScrollView contactsSelectLayout;
	private TextView noSipContact, noContact;
	private List<ContactAddress> contactsSelected;
	private ImageView allContacts, linphoneContacts;
	private boolean onlyDisplayLinphoneContacts;
	private View allContactsSelected, linphoneContactsSelected;
	private ImageView clearSearchField;
	private EditText searchField;
	private ProgressBar contactsFetchInProgress;
	private SearchContactsListAdapter searchAdapter;
	private ImageView back, next;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.create_chat, container, false);
		contactsSelected = new ArrayList<ContactAddress>();

		noSipContact = (TextView) view.findViewById(R.id.noSipContact);
		noContact = (TextView) view.findViewById(R.id.noContact);

		contactsList = (ListView) view.findViewById(R.id.contactsList);
		contactsSelectedLayout = (LinearLayout) view.findViewById(R.id.contactsSelected);
		contactsSelectLayout = (HorizontalScrollView) view.findViewById(R.id.layoutContactsSelected);

		allContacts = (ImageView) view.findViewById(R.id.all_contacts);
		allContacts.setOnClickListener(this);

		linphoneContacts = (ImageView) view.findViewById(R.id.linphone_contacts);
		linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);

		back = (ImageView) view.findViewById(R.id.back);
		back.setOnClickListener(this);

		next = (ImageView) view.findViewById(R.id.next);
		next.setOnClickListener(this);

		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);

		contactsFetchInProgress = (ProgressBar) view.findViewById(R.id.contactsFetchInProgress);
		contactsFetchInProgress.setVisibility(View.VISIBLE);

		searchAdapter = new SearchContactsListAdapter(null, mInflater, contactsFetchInProgress);

		searchField = (EditText) view.findViewById(R.id.searchField);
		searchField.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
			                              int after) {

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
			if (onlyDisplayLinphoneContacts) {
				allContactsSelected.setVisibility(View.INVISIBLE);
				linphoneContactsSelected.setVisibility(View.VISIBLE);
			} else {
				allContactsSelected.setVisibility(View.VISIBLE);
				linphoneContactsSelected.setVisibility(View.INVISIBLE);
			}
			updateList();
		}

		searchAdapter.setOnlySipContact(onlyDisplayLinphoneContacts);
		allContacts.setEnabled(onlyDisplayLinphoneContacts);
		linphoneContacts.setEnabled(!allContacts.isEnabled());

		return view;
	}

	private void updateList() {
		searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
		searchAdapter.notifyDataSetChanged();
	}

	private void updateListSelected() {
		if (contactsSelected.size() > 0) {
			contactsSelectLayout.setVisibility(View.VISIBLE);
			contactsSelectLayout.invalidate();
		} else {
			contactsSelectLayout.setVisibility(View.GONE);
		}
	}

	private int getIndexOfCa(ContactAddress ca, List<ContactAddress> caList) {
		for (int i = 0 ; i < caList.size() ; i++) {
			if (caList.get(i).getAddress().compareTo(ca.getAddress()) == 0)
				return i;
		}
		return -1;
	}

	private void updateContactsClick(ContactAddress ca, List<ContactAddress> caSelectedList) {
		ca.setSelect((getIndexOfCa(ca, caSelectedList) == -1));
		if(ca.isSelect()) {
			ContactSelectView csv = new ContactSelectView(LinphoneActivity.instance());
			csv.setListener(this);
			csv.setContactName(ca);
			contactsSelected.add(ca);
			View viewContact = LayoutInflater.from(LinphoneActivity.instance()).inflate(R.layout.contact_selected, null);
			((TextView)viewContact.findViewById(R.id.sipUri)).setText(ca.getContact().getFullName());
			viewContact.findViewById(R.id.contactChatDelete).setOnClickListener(this);
			viewContact.setOnClickListener(this);
			ca.setView(viewContact);
			contactsSelectedLayout.addView(viewContact);
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

	private void removeContactFromView(View v) {
		for (ContactAddress ca : contactsSelected) {
			if (ca.getView() == v) {
				ca.setSelect(false);
				updateContactsClick(ca, searchAdapter.getContactsSelectedList());
			}
		}
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
			getFragmentManager().popBackStackImmediate();
		} else if (id == R.id.next) {
			//TODO aller selon le nombre de selectionner en chat ou en groupe
		} else if (id == R.id.clearSearchField) {
			searchField.setText("");
			searchAdapter.searchContacts("", contactsList);
		} else if (id == R.id.deleteContact) {
			//TODO
			removeContactFromView(view);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		// Get contact
		ContactAddress ca = searchAdapter.getContacts().get(i);
		updateContactsClick(ca, searchAdapter.getContactsSelectedList());
		updateList();
		updateListSelected();
	}

	@Override
	public void onContactsUpdated() {
		searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
	}
}
