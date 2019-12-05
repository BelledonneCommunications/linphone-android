/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.chat;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.call.views.LinphoneLinearLayoutManager;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.LinphoneContact;
import org.linphone.contacts.SearchContactViewHolder;
import org.linphone.contacts.SearchContactsAdapter;
import org.linphone.contacts.views.ContactSelectView;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomBackend;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.FriendCapability;
import org.linphone.core.ProxyConfig;
import org.linphone.core.SearchResult;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class ChatRoomCreationFragment extends Fragment
        implements View.OnClickListener,
                SearchContactViewHolder.ClickListener,
                ContactsUpdatedListener {
    private RecyclerView mContactsList;
    private LinearLayout mContactsSelectedLayout;
    private HorizontalScrollView mContactsSelectLayout;
    private ImageView mAllContactsButton;
    private ImageView mLinphoneContactsButton;
    private ImageView mNextButton;
    private boolean mOnlyDisplayLinphoneContacts;
    private View mAllContactsSelected, mLinphoneContactsSelected;
    private RelativeLayout mSearchLayout, mWaitLayout, mLinphoneContactsToggle, mAllContactsToggle;
    private SearchView mSearchField;
    private SearchContactsAdapter mSearchAdapter;
    private String mChatRoomSubject, mChatRoomAddress;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private Switch mSecurityToggle;
    private ArrayList<ContactAddress> mParticipants;
    private boolean mCreateGroupChatRoom;
    private boolean mChatRoomEncrypted;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = inflater.inflate(R.layout.chat_create, container, false);
        setRetainInstance(true);

        mParticipants = new ArrayList<>();
        mChatRoomSubject = null;
        mChatRoomAddress = null;
        mCreateGroupChatRoom = false;

        if (getArguments() != null) {
            if (getArguments().getSerializable("Participants") != null) {
                mParticipants =
                        (ArrayList<ContactAddress>) getArguments().getSerializable("Participants");
            }
            mChatRoomSubject = getArguments().getString("Subject");
            mChatRoomAddress = getArguments().getString("RemoteSipUri");
            mCreateGroupChatRoom = getArguments().getBoolean("IsGroupChatRoom", false);
            mChatRoomEncrypted = getArguments().getBoolean("Encrypted", false);
        }

        mWaitLayout = view.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mContactsList = view.findViewById(R.id.contactsList);
        mContactsSelectedLayout = view.findViewById(R.id.contactsSelected);
        mContactsSelectLayout = view.findViewById(R.id.layoutContactsSelected);

        mAllContactsButton = view.findViewById(R.id.all_contacts);
        mAllContactsButton.setOnClickListener(this);

        mLinphoneContactsButton = view.findViewById(R.id.linphone_contacts);
        mLinphoneContactsButton.setOnClickListener(this);

        mAllContactsSelected = view.findViewById(R.id.all_contacts_select);
        mLinphoneContactsSelected = view.findViewById(R.id.linphone_contacts_select);

        ImageView backButton = view.findViewById(R.id.back);
        backButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ChatActivity) getActivity()).goBack();
                    }
                });

        mNextButton = view.findViewById(R.id.next);
        mNextButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mChatRoomAddress == null && mChatRoomSubject == null) {
                            mContactsSelectedLayout.removeAllViews();
                        } else {
                            // Pop the back stack twice so we don't have in stack
                            // Group -> Creation -> Group
                            getFragmentManager().popBackStack();
                            getFragmentManager().popBackStack();
                        }
                        ((ChatActivity) getActivity())
                                .showChatRoomGroupInfo(
                                        mChatRoomAddress == null
                                                ? null
                                                : Factory.instance()
                                                        .createAddress(mChatRoomAddress),
                                        mSearchAdapter.getContactsSelectedList(),
                                        mChatRoomSubject,
                                        mSecurityToggle.isChecked());
                    }
                });
        mNextButton.setEnabled(false);
        mSearchLayout = view.findViewById(R.id.layoutSearchField);

        ProgressBar contactsFetchInProgress = view.findViewById(R.id.contactsFetchInProgress);
        contactsFetchInProgress.setVisibility(View.GONE);

        mSearchAdapter = new SearchContactsAdapter(this, !mCreateGroupChatRoom, mChatRoomEncrypted);

        mSearchField = view.findViewById(R.id.searchField);
        mSearchField.setOnQueryTextListener(
                new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        mSearchAdapter.searchContacts(newText);
                        return true;
                    }
                });

        mLinphoneContactsToggle = view.findViewById(R.id.layout_linphone_contacts);
        mAllContactsToggle = view.findViewById(R.id.layout_all_contacts);

        mSecurityToggle = view.findViewById(R.id.security_toogle);
        mSecurityToggle.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        setSecurityEnabled(isChecked);
                    }
                });
        ImageView securityToggleOn = view.findViewById(R.id.security_toogle_on);
        ImageView securityToggleOff = view.findViewById(R.id.security_toogle_off);
        securityToggleOn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSecurityEnabled(true);
                    }
                });
        securityToggleOff.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setSecurityEnabled(false);
                    }
                });

        mSecurityToggle.setChecked(mChatRoomEncrypted);
        mSearchAdapter.setSecurityEnabled(mChatRoomEncrypted);
        ProxyConfig lpc = LinphoneManager.getCore().getDefaultProxyConfig();
        if ((mChatRoomSubject != null && mChatRoomAddress != null)
                || (lpc == null || lpc.getConferenceFactoryUri() == null)) {
            mSecurityToggle.setVisibility(View.GONE);
            securityToggleOn.setVisibility(View.GONE);
            securityToggleOff.setVisibility(View.GONE);
        }

        if (getResources().getBoolean(R.bool.force_end_to_end_encryption_in_chat)) {
            mSecurityToggle.setChecked(true);
            mSearchAdapter.setSecurityEnabled(true);
            mSecurityToggle.setVisibility(View.GONE);
            securityToggleOn.setVisibility(View.GONE);
            securityToggleOff.setVisibility(View.GONE);
        }

        LinearLayoutManager layoutManager =
                new LinphoneLinearLayoutManager(getActivity().getApplicationContext());

        mContactsList.setAdapter(mSearchAdapter);

        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mContactsList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(
                getActivity()
                        .getApplicationContext()
                        .getResources()
                        .getDrawable(R.drawable.divider));
        mContactsList.addItemDecoration(dividerItemDecoration);

        mContactsList.setLayoutManager(layoutManager);

        mOnlyDisplayLinphoneContacts =
                ContactsManager.getInstance().isLinphoneContactsPrefered()
                        || getResources().getBoolean(R.bool.hide_non_linphone_contacts);

        if (savedInstanceState != null) {
            if (mParticipants.isEmpty()
                    && savedInstanceState.getStringArrayList("Participants") != null) {
                mContactsSelectedLayout.removeAllViews();
                // We need to get all contacts not only sip
                mParticipants =
                        (ArrayList<ContactAddress>)
                                savedInstanceState.getSerializable("Participants");
            }
            mOnlyDisplayLinphoneContacts =
                    savedInstanceState.getBoolean("onlySipContact", mOnlyDisplayLinphoneContacts);
        }

        mChatRoomCreationListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                        if (newState == ChatRoom.State.Created) {
                            mWaitLayout.setVisibility(View.GONE);
                            // Pop back stack so back button takes to the chat rooms list
                            getFragmentManager().popBackStack();
                            ((ChatActivity) getActivity())
                                    .showChatRoom(
                                            mChatRoom.getLocalAddress(),
                                            mChatRoom.getPeerAddress());
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            ((ChatActivity) getActivity()).displayChatRoomError();
                            Log.e(
                                    "[Chat Room Creation] Group chat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.getInstance().addContactsListener(this);

        updateLayout();

        InputMethodManager inputMethodManager =
                (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        ContactsManager.getInstance().removeContactsListener(this);
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchAdapter.getContactsSelectedList().size() > 0) {
            outState.putSerializable("Participants", mSearchAdapter.getContactsSelectedList());
        }
        outState.putBoolean("onlySipContact", mOnlyDisplayLinphoneContacts);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.all_contacts) {
            mOnlyDisplayLinphoneContacts = false;
            mSearchAdapter.setOnlySipContact(false);
            mAllContactsSelected.setVisibility(View.VISIBLE);
            mAllContactsButton.setEnabled(false);
            mLinphoneContactsButton.setEnabled(true);
            mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
            updateList();
            resetAndResearch();
        } else if (id == R.id.linphone_contacts) {
            mSearchAdapter.setOnlySipContact(true);
            mLinphoneContactsSelected.setVisibility(View.VISIBLE);
            mLinphoneContactsButton.setEnabled(false);
            mOnlyDisplayLinphoneContacts = true;
            mAllContactsButton.setEnabled(true);
            mAllContactsSelected.setVisibility(View.INVISIBLE);
            updateList();
            resetAndResearch();
        } else if (id == R.id.contactChatDelete) {
            ContactAddress ca = (ContactAddress) view.getTag();
            addOrRemoveContactFromSelection(ca);
        }
    }

    @Override
    public void onItemClicked(int position) {
        SearchResult searchResult = mSearchAdapter.getContacts().get(position);
        Core core = LinphoneManager.getCore();
        ProxyConfig lpc = core.getDefaultProxyConfig();
        boolean createEncryptedChatRoom = mSecurityToggle.isChecked();

        if (createEncryptedChatRoom && !searchResult.hasCapability(FriendCapability.LimeX3Dh)) {
            Log.w(
                    "[Chat Room Creation] Contact "
                            + searchResult.getFriend()
                            + " doesn't have LIME X3DH capability !");
            return;
        } else if (mCreateGroupChatRoom
                && !searchResult.hasCapability(FriendCapability.GroupChat)) {
            Log.w(
                    "[Chat Room Creation] Contact "
                            + searchResult.getFriend()
                            + " doesn't have group chat capability !");
            return;
        }

        if (lpc == null || lpc.getConferenceFactoryUri() == null || !mCreateGroupChatRoom) {
            Address address = searchResult.getAddress();
            if (address == null) {
                Log.w(
                        "[Chat Room Creation] Using search result without an address, trying with phone number...");
                address = core.interpretUrl(searchResult.getPhoneNumber());
            }
            if (address == null) {
                Log.e("[Chat Room Creation] Can't create a chat room without a valid address !");
                return;
            }
            if (lpc != null && lpc.getIdentityAddress().weakEqual(address)) {
                Log.e("[Chat Room Creation] Can't create a 1-to-1 chat room with myself !");
                return;
            }

            if (createEncryptedChatRoom && lpc != null && lpc.getConferenceFactoryUri() != null) {
                mChatRoom = core.findOneToOneChatRoom(lpc.getIdentityAddress(), address, true);
                if (mChatRoom != null) {
                    ((ChatActivity) getActivity())
                            .showChatRoom(mChatRoom.getLocalAddress(), mChatRoom.getPeerAddress());
                } else {
                    ChatRoomParams params = core.createDefaultChatRoomParams();
                    // This will set the backend to FlexisipChat automatically
                    params.enableEncryption(true);
                    params.enableGroup(false);

                    Address[] participants = new Address[1];
                    participants[0] = address;

                    mChatRoom =
                            core.createChatRoom(
                                    params,
                                    getString(R.string.dummy_group_chat_subject),
                                    participants);
                    if (mChatRoom != null) {
                        mChatRoom.addListener(mChatRoomCreationListener);
                    } else {
                        Log.w("[Chat Room Creation Fragment] createChatRoom returned null...");
                        mWaitLayout.setVisibility(View.GONE);
                    }
                }
            } else {
                if (lpc != null
                        && lpc.getConferenceFactoryUri() != null
                        && !LinphonePreferences.instance().useBasicChatRoomFor1To1()) {
                    mChatRoom = core.findOneToOneChatRoom(lpc.getIdentityAddress(), address, false);
                    if (mChatRoom == null) {
                        mWaitLayout.setVisibility(View.VISIBLE);

                        ChatRoomParams params = core.createDefaultChatRoomParams();
                        params.enableEncryption(false);
                        params.enableGroup(false);
                        // We don't want a basic chat room
                        params.setBackend(ChatRoomBackend.FlexisipChat);

                        Address[] participants = new Address[1];
                        participants[0] = address;

                        mChatRoom =
                                core.createChatRoom(
                                        params,
                                        getString(R.string.dummy_group_chat_subject),
                                        participants);
                        if (mChatRoom != null) {
                            mChatRoom.addListener(mChatRoomCreationListener);
                        } else {
                            Log.w("[Chat Room Creation Fragment] createChatRoom returned null...");
                            mWaitLayout.setVisibility(View.GONE);
                        }
                    } else {
                        // Pop back stack so back button takes to the chat rooms list
                        getFragmentManager().popBackStack();
                        ((ChatActivity) getActivity())
                                .showChatRoom(
                                        mChatRoom.getLocalAddress(), mChatRoom.getPeerAddress());
                    }
                } else {
                    ChatRoom chatRoom = null;
                    if (lpc != null) chatRoom = core.getChatRoom(address, lpc.getIdentityAddress());
                    else chatRoom = core.getChatRoom(address);

                    if (chatRoom != null) {
                        // Pop back stack so back button takes to the chat rooms list
                        getFragmentManager().popBackStack();
                        ((ChatActivity) getActivity())
                                .showChatRoom(
                                        chatRoom.getLocalAddress(), chatRoom.getPeerAddress());
                    }
                }
            }
        } else {
            LinphoneContact c =
                    searchResult.getFriend() != null
                            ? (LinphoneContact) searchResult.getFriend().getUserData()
                            : null;
            if (c == null) {
                c = ContactsManager.getInstance().findContactFromAddress(searchResult.getAddress());
                if (c == null) {
                    c =
                            ContactsManager.getInstance()
                                    .findContactFromPhoneNumber(searchResult.getPhoneNumber());
                }
            }
            addOrRemoveContactFromSelection(
                    new ContactAddress(
                            c,
                            searchResult.getAddress().asStringUriOnly(),
                            searchResult.getPhoneNumber()));
        }
    }

    @Override
    public void onContactsUpdated() {
        updateList();
    }

    private void updateLayout() {
        if (!mParticipants.isEmpty()) {
            mSearchAdapter.setContactsSelectedList(mParticipants);
            updateList();
            updateListSelected();
        }

        mSearchAdapter.setOnlySipContact(mOnlyDisplayLinphoneContacts);
        updateList();

        displayChatCreation();
    }

    private void setSecurityEnabled(boolean enabled) {
        mChatRoomEncrypted = enabled;
        mSecurityToggle.setChecked(mChatRoomEncrypted);
        mSearchAdapter.setSecurityEnabled(mChatRoomEncrypted);

        if (enabled) {
            // Remove all contacts added before LIME switch was set
            // and that can stay because they don't have the capability
            mContactsSelectedLayout.removeAllViews();
            List<ContactAddress> toToggle = new ArrayList<>();
            for (ContactAddress ca : mSearchAdapter.getContactsSelectedList()) {
                // If the ContactAddress doesn't have a contact keep it anyway
                if (ca.getContact() != null && !ca.hasCapability(FriendCapability.LimeX3Dh)) {
                    toToggle.add(ca);
                } else {
                    if (ca.getView() != null) {
                        mContactsSelectedLayout.addView(ca.getView());
                    }
                }
            }
            for (ContactAddress ca : toToggle) {
                mSearchAdapter.toggleContactSelection(ca);
            }
            mContactsSelectedLayout.invalidate();
        }
    }

    private void displayChatCreation() {
        mNextButton.setVisibility(View.VISIBLE);
        mNextButton.setEnabled(mSearchAdapter.getContactsSelectedList().size() > 0);

        mContactsList.setVisibility(View.VISIBLE);
        mSearchLayout.setVisibility(View.VISIBLE);

        if (mCreateGroupChatRoom) {
            mLinphoneContactsToggle.setVisibility(View.GONE);
            mAllContactsToggle.setVisibility(View.GONE);
            mContactsSelectLayout.setVisibility(View.VISIBLE);
            mNextButton.setVisibility(View.VISIBLE);
        } else {
            mLinphoneContactsToggle.setVisibility(View.VISIBLE);
            mAllContactsToggle.setVisibility(View.VISIBLE);
            mContactsSelectLayout.setVisibility(View.GONE);
            mNextButton.setVisibility(View.GONE);
        }

        if (getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
            mLinphoneContactsToggle.setVisibility(View.GONE);
            mLinphoneContactsButton.setVisibility(View.INVISIBLE);

            mAllContactsButton.setEnabled(false);
            mLinphoneContactsButton.setEnabled(false);

            mOnlyDisplayLinphoneContacts = true;

            mAllContactsButton.setOnClickListener(null);
            mLinphoneContactsButton.setOnClickListener(null);

            mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
            mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
        } else {
            mAllContactsButton.setVisibility(View.VISIBLE);
            mLinphoneContactsButton.setVisibility(View.VISIBLE);

            if (mOnlyDisplayLinphoneContacts) {
                mAllContactsSelected.setVisibility(View.INVISIBLE);
                mLinphoneContactsSelected.setVisibility(View.VISIBLE);
            } else {
                mAllContactsSelected.setVisibility(View.VISIBLE);
                mLinphoneContactsSelected.setVisibility(View.INVISIBLE);
            }

            mAllContactsButton.setEnabled(mOnlyDisplayLinphoneContacts);
            mLinphoneContactsButton.setEnabled(!mAllContactsButton.isEnabled());
        }

        mContactsSelectedLayout.removeAllViews();
        if (mSearchAdapter.getContactsSelectedList().size() > 0) {
            for (ContactAddress ca : mSearchAdapter.getContactsSelectedList()) {
                addSelectedContactAddress(ca);
            }
        }
    }

    private void updateList() {
        mSearchAdapter.searchContacts(mSearchField.getQuery().toString());
        mSearchAdapter.notifyDataSetChanged();
    }

    private void updateListSelected() {
        if (mSearchAdapter.getContactsSelectedList().size() > 0) {
            mContactsSelectLayout.invalidate();
            mNextButton.setEnabled(true);
        } else {
            mNextButton.setEnabled(false);
        }
    }

    private void resetAndResearch() {
        ContactsManager.getInstance().getMagicSearch().resetSearchCache();
        mSearchAdapter.searchContacts(mSearchField.getQuery().toString());
    }

    private void addSelectedContactAddress(ContactAddress ca) {
        View viewContact =
                LayoutInflater.from(getActivity()).inflate(R.layout.contact_selected, null);
        if (ca.getContact() != null) {
            String name =
                    (ca.getContact().getFullName() != null
                                    && !ca.getContact().getFullName().isEmpty())
                            ? ca.getContact().getFullName()
                            : (ca.getDisplayName() != null)
                                    ? ca.getDisplayName()
                                    : (ca.getUsername() != null) ? ca.getUsername() : "";
            ((TextView) viewContact.findViewById(R.id.sipUri)).setText(name);
        } else {
            ((TextView) viewContact.findViewById(R.id.sipUri))
                    .setText(ca.getAddressAsDisplayableString());
        }
        View removeContact = viewContact.findViewById(R.id.contactChatDelete);
        removeContact.setTag(ca);
        removeContact.setOnClickListener(this);
        viewContact.setOnClickListener(this);
        ca.setView(viewContact);
        mContactsSelectedLayout.addView(viewContact);
        mContactsSelectedLayout.invalidate();
    }

    private void updateContactsClick(ContactAddress ca) {
        boolean isSelected = mSearchAdapter.toggleContactSelection(ca);
        if (isSelected) {
            ContactSelectView csv = new ContactSelectView(getActivity());
            csv.setListener(this);
            csv.setContactName(ca);
            addSelectedContactAddress(ca);
        } else {
            mContactsSelectedLayout.removeAllViews();
            for (ContactAddress contactAddress : mSearchAdapter.getContactsSelectedList()) {
                if (contactAddress.getView() != null)
                    mContactsSelectedLayout.addView(contactAddress.getView());
            }
        }
        mContactsSelectedLayout.invalidate();
    }

    private void addOrRemoveContactFromSelection(ContactAddress ca) {
        updateContactsClick(ca);
        mSearchAdapter.notifyDataSetChanged();
        updateListSelected();
    }
}
