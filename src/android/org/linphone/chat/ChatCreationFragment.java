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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.contacts.SearchContactsListAdapter;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.ui.ContactSelectView;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class ChatCreationFragment extends Fragment implements View.OnClickListener, SearchContactsListAdapter.ViewHolder.ClickListener, ContactsUpdatedListener {
    private RecyclerView mContactsList;
    private LinearLayout mContactsSelectedLayout;
    private HorizontalScrollView mContactsSelectLayout;
    private ArrayList<ContactAddress> mContactsSelected;
    private ImageView mAllContactsButton, mLinphoneContactsButton, mClearSearchFieldButton, mBackButton, mNextButton;
    private boolean mOnlyDisplayLinphoneContacts;
    private View mAllContactsSelected, mLinphoneContactsSelected;
    private RelativeLayout mSearchLayout, mWaitLayout;
    private EditText mSearchField;
    private ProgressBar mContactsFetchInProgress;
    private SearchContactsListAdapter mSearchAdapter;
    private String mChatRoomSubject, mChatRoomAddress;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private Bundle mShareInfos;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = inflater.inflate(R.layout.chat_create, container, false);

        mContactsSelected = new ArrayList<>();
        mChatRoomSubject = null;
        mChatRoomAddress = null;
        if (getArguments() != null) {
            if (getArguments().getSerializable("selectedContacts") != null) {
                mContactsSelected = (ArrayList<ContactAddress>) getArguments().getSerializable("selectedContacts");
            }
            mChatRoomSubject = getArguments().getString("subject");
            mChatRoomAddress = getArguments().getString("groupChatRoomAddress");
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

        mBackButton = view.findViewById(R.id.back);
        mBackButton.setOnClickListener(this);

        mNextButton = view.findViewById(R.id.next);
        mNextButton.setOnClickListener(this);
        mNextButton.setEnabled(false);
        mSearchLayout = view.findViewById(R.id.layoutSearchField);

        mClearSearchFieldButton = view.findViewById(R.id.clearSearchField);
        mClearSearchFieldButton.setOnClickListener(this);

        mContactsFetchInProgress = view.findViewById(R.id.contactsFetchInProgress);
        mContactsFetchInProgress.setVisibility(View.VISIBLE);

        mSearchAdapter = new SearchContactsListAdapter(null, mContactsFetchInProgress, this);

        mSearchField = view.findViewById(R.id.searchField);
        mSearchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (before > count) {
                    ContactsManager.getInstance().getMagicSearch().resetSearchCache();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                mSearchAdapter.searchContacts(mSearchField.getText().toString(), mContactsList);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity().getApplicationContext());

        mContactsList.setAdapter(mSearchAdapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(mContactsList.getContext(),
                layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getActivity().getApplicationContext().getResources().getDrawable(R.drawable.divider));
        mContactsList.addItemDecoration(dividerItemDecoration);

        mContactsList.setLayoutManager(layoutManager);

        if (savedInstanceState != null && savedInstanceState.getStringArrayList("mContactsSelected") != null) {
            mContactsSelectedLayout.removeAllViews();
            // We need to get all contacts not only sip
            for (String uri : savedInstanceState.getStringArrayList("mContactsSelected")) {
                for (ContactAddress ca : mSearchAdapter.getContactsList()) {
                    if (ca.getAddressAsDisplayableString().compareTo(uri) == 0) {
                        ca.setView(null);
                        addSelectedContactAddress(ca);
                        break;
                    }
                }
            }
            updateList();
            updateListSelected();
        }

        mOnlyDisplayLinphoneContacts = ContactsManager.getInstance().getSIPContacts().size() > 0 ? true : false;
        if (savedInstanceState != null) {
            mOnlyDisplayLinphoneContacts = savedInstanceState.getBoolean("onlySipContact", true);
        }
        mSearchAdapter.setOnlySipContact(mOnlyDisplayLinphoneContacts);
        updateList();

        displayChatCreation();

        mChatRoomCreationListener = new ChatRoomListenerStub() {
            @Override
            public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                if (newState == ChatRoom.State.Created) {
                    mWaitLayout.setVisibility(View.GONE);
                    LinphoneActivity.instance().goToChat(cr.getPeerAddress().asStringUriOnly(), mShareInfos);
                } else if (newState == ChatRoom.State.CreationFailed) {
                    mWaitLayout.setVisibility(View.GONE);
                    LinphoneActivity.instance().displayChatRoomError();
                    Log.e("Group chat room for address " + cr.getPeerAddress() + " has failed !");
                }
            }
        };

        if (getArguments() != null) {
            String fileSharedUri = getArguments().getString("fileSharedUri");
            String messageDraft = getArguments().getString("messageDraft");

            if (fileSharedUri != null || messageDraft != null)
                mShareInfos = new Bundle();

            if (fileSharedUri != null) {
                LinphoneActivity.instance().checkAndRequestPermissionsToSendImage();
                mShareInfos.putString("fileSharedUri", fileSharedUri);
            }

            if (messageDraft != null)
                mShareInfos.putString("messageDraft", messageDraft);
        }

        return view;
    }

    @Override
    public void onResume() {
        ContactsManager.addContactsListener(this);
        super.onResume();

        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
        if (getActivity().getCurrentFocus() != null) {
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        ContactsManager.removeContactsListener(this);
        super.onPause();
    }

    private void displayChatCreation() {
        mNextButton.setVisibility(View.VISIBLE);
        mNextButton.setEnabled(mContactsSelected.size() > 0);

        mContactsList.setVisibility(View.VISIBLE);
        mSearchLayout.setVisibility(View.VISIBLE);

        if (getResources().getBoolean(R.bool.hide_non_linphone_contacts)) {
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
        if (mContactsSelected.size() > 0) {
            mSearchAdapter.setContactsSelectedList(mContactsSelected);
            for (ContactAddress ca : mContactsSelected) {
                addSelectedContactAddress(ca);
            }
        }
    }

    private void updateList() {
        mSearchAdapter.searchContacts(mSearchField.getText().toString(), mContactsList);
        mSearchAdapter.notifyDataSetChanged();
    }

    private void updateListSelected() {
        if (mContactsSelected.size() > 0) {
            mContactsSelectLayout.invalidate();
            mNextButton.setEnabled(true);
        } else {
            mNextButton.setEnabled(false);
        }
    }

    private int getIndexOfCa(ContactAddress ca, List<ContactAddress> caList) {
        for (int i = 0; i < caList.size(); i++) {
            if (ca.getAddress() != null && ca.getAddress().getUsername() != null) {
                if (caList.get(i).getAddressAsDisplayableString().compareTo(ca.getAddressAsDisplayableString()) == 0)
                    return i;
            } else if (ca.getPhoneNumber() != null && caList.get(i).getPhoneNumber() != null) {
                if (ca.getPhoneNumber().compareTo(caList.get(i).getPhoneNumber()) == 0)
                    return i;
            }
        }
        return -1;
    }

    private void resetAndResearch() {
        ContactsManager.getInstance().getMagicSearch().resetSearchCache();
        mSearchAdapter.searchContacts(mSearchField.getText().toString(), mContactsList);
    }

    private void addSelectedContactAddress(ContactAddress ca) {
        View viewContact = LayoutInflater.from(LinphoneActivity.instance()).inflate(R.layout.contact_selected, null);
        if (ca.getContact() != null) {
            String name = (ca.getContact().getFullName() != null && !ca.getContact().getFullName().isEmpty()) ?
                    ca.getContact().getFullName() : (ca.getDisplayName() != null) ?
                    ca.getDisplayName() : (ca.getUsername() != null) ?
                    ca.getUsername() : "";
            ((TextView) viewContact.findViewById(R.id.sipUri)).setText(name);
        } else {
            ((TextView) viewContact.findViewById(R.id.sipUri)).setText(ca.getAddressAsDisplayableString());
        }
        View removeContact = viewContact.findViewById(R.id.contactChatDelete);
        removeContact.setTag(ca);
        removeContact.setOnClickListener(this);
        viewContact.setOnClickListener(this);
        ca.setView(viewContact);
        mContactsSelectedLayout.addView(viewContact);
        mContactsSelectedLayout.invalidate();
    }

    private void updateContactsClick(ContactAddress ca, List<ContactAddress> caSelectedList) {
        ca.setSelect((getIndexOfCa(ca, caSelectedList) == -1));
        if (ca.isSelect()) {
            ContactSelectView csv = new ContactSelectView(LinphoneActivity.instance());
            csv.setListener(this);
            csv.setContactName(ca);
            mContactsSelected.add(ca);
            addSelectedContactAddress(ca);
        } else {
            mContactsSelected.remove(getIndexOfCa(ca, mContactsSelected));
            mContactsSelectedLayout.removeAllViews();
            for (ContactAddress contactAddress : mContactsSelected) {
                if (contactAddress.getView() != null)
                    mContactsSelectedLayout.addView(contactAddress.getView());
            }
        }
        mSearchAdapter.setContactsSelectedList(mContactsSelected);
        mContactsSelectedLayout.invalidate();

    }

    private void removeContactFromSelection(ContactAddress ca) {
        updateContactsClick(ca, mSearchAdapter.getContactsSelectedList());
        mSearchAdapter.notifyDataSetChanged();
        updateListSelected();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mContactsSelected != null && mContactsSelected.size() > 0) {
            ArrayList<String> listUri = new ArrayList<String>();
            for (ContactAddress ca : mContactsSelected) {
                listUri.add(ca.getAddressAsDisplayableString());
            }
            outState.putStringArrayList("mContactsSelected", listUri);
        }

        outState.putBoolean("onlySipContact", mOnlyDisplayLinphoneContacts);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.all_contacts) {
            mSearchAdapter.setOnlySipContact(mOnlyDisplayLinphoneContacts = false);
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
            mAllContactsButton.setEnabled(mOnlyDisplayLinphoneContacts = true);
            mAllContactsSelected.setVisibility(View.INVISIBLE);
            updateList();
            resetAndResearch();
        } else if (id == R.id.back) {
            if (LinphoneActivity.instance().isTablet()) {
                LinphoneActivity.instance().goToChatList();
            } else {
                mContactsSelectedLayout.removeAllViews();
                LinphoneActivity.instance().popBackStack();
            }
        } else if (id == R.id.next) {
            if (mChatRoomAddress == null && mChatRoomSubject == null) {
                if (mContactsSelected.size() == 1) {
                    mContactsSelectedLayout.removeAllViews();
                    mWaitLayout.setVisibility(View.VISIBLE);
                    Core lc = LinphoneManager.getLc();
                    Address participant = mContactsSelected.get(0).getAddress();
                    ChatRoom chatRoom = lc.findOneToOneChatRoom(lc.getDefaultProxyConfig().getContact(), participant);
                    if (chatRoom == null) {
                        ProxyConfig lpc = lc.getDefaultProxyConfig();
                        if (lpc != null && lpc.getConferenceFactoryUri() != null && !LinphonePreferences.instance().useBasicChatRoomFor1To1()) {
                            mChatRoom = lc.createClientGroupChatRoom(getString(R.string.dummy_group_chat_subject), true);
                            mChatRoom.addListener(mChatRoomCreationListener);
                            mChatRoom.addParticipant(participant);
                        } else {
                            chatRoom = lc.getChatRoom(participant);
                            LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asStringUriOnly(), mShareInfos);
                        }
                    } else {
                        LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asStringUriOnly(), mShareInfos);
                    }
                } else {
                    mContactsSelectedLayout.removeAllViews();
                    LinphoneActivity.instance().goToChatGroupInfos(null, mContactsSelected, null, true, false, mShareInfos);
                }
            } else {
                LinphoneActivity.instance().goToChatGroupInfos(mChatRoomAddress, mContactsSelected, mChatRoomSubject, true, true, mShareInfos);
            }
        } else if (id == R.id.clearSearchField) {
            mSearchField.setText("");
            mSearchAdapter.searchContacts("", mContactsList);
        } else if (id == R.id.contactChatDelete) {
            ContactAddress ca = (ContactAddress) view.getTag();
            removeContactFromSelection(ca);
        }
    }

    @Override
    public void onItemClicked(int position) {
        ContactAddress ca = mSearchAdapter.getContacts().get(position);
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        ProxyConfig lpc = lc.getDefaultProxyConfig();
        if (lpc == null || lpc.getConferenceFactoryUri() == null) {
            ChatRoom chatRoom = lc.getChatRoom(ca.getAddress());
            LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asStringUriOnly(), mShareInfos);
        } else {
            removeContactFromSelection(ca);
        }
    }

    @Override
    public void onContactsUpdated() {
        updateList();
    }
}
