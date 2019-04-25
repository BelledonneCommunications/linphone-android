package org.linphone.history;

/*
HistoryDetailFragment.java
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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.CallLog;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomBackend;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.FriendCapability;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class HistoryDetailFragment extends Fragment {
    private ImageView mDialBack, mChat, mAddToContacts, mGoToContact, mBack;
    private View mView;
    private TextView mContactName, mContactAddress;
    private String mSipUri, mDisplayName;
    private RelativeLayout mWaitLayout, mAvatarLayout, mChatSecured;
    private LinphoneContact mContact;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private ListView mLogsList;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSipUri = getArguments().getString("SipUri");
        mDisplayName = getArguments().getString("DisplayName");

        mView = inflater.inflate(R.layout.history_detail, container, false);

        mWaitLayout = mView.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mDialBack = mView.findViewById(R.id.call);
        mDialBack.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getInstance().newOutgoingCall(mSipUri, mDisplayName);
                    }
                });

        mBack = mView.findViewById(R.id.back);
        mBack.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((HistoryActivity) getActivity()).goBack();
                    }
                });
        mBack.setVisibility(
                getResources().getBoolean(R.bool.isTablet) ? View.INVISIBLE : View.VISIBLE);

        mChat = mView.findViewById(R.id.chat);
        mChat.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goToChat(false);
                    }
                });

        mChatSecured = mView.findViewById(R.id.chat_secured);
        mChatSecured.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goToChat(true);
                    }
                });

        if (getResources().getBoolean(R.bool.disable_chat)) {
            mChat.setVisibility(View.GONE);
            mChatSecured.setVisibility(View.GONE);
        }

        mAddToContacts = mView.findViewById(R.id.add_contact);
        mAddToContacts.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Address addr = Factory.instance().createAddress(mSipUri);
                        if (addr != null) {
                            addr.clean();
                            ((HistoryActivity) getActivity())
                                    .showContactsListForCreationOrEdition(addr);
                        }
                    }
                });

        mGoToContact = mView.findViewById(R.id.goto_contact);
        mGoToContact.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((HistoryActivity) getActivity()).showContactDetails(mContact);
                    }
                });

        mAvatarLayout = mView.findViewById(R.id.avatar_layout);
        mContactName = mView.findViewById(R.id.contact_name);
        mContactAddress = mView.findViewById(R.id.contact_address);

        mChatRoomCreationListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                        // TODO FIXME
                        /*if (newState == ChatRoom.State.Created) {
                            mWaitLayout.setVisibility(View.GONE);
                            LinphoneActivity.instance()
                                    .goToChat(
                                            cr.getLocalAddress().asStringUriOnly(),
                                            cr.getPeerAddress().asStringUriOnly(),
                                            null);
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            LinphoneActivity.instance().displayChatRoomError();
                            Log.e(
                                    "Group mChat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }*/
                    }
                };

        mLogsList = mView.findViewById(R.id.logs_list);
        displayHistory();

        return mView;
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        super.onPause();
    }

    private void displayHistory() {
        if (mSipUri != null) {
            Address lAddress = Factory.instance().createAddress(mSipUri);
            mChatSecured.setVisibility(View.GONE);

            if (lAddress != null) {
                CallLog[] logs =
                        LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                                .getCallHistoryForAddress(lAddress);
                List<CallLog> logsList = Arrays.asList(logs);
                mLogsList.setAdapter(
                        new HistoryLogAdapter(
                                getActivity(), R.layout.history_detail_cell, logsList));

                mContactAddress.setText(LinphoneUtils.getDisplayableAddress(lAddress));
                mContact = ContactsManager.getInstance().findContactFromAddress(lAddress);

                if (mContact != null) {
                    mContactName.setText(mContact.getFullName());
                    ContactAvatar.displayAvatar(mContact, mAvatarLayout);
                    mAddToContacts.setVisibility(View.GONE);
                    mGoToContact.setVisibility(View.VISIBLE);

                    if (!getResources().getBoolean(R.bool.disable_chat)
                            && mContact.hasPresenceModelForUriOrTelCapability(
                                    mSipUri, FriendCapability.LimeX3Dh)) {
                        mChatSecured.setVisibility(View.VISIBLE);
                    }
                } else {
                    mContactName.setText(
                            mDisplayName == null
                                    ? LinphoneUtils.getAddressDisplayName(mSipUri)
                                    : mDisplayName);
                    ContactAvatar.displayAvatar(
                            LinphoneUtils.getAddressDisplayName(lAddress), mAvatarLayout);
                    mAddToContacts.setVisibility(View.VISIBLE);
                    mGoToContact.setVisibility(View.GONE);
                }
            } else {
                mContactAddress.setText(mSipUri);
                mContactName.setText(
                        mDisplayName == null
                                ? LinphoneUtils.getAddressDisplayName(mSipUri)
                                : mDisplayName);
            }
        }
    }

    private void goToChat(boolean isSecured) {
        Core lc = LinphoneManager.getLc();
        Address participant = Factory.instance().createAddress(mSipUri);
        ChatRoom room =
                lc.findOneToOneChatRoom(
                        lc.getDefaultProxyConfig().getContact(), participant, isSecured);
        if (room != null) {
            // TODO FIXME
            /*LinphoneActivity.instance()
            .goToChat(
                    room.getLocalAddress().asStringUriOnly(),
                    room.getPeerAddress().asStringUriOnly(),
                    null);*/
        } else {
            ProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null
                    && lpc.getConferenceFactoryUri() != null
                    && (isSecured || !LinphonePreferences.instance().useBasicChatRoomFor1To1())) {
                mWaitLayout.setVisibility(View.VISIBLE);

                ChatRoomParams params = lc.createDefaultChatRoomParams();
                params.enableEncryption(isSecured);
                params.enableGroup(false);
                // We don't want a basic chat room
                params.setBackend(ChatRoomBackend.FlexisipChat);

                Address participants[] = new Address[1];
                participants[0] = participant;

                mChatRoom =
                        lc.createChatRoom(
                                params, getString(R.string.dummy_group_chat_subject), participants);
                if (mChatRoom != null) {
                    mChatRoom.addListener(mChatRoomCreationListener);
                } else {
                    Log.w("[History Detail Fragment] createChatRoom returned null...");
                    mWaitLayout.setVisibility(View.GONE);
                }
            } else {
                room = lc.getChatRoom(participant);
                // TODO FIXME
                /*LinphoneActivity.instance()
                .goToChat(
                        room.getLocalAddress().asStringUriOnly(),
                        room.getPeerAddress().asStringUriOnly(),
                        null);*/
            }
        }
    }
}
