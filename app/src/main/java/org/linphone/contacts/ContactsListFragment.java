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


import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.ui.SelectableHelper;

import java.util.ArrayList;
import java.util.List;

public class ContactsListFragment extends Fragment implements OnItemClickListener, ContactsUpdatedListener, ContactsListAdapter.ViewHolder.ClickListener, SelectableHelper.DeleteListener {
    private RecyclerView contactsList;
    private TextView noSipContact, noContact;
    private ImageView allContacts, linphoneContacts, newContact;
    private boolean onlyDisplayLinphoneContacts;
    private View allContactsSelected, linphoneContactsSelected;
    private int lastKnownPosition;
    private boolean editOnClick = false, editConsumed = false, onlyDisplayChatAddress = false;
    private String sipAddressToAdd, displayName = null;
    private SearchView mSearchView;
    private ProgressBar contactsFetchInProgress;
    private LinearLayoutManager layoutManager;
    private Context mContext;
    private SelectableHelper mSelectionHelper;
    private ContactsListAdapter mContactAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.contacts_list, container, false);
        mContext = getActivity().getApplicationContext();
        mSelectionHelper = new SelectableHelper(view, this);

        if (getArguments() != null) {
            editOnClick = getArguments().getBoolean("EditOnClick");
            sipAddressToAdd = getArguments().getString("SipAddress");
            if (getArguments().getString("DisplayName") != null)
                displayName = getArguments().getString("DisplayName");
            onlyDisplayChatAddress = getArguments().getBoolean("ChatAddressOnly");
        }

        noSipContact = view.findViewById(R.id.noSipContact);
        noContact = view.findViewById(R.id.noContact);
        contactsList = view.findViewById(R.id.contactsList);

        allContacts = view.findViewById(R.id.all_contacts);
        linphoneContacts = view.findViewById(R.id.linphone_contacts);
        allContactsSelected = view.findViewById(R.id.all_contacts_select);
        linphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);
        contactsFetchInProgress = view.findViewById(R.id.contactsFetchInProgress);
        newContact = view.findViewById(R.id.newContact);

        allContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlyDisplayLinphoneContacts = false;
                allContactsSelected.setVisibility(View.VISIBLE);
                allContacts.setEnabled(false);
                linphoneContacts.setEnabled(true);
                linphoneContactsSelected.setVisibility(View.INVISIBLE);
                changeContactsAdapter();
            }
        });

        linphoneContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allContactsSelected.setVisibility(View.INVISIBLE);
                linphoneContactsSelected.setVisibility(View.VISIBLE);
                linphoneContacts.setEnabled(false);
                allContacts.setEnabled(true);
                onlyDisplayLinphoneContacts = true;
                changeContactsAdapter();
            }
        });

        newContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editConsumed = true;
                if (displayName != null)
                    LinphoneActivity.instance().addContact(displayName, sipAddressToAdd);
                else
                    LinphoneActivity.instance().addContact(null, sipAddressToAdd);
            }
        });

        if (getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
            allContacts.setEnabled(false);
            linphoneContacts.setEnabled(false);
            onlyDisplayLinphoneContacts = true;
            allContacts.setOnClickListener(null);
            linphoneContacts.setOnClickListener(null);
            linphoneContacts.setVisibility(View.INVISIBLE);
            linphoneContactsSelected.setVisibility(View.INVISIBLE);
        } else {
            allContacts.setEnabled(onlyDisplayLinphoneContacts);
            linphoneContacts.setEnabled(!allContacts.isEnabled());
        }
        newContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);

        if (!ContactsManager.getInstance().contactsFetchedOnce()) {
            contactsFetchInProgress.setVisibility(View.VISIBLE);
        } else {
            if (!onlyDisplayLinphoneContacts && ContactsManager.getInstance().getContacts().size() == 0) {
                noContact.setVisibility(View.VISIBLE);
            } else if (onlyDisplayLinphoneContacts && ContactsManager.getInstance().getSIPContacts().size() == 0) {
                noSipContact.setVisibility(View.VISIBLE);
            }
        }

        mSearchView = view.findViewById(R.id.searchField);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchContacts(newText.toString());
                return true;
            }
        });

        layoutManager = new LinearLayoutManager(mContext);
        contactsList.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(contactsList.getContext(),
                layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getActivity().getResources().getDrawable(R.drawable.divider));
        contactsList.addItemDecoration(dividerItemDecoration);

        return view;
    }

    public void displayFirstContact() {
        if (contactsList != null && contactsList.getAdapter() != null && contactsList.getAdapter().getItemCount() > 0) {
            ContactsListAdapter mAdapt = (ContactsListAdapter) contactsList.getAdapter();
            LinphoneActivity.instance().displayContact((LinphoneContact) mAdapt.getItem(0), false);
        } else {
            LinphoneActivity.instance().displayEmptyFragment();
        }
    }

    private void searchContacts(String search) {
        boolean isEditionEnabled = false;
        if (search == null || search.length() == 0) {
            changeContactsAdapter();
            return;
        }
        changeContactsToggle();
        mContactAdapter.setmIsSearchMode(true);

        List<LinphoneContact> listContact;

        if (onlyDisplayLinphoneContacts) {
            listContact = ContactsManager.getInstance().getSIPContacts(search);
        } else {
            listContact = ContactsManager.getInstance().getContacts(search);
        }
        if (mContactAdapter != null && mContactAdapter.isEditionEnabled()) {
            isEditionEnabled = true;
        }

        mContactAdapter = new ContactsListAdapter(mContext, listContact, this, mSelectionHelper);

//		contactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        mSelectionHelper.setAdapter(mContactAdapter);
        if (isEditionEnabled) {
            mSelectionHelper.enterEditionMode();
        }
        contactsList.setAdapter(mContactAdapter);
    }


    private void changeContactsAdapter() {
        changeContactsToggle();
        List<LinphoneContact> listContact;

        noSipContact.setVisibility(View.GONE);
        noContact.setVisibility(View.GONE);
        contactsList.setVisibility(View.VISIBLE);
        boolean isEditionEnabled = false;
        if (mSearchView.getQuery().toString() == "") {
            if (onlyDisplayLinphoneContacts) {
                listContact = ContactsManager.getInstance().getSIPContacts();
            } else {
                listContact = ContactsManager.getInstance().getContacts();
            }
        } else {
            if (onlyDisplayLinphoneContacts) {
                listContact = ContactsManager.getInstance().getSIPContacts(mSearchView.getQuery().toString());
            } else {
                listContact = ContactsManager.getInstance().getContacts(mSearchView.getQuery().toString());
            }
        }

        if (mContactAdapter != null && mContactAdapter.isEditionEnabled()) {
            isEditionEnabled = true;
        }

        mContactAdapter = new ContactsListAdapter(mContext, listContact, this, mSelectionHelper);

        mSelectionHelper.setAdapter(mContactAdapter);

        if (isEditionEnabled) {
            mSelectionHelper.enterEditionMode();
        }
        contactsList.setAdapter(mContactAdapter);

        mContactAdapter.notifyDataSetChanged();

        if (!onlyDisplayLinphoneContacts && mContactAdapter.getItemCount() == 0) {
            noContact.setVisibility(View.VISIBLE);
        } else if (onlyDisplayLinphoneContacts && mContactAdapter.getItemCount() == 0) {
            noSipContact.setVisibility(View.VISIBLE);
        }
    }

    private void changeContactsToggle() {
        if (onlyDisplayLinphoneContacts && !getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
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
            LinphoneActivity.instance().displayContact(contact, onlyDisplayChatAddress);
        }
    }

    @Override
    public void onItemClicked(int position) {
        LinphoneContact contact = (LinphoneContact) mContactAdapter.getItem(position);

        if (mContactAdapter.isEditionEnabled()) {
            mContactAdapter.toggleSelection(position);

        } else if (editOnClick) {
            editConsumed = true;
            LinphoneActivity.instance().editContact(contact, sipAddressToAdd);
        } else {
            lastKnownPosition = layoutManager.findFirstVisibleItemPosition();
            LinphoneActivity.instance().displayContact(contact, onlyDisplayChatAddress);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mContactAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mContactAdapter.toggleSelection(position);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.addContactsListener(this);

        if (editConsumed) {
            editOnClick = false;
            sipAddressToAdd = null;
        }

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS_LIST);
            onlyDisplayLinphoneContacts = ContactsManager.getInstance().isLinphoneContactsPrefered() || getResources().getBoolean(R.bool.hide_non_linphone_contacts);
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
        if (mContactAdapter != null) {
            mContactAdapter.updateDataSet(onlyDisplayLinphoneContacts ? ContactsManager.getInstance().getSIPContacts() : ContactsManager.getInstance().getContacts());
            mContactAdapter.notifyDataSetChanged();

            if (mContactAdapter.getItemCount() > 0) {
                noContact.setVisibility(View.GONE);
                noSipContact.setVisibility(View.GONE);
            }
        }
        contactsFetchInProgress.setVisibility(View.GONE);

    }

    public void invalidate() {
        if (mSearchView != null && mSearchView.getQuery().toString().length() > 0) {
            searchContacts(mSearchView.getQuery().toString());
        } else {
            changeContactsAdapter();
        }
        contactsList.scrollToPosition(lastKnownPosition);
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        ArrayList<String> ids = new ArrayList<>();
        int size = mContactAdapter.getSelectedItemCount();
        for (int i = size - 1; i >= 0; i--) {
            LinphoneContact contact = (LinphoneContact) objectsToDelete[i];
            if (contact.isAndroidContact()) {
                contact.deleteFriend();
                ids.add(contact.getAndroidId());
            } else {
                contact.delete();
            }
        }
        ContactsManager.getInstance().deleteMultipleContactsAtOnce(ids);
    }
}
