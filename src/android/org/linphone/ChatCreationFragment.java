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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Created by Erwan Croze.
 */

public class ChatCreationFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private ListView contactsList;
	private TextView noSipContact, noContact;
	private ImageView allContacts, linphoneContacts;
	private boolean onlyDisplayLinphoneContacts;
	private View allContactsSelected, linphoneContactsSelected;
	private ImageView clearSearchField;
	private EditText searchField;
	private ProgressBar contactsFetchInProgress;
	private SearchContactsListAdapter searchAdapter;
	private ImageView back;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;
		View view = inflater.inflate(R.layout.create_chat, container, false);

		noSipContact = (TextView) view.findViewById(R.id.noSipContact);
		noContact = (TextView) view.findViewById(R.id.noContact);

		contactsList = (ListView) view.findViewById(R.id.contactsList);
		contactsList.setOnItemClickListener(this);

		allContacts = (ImageView) view.findViewById(R.id.all_contacts);
		allContacts.setOnClickListener(this);

		linphoneContacts = (ImageView) view.findViewById(R.id.linphone_contacts);
		linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);

		allContacts.setEnabled(onlyDisplayLinphoneContacts);
		linphoneContacts.setEnabled(!allContacts.isEnabled());

		back = (ImageView) view.findViewById(R.id.back);
		back.setOnClickListener(this);

		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);

		contactsFetchInProgress = (ProgressBar) view.findViewById(R.id.contactsFetchInProgress);
		contactsFetchInProgress.setVisibility(View.VISIBLE);

		searchAdapter = new SearchContactsListAdapter(null, mInflater, contactsFetchInProgress);
		contactsList.setAdapter(searchAdapter);

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

		return view;
	}

	public ChatCreationFragment() {
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
			searchAdapter.setContactsList(null);
			searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
		} else if (id == R.id.linphone_contacts) {
			searchAdapter.setOnlySipContact(true);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(false);
			allContacts.setEnabled(onlyDisplayLinphoneContacts = true);
			allContactsSelected.setVisibility(View.INVISIBLE);
			searchAdapter.setContactsList(null);
			searchAdapter.searchContacts(searchField.getText().toString(), contactsList);
		} else if (id == R.id.back) {
			getFragmentManager().popBackStackImmediate();
		} else if (id == R.id.next) {
			//TODO aller selon le nombre de selectionner en chat ou en groupe
		} else if (id == R.id.clearSearchField) {
			searchField.setText("");
			searchAdapter.searchContacts("", contactsList);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

	}

	@Override
	public void onContactsUpdated() {

	}
}
