package org.linphone.contacts;

/*
ContactsListFragment.java
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


import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.TextView;

import org.linphone.fragments.FragmentsAvailable;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.ui.SelectableHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ContactsListFragment extends Fragment implements OnItemClickListener, ContactsUpdatedListener, ContactsListAdapter.ViewHolder.ClickListener ,SelectableHelper.DeleteListener {
//public class ContactsListFragment extends Fragment implements OnClickListener, OnItemClickListener, ContactsUpdatedListener {
//public class ContactsListFragment extends Fragment implements OnClickListener, OnItemClickListener, ContactsUpdatedListener {
	private LayoutInflater mInflater;
	private RecyclerView contactsList;
	private TextView noSipContact, noContact;
	private ImageView allContacts, linphoneContacts, newContact, edit, selectAll, deselectAll, delete, cancel;
	private boolean onlyDisplayLinphoneContacts;
	private View allContactsSelected, linphoneContactsSelected;
	private LinearLayout editList, topbar;
	private int lastKnownPosition;
	private boolean editOnClick = false, editConsumed = false, onlyDisplayChatAddress = false;
	private String sipAddressToAdd, displayName = null;
	private ImageView clearSearchField;
	private EditText searchField;
	private ProgressBar contactsFetchInProgress;
	private LinearLayoutManager layoutManager;
	private Context mContext;
	private SelectableHelper mSelectionHelper;
	private ContactsListAdapter mContactAdapter;

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mInflater = inflater;
        View view = inflater.inflate(R.layout.contacts_list, container, false);
		mContext = getActivity().getApplicationContext();
		mSelectionHelper = new SelectableHelper(view, this);
        if (getArguments() != null) {
	        editOnClick = getArguments().getBoolean("EditOnClick");
	        sipAddressToAdd = getArguments().getString("SipAddress");
			if(getArguments().getString("DisplayName") != null)
				displayName = getArguments().getString("DisplayName");
			onlyDisplayChatAddress = getArguments().getBoolean("ChatAddressOnly");
        }

        noSipContact = (TextView) view.findViewById(R.id.noSipContact);
        noContact = (TextView) view.findViewById(R.id.noContact);

        contactsList = view.findViewById(R.id.contactsList);
        contactsList.setOnItemClickListener(this);

        allContacts = (ImageView) view.findViewById(R.id.all_contacts);
        allContacts.setOnClickListener(this);

        linphoneContacts = (ImageView) view.findViewById(R.id.linphone_contacts);
        linphoneContacts.setOnClickListener(this);

		allContactsSelected = view.findViewById(R.id.all_contacts_select);
		linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);

        newContact = (ImageView) view.findViewById(R.id.newContact);
        newContact.setOnClickListener(this);
        newContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);

        allContacts.setEnabled(onlyDisplayLinphoneContacts);
        linphoneContacts.setEnabled(!allContacts.isEnabled());

		selectAll = (ImageView) view.findViewById(R.id.select_all);
		selectAll.setOnClickListener(this);

		deselectAll = (ImageView) view.findViewById(R.id.deselect_all);
		deselectAll.setOnClickListener(this);

		delete = (ImageView) view.findViewById(R.id.delete);
		delete.setOnClickListener(this);

		editList = (LinearLayout) view.findViewById(R.id.edit_list);
		topbar = (LinearLayout) view.findViewById(R.id.top_bar);

		cancel = (ImageView) view.findViewById(R.id.cancel);
		cancel.setOnClickListener(this);

		edit = (ImageView) view.findViewById(R.id.edit);
		edit.setOnClickListener(this);

		clearSearchField = (ImageView) view.findViewById(R.id.clearSearchField);
		clearSearchField.setOnClickListener(this);

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
				searchContacts(searchField.getText().toString());
			}
		});

		contactsFetchInProgress = (ProgressBar) view.findViewById(R.id.contactsFetchInProgress);
		contactsFetchInProgress.setVisibility(View.VISIBLE);



		layoutManager = new LinearLayoutManager(mContext);
        contactsList.setLayoutManager(layoutManager);



		ContactsManager.getInstance().fetchContactsAsync();

		return view;
    }

	public int getNbItemsChecked(){
		int size = contactsList.getAdapter().getItemCount();
		int nb = 0;
		for(int i=0; i<size; i++) {
			if(contactsList.isItemChecked(i)) {
				nb ++;
			}
		}
		return nb;
	}

	public void enabledDeleteButton(Boolean enabled){
		if(enabled){
			delete.setEnabled(true);
		} else {
			if (getNbItemsChecked() == 0){
				delete.setEnabled(false);
			}
		}
	}

	public void quitEditMode(){
		isEditMode = false;
		editList.setVisibility(View.GONE);
		topbar.setVisibility(View.VISIBLE);
		invalidate();
		if(getResources().getBoolean(R.bool.isTablet)){
			displayFirstContact();
		}
	}

	public void displayFirstContact(){
		if (contactsList != null && contactsList.getAdapter() != null && contactsList.getAdapter().getItemCount() > 0) {
			ContactsListAdapter mAdapt = (ContactsListAdapter)contactsList.getAdapter();
			LinphoneActivity.instance().displayContact((LinphoneContact) mAdapt.getItem(0), false);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	private void searchContacts() {
		searchContacts(searchField.getText().toString());
	}

	private void searchContacts(String search) {
		if (search == null || search.length() == 0) {
			changeContactsAdapter();
			return;
		}
		changeContactsToggle();
		List<LinphoneContact> listContact;
		isSearchMode = true;

		if (onlyDisplayLinphoneContacts) {
			listContact = ContactsManager.getInstance().getSIPContacts(search);
		} else {
			listContact = ContactsManager.getInstance().getContacts(search);
		}
		mContactAdapter = new ContactsListAdapter(mContext, listContact, this, mSelectionHelper);
		contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
		contactsList.setAdapter(mContactAdapter);





	}

	private void changeContactsAdapter() {
		changeContactsToggle();
		List<LinphoneContact> listContact;
		isSearchMode = false;
		noSipContact.setVisibility(View.GONE);
		noContact.setVisibility(View.GONE);
		contactsList.setVisibility(View.VISIBLE);

		ContactsListAdapter adapter;
		contactsList.setFastScrollEnabled(false);
		contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);


		if (onlyDisplayLinphoneContacts) {
			listContact = ContactsManager.getInstance().getSIPContacts();
		} else {
			listContact = ContactsManager.getInstance().getContacts();
		}
		mContactAdapter = new ContactsListAdapter(mContext, listContact, this, mSelectionHelper);
		contactsList.setAdapter(mContactAdapter);
		edit.setEnabled(true);




		contactsList.setFastScrollEnabled(true);
		adapter.notifyDataSetChanged();


		if (adapter.getItemCount() > 0) {
			contactsFetchInProgress.setVisibility(View.GONE);
		}
		ContactsManager.getInstance().setLinphoneContactsPrefered(onlyDisplayLinphoneContacts);
	}

	private void changeContactsToggle() {
		if (onlyDisplayLinphoneContacts) {
			allContacts.setEnabled(true);
			allContactsSelected.setVisibility(View.INVISIBLE);
			linphoneContacts.setEnabled(false);
			linphoneContactsSelected.setVisibility(View.VISIBLE);
		} else {
			allContacts.setEnabled(false);
			allContactsSelected.setVisibility(View.VISIBLE);
			linphoneContacts.setEnabled(true);
			linphoneContactsSelected.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
		LinphoneContact contact = (LinphoneContact) adapter.getItemAtPosition(position);
		if (editOnClick) {
			editConsumed = true;
			LinphoneActivity.instance().editContact(contact, sipAddressToAdd);
		} else {
			lastKnownPosition = layoutManager.findFirstVisibleItemPosition();
//			lastKnownPosition = contactsList.getFirstVisiblePosition();
			LinphoneActivity.instance().displayContact(contact, onlyDisplayChatAddress);
		}
	}

	@Override
	public void onResume() {
		ContactsManager.addContactsListener(this);
		super.onResume();

		if (editConsumed) {
			editOnClick = false;
			sipAddressToAdd = null;
		}

		if (searchField != null && searchField.getText().toString().length() > 0) {
			if (contactsFetchInProgress != null) contactsFetchInProgress.setVisibility(View.GONE);
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS_LIST);
			LinphoneActivity.instance().hideTabBar(false);
			onlyDisplayLinphoneContacts = ContactsManager.getInstance().isLinphoneContactsPrefered();
		}
		changeContactsToggle();
		invalidate();
	}

	@Override
	public void onPause() {
		ContactsManager.removeContactsListener(this);
		super.onPause();
	}

	@Override
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != FragmentsAvailable.CONTACTS_LIST)
			return;
		ContactsListAdapter adapter = (ContactsListAdapter)contactsList.getAdapter();
		if (adapter != null) {
			contactsList.setFastScrollEnabled(false);
			if (onlyDisplayLinphoneContacts) {
				adapter.updateDataSet(ContactsManager.getInstance().getSIPContacts());
			} else {
				adapter.updateDataSet(ContactsManager.getInstance().getContacts());
			}
			contactsList.setFastScrollEnabled(true);
			contactsFetchInProgress.setVisibility(View.GONE);
		}
	}

	public void invalidate() {
		if (searchField != null && searchField.getText().toString().length() > 0) {
			searchContacts(searchField.getText().toString());
		} else {
			changeContactsAdapter();
		}
		contactsList.scrollToPosition(lastKnownPosition);
//		contactsList.setSelectionFromTop(lastKnownPosition, 0);
	}

	@Override
	public void onDeleteSelection(Object[] objectsToDelete) {

	}

	@Override
	public void onItemClicked(int position) {

	}
}
