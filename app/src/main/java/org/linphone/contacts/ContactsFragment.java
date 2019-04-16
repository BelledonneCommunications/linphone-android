package org.linphone.contacts;

/*
ContactsFragment.java
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.utils.SelectableHelper;

public class ContactsFragment extends Fragment
        implements OnItemClickListener,
                ContactsUpdatedListener,
                ContactViewHolder.ClickListener,
                SelectableHelper.DeleteListener {
    private RecyclerView mContactsList;
    private TextView mNoSipContact, mNoContact;
    private ImageView mAllContacts, mLinphoneContacts, mNewContact;
    private boolean mOnlyDisplayLinphoneContacts;
    private View mAllContactsSelected, mLinphoneContactsSelected;
    private int mLastKnownPosition;
    private boolean mEditOnClick = false, mEditConsumed = false, mOnlyDisplayChatAddress = false;
    private String mSipAddressToAdd, mDisplayName = null;
    private SearchView mSearchView;
    private ProgressBar mContactsFetchInProgress;
    private LinearLayoutManager mLayoutManager;
    private Context mContext;
    private SelectableHelper mSelectionHelper;
    private ContactsAdapter mContactAdapter;
    private SwipeRefreshLayout mContactsRefresher;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.contacts_list, container, false);
        mContext = getActivity().getApplicationContext();
        mSelectionHelper = new SelectableHelper(view, this);
        mSelectionHelper.setDialogMessage(R.string.delete_contacts_text);

        if (getArguments() != null) {
            mEditOnClick = getArguments().getBoolean("EditOnClick");
            mSipAddressToAdd = getArguments().getString("SipAddress");
            if (getArguments().getString("DisplayName") != null) {
                mDisplayName = getArguments().getString("DisplayName");
            }
            mOnlyDisplayChatAddress = getArguments().getBoolean("ChatAddressOnly");

            if (getArguments().getBoolean("EditOnClick")) {
                Toast.makeText(
                                LinphoneActivity.instance(),
                                R.string.toast_choose_contact_for_edition,
                                Toast.LENGTH_LONG)
                        .show();
            }
            getArguments().clear();
        }

        mNoSipContact = view.findViewById(R.id.noSipContact);
        mNoContact = view.findViewById(R.id.noContact);
        mContactsList = view.findViewById(R.id.contactsList);

        mAllContacts = view.findViewById(R.id.all_contacts);
        mLinphoneContacts = view.findViewById(R.id.linphone_contacts);
        mAllContactsSelected = view.findViewById(R.id.all_contacts_select);
        mLinphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);
        mContactsFetchInProgress = view.findViewById(R.id.contactsFetchInProgress);
        mNewContact = view.findViewById(R.id.newContact);
        mContactsRefresher = view.findViewById(R.id.contactsListRefresher);

        mContactsRefresher.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        ContactsManager.getInstance().fetchContactsAsync();
                    }
                });

        mAllContacts.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mOnlyDisplayLinphoneContacts = false;
                        mAllContactsSelected.setVisibility(View.VISIBLE);
                        mAllContacts.setEnabled(false);
                        mLinphoneContacts.setEnabled(true);
                        mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
                        changeContactsAdapter();
                    }
                });

        mLinphoneContacts.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAllContactsSelected.setVisibility(View.INVISIBLE);
                        mLinphoneContactsSelected.setVisibility(View.VISIBLE);
                        mLinphoneContacts.setEnabled(false);
                        mAllContacts.setEnabled(true);
                        mOnlyDisplayLinphoneContacts = true;
                        changeContactsAdapter();
                    }
                });

        mNewContact.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mEditConsumed = true;
                        ContactsManager.getInstance()
                                .createContact(getActivity(), mDisplayName, mSipAddressToAdd);
                    }
                });

        if (getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
            mAllContacts.setEnabled(false);
            mLinphoneContacts.setEnabled(false);
            mOnlyDisplayLinphoneContacts = true;
            mAllContacts.setOnClickListener(null);
            mLinphoneContacts.setOnClickListener(null);
            mLinphoneContacts.setVisibility(View.INVISIBLE);
            mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
        } else {
            mAllContacts.setEnabled(mOnlyDisplayLinphoneContacts);
            mLinphoneContacts.setEnabled(!mAllContacts.isEnabled());
        }
        mNewContact.setEnabled(LinphoneManager.getLc().getCallsNb() == 0);

        if (!ContactsManager.getInstance().contactsFetchedOnce()) {
            if (ContactsManager.getInstance().hasReadContactsAccess()) {
                mContactsFetchInProgress.setVisibility(View.VISIBLE);
            } else {
                LinphoneActivity.instance().checkAndRequestReadContactsPermission();
            }
        } else {
            if (!mOnlyDisplayLinphoneContacts
                    && ContactsManager.getInstance().getContacts().size() == 0) {
                mNoContact.setVisibility(View.VISIBLE);
            } else if (mOnlyDisplayLinphoneContacts
                    && ContactsManager.getInstance().getSIPContacts().size() == 0) {
                mNoSipContact.setVisibility(View.VISIBLE);
            }
        }

        mSearchView = view.findViewById(R.id.searchField);
        mSearchView.setOnQueryTextListener(
                new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        searchContacts(newText);
                        return true;
                    }
                });

        mLayoutManager = new LinearLayoutManager(mContext);
        mContactsList.setLayoutManager(mLayoutManager);

        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mContactsList.getContext(), mLayoutManager.getOrientation());
        dividerItemDecoration.setDrawable(
                getActivity().getResources().getDrawable(R.drawable.divider));
        mContactsList.addItemDecoration(dividerItemDecoration);

        return view;
    }

    public void displayFirstContact() {
        if (mContactsList != null
                && mContactsList.getAdapter() != null
                && mContactsList.getAdapter().getItemCount() > 0) {
            ContactsAdapter mAdapt = (ContactsAdapter) mContactsList.getAdapter();
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

        List<LinphoneContact> listContact;

        if (mOnlyDisplayLinphoneContacts) {
            listContact = ContactsManager.getInstance().getSIPContacts(search);
        } else {
            listContact = ContactsManager.getInstance().getContacts(search);
        }
        if (mContactAdapter != null && mContactAdapter.isEditionEnabled()) {
            isEditionEnabled = true;
        }

        mContactAdapter = new ContactsAdapter(mContext, listContact, this, mSelectionHelper);
        mContactAdapter.setIsSearchMode(true);

        //		mContactsList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        mSelectionHelper.setAdapter(mContactAdapter);
        if (isEditionEnabled) {
            mSelectionHelper.enterEditionMode();
        }
        mContactsList.setAdapter(mContactAdapter);
    }

    private void changeContactsAdapter() {
        changeContactsToggle();
        List<LinphoneContact> listContact;

        mNoSipContact.setVisibility(View.GONE);
        mNoContact.setVisibility(View.GONE);
        mContactsList.setVisibility(View.VISIBLE);
        boolean isEditionEnabled = false;
        String query = mSearchView.getQuery().toString();
        if (query.equals("")) {
            if (mOnlyDisplayLinphoneContacts) {
                listContact = ContactsManager.getInstance().getSIPContacts();
            } else {
                listContact = ContactsManager.getInstance().getContacts();
            }
        } else {
            if (mOnlyDisplayLinphoneContacts) {
                listContact = ContactsManager.getInstance().getSIPContacts(query);
            } else {
                listContact = ContactsManager.getInstance().getContacts(query);
            }
        }

        if (mContactAdapter != null && mContactAdapter.isEditionEnabled()) {
            isEditionEnabled = true;
        }

        mContactAdapter = new ContactsAdapter(mContext, listContact, this, mSelectionHelper);

        mSelectionHelper.setAdapter(mContactAdapter);

        if (isEditionEnabled) {
            mSelectionHelper.enterEditionMode();
        }
        mContactsList.setAdapter(mContactAdapter);

        mContactAdapter.notifyDataSetChanged();

        if (!mOnlyDisplayLinphoneContacts && mContactAdapter.getItemCount() == 0) {
            mNoContact.setVisibility(View.VISIBLE);
        } else if (mOnlyDisplayLinphoneContacts && mContactAdapter.getItemCount() == 0) {
            mNoSipContact.setVisibility(View.VISIBLE);
        }
    }

    private void changeContactsToggle() {
        if (mOnlyDisplayLinphoneContacts
                && !getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
            mAllContacts.setEnabled(true);
            mAllContactsSelected.setVisibility(View.INVISIBLE);
            mLinphoneContacts.setEnabled(false);
            mLinphoneContactsSelected.setVisibility(View.VISIBLE);
        } else {
            mAllContacts.setEnabled(false);
            mAllContactsSelected.setVisibility(View.VISIBLE);
            mLinphoneContacts.setEnabled(true);
            mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        LinphoneContact contact = (LinphoneContact) adapter.getItemAtPosition(position);
        if (mEditOnClick) {
            mEditConsumed = true;
            ContactsManager.getInstance().editContact(getActivity(), contact, mSipAddressToAdd);
        } else {
            mLastKnownPosition = mLayoutManager.findFirstVisibleItemPosition();
            LinphoneActivity.instance().displayContact(contact, mOnlyDisplayChatAddress);
        }
    }

    @Override
    public void onItemClicked(int position) {
        LinphoneContact contact = (LinphoneContact) mContactAdapter.getItem(position);

        if (mContactAdapter.isEditionEnabled()) {
            mContactAdapter.toggleSelection(position);

        } else if (mEditOnClick) {
            mEditConsumed = true;
            ContactsManager.getInstance().editContact(getActivity(), contact, mSipAddressToAdd);
        } else {
            mLastKnownPosition = mLayoutManager.findFirstVisibleItemPosition();
            LinphoneActivity.instance().displayContact(contact, mOnlyDisplayChatAddress);
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
        ContactsManager.getInstance().addContactsListener(this);

        if (mEditConsumed) {
            mEditOnClick = false;
            mSipAddressToAdd = null;
        }

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.CONTACTS_LIST);
            mOnlyDisplayLinphoneContacts =
                    ContactsManager.getInstance().isLinphoneContactsPrefered()
                            || getResources().getBoolean(R.bool.hide_non_linphone_contacts);
        }
        changeContactsToggle();
        invalidate();
    }

    @Override
    public void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onContactsUpdated() {
        if (!LinphoneActivity.isInstanciated()
                || (LinphoneActivity.instance().getCurrentFragment()
                                != FragmentsAvailable.CONTACTS_LIST
                        && !LinphoneActivity.instance().isTablet())) return;
        if (mContactAdapter != null) {
            mContactAdapter.updateDataSet(
                    mOnlyDisplayLinphoneContacts
                            ? ContactsManager.getInstance().getSIPContacts()
                            : ContactsManager.getInstance().getContacts());
            mContactAdapter.notifyDataSetChanged();

            if (mContactAdapter.getItemCount() > 0) {
                mNoContact.setVisibility(View.GONE);
                mNoSipContact.setVisibility(View.GONE);
            }
        }
        mContactsFetchInProgress.setVisibility(View.GONE);
        mContactsRefresher.setRefreshing(false);
    }

    private void invalidate() {
        if (mSearchView != null && mSearchView.getQuery().toString().length() > 0) {
            searchContacts(mSearchView.getQuery().toString());
        } else {
            changeContactsAdapter();
        }
        mContactsList.scrollToPosition(mLastKnownPosition);
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
