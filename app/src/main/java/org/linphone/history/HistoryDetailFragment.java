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
package org.linphone.history;

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
import org.linphone.activities.MainActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.contacts.views.ContactAvatar;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomBackend;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.ChatRoomParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.FriendCapability;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class HistoryDetailFragment extends Fragment {
    private ImageView mAddToContacts;
    private ImageView mGoToContact;
    private TextView mContactName, mContactAddress;
    private String mSipUri, mDisplayName;
    private RelativeLayout mWaitLayout, mAvatarLayout, mChatSecured;
    private LinphoneContact mContact;
    private ChatRoom mChatRoom;
    private ChatRoomListenerStub mChatRoomCreationListener;
    private ListView mLogsList;
    private CoreListenerStub mListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSipUri = getArguments().getString("SipUri");
        mDisplayName = getArguments().getString("DisplayName");

        View view = inflater.inflate(R.layout.history_detail, container, false);

        mWaitLayout = view.findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        ImageView dialBack = view.findViewById(R.id.call);
        dialBack.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity) getActivity()).newOutgoingCall(mSipUri);
                    }
                });

        ImageView back = view.findViewById(R.id.back);
        back.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((HistoryActivity) getActivity()).goBack();
                    }
                });
        back.setVisibility(
                getResources().getBoolean(R.bool.isTablet) ? View.INVISIBLE : View.VISIBLE);

        ImageView chat = view.findViewById(R.id.chat);
        chat.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goToChat(false);
                    }
                });

        mChatSecured = view.findViewById(R.id.chat_secured);
        mChatSecured.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goToChat(true);
                    }
                });

        if (getResources().getBoolean(R.bool.force_end_to_end_encryption_in_chat)) {
            chat.setVisibility(View.GONE);
        }
        if (getResources().getBoolean(R.bool.disable_chat)) {
            chat.setVisibility(View.GONE);
            mChatSecured.setVisibility(View.GONE);
        }

        mAddToContacts = view.findViewById(R.id.add_contact);
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

        mGoToContact = view.findViewById(R.id.goto_contact);
        mGoToContact.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((HistoryActivity) getActivity()).showContactDetails(mContact);
                    }
                });

        mAvatarLayout = view.findViewById(R.id.avatar_layout);
        mContactName = view.findViewById(R.id.contact_name);
        mContactAddress = view.findViewById(R.id.contact_address);

        mChatRoomCreationListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom cr, ChatRoom.State newState) {
                        if (newState == ChatRoom.State.Created) {
                            mWaitLayout.setVisibility(View.GONE);
                            ((HistoryActivity) getActivity())
                                    .showChatRoom(cr.getLocalAddress(), cr.getPeerAddress());
                        } else if (newState == ChatRoom.State.CreationFailed) {
                            mWaitLayout.setVisibility(View.GONE);
                            ((HistoryActivity) getActivity()).displayChatRoomError();
                            Log.e(
                                    "[History Detail Fragment] Group mChat room for address "
                                            + cr.getPeerAddress()
                                            + " has failed !");
                        }
                    }
                };

        mLogsList = view.findViewById(R.id.logs_list);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Error) {
                            displayHistory();
                        }
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        LinphoneManager.getCore().addListener(mListener);
        displayHistory();
    }

    @Override
    public void onPause() {
        if (mChatRoom != null) {
            mChatRoom.removeListener(mChatRoomCreationListener);
        }
        LinphoneManager.getCore().removeListener(mListener);

        super.onPause();
    }

    private void displayHistory() {
        if (mSipUri != null) {
            Address address = Factory.instance().createAddress(mSipUri);
            mChatSecured.setVisibility(View.GONE);

            Core core = LinphoneManager.getCore();
            if (address != null && core != null) {
                address.clean();
                ProxyConfig proxyConfig = core.getDefaultProxyConfig();
                CallLog[] logs;
                if (proxyConfig != null) {
                    logs = core.getCallHistory(address, proxyConfig.getIdentityAddress());
                } else {
                    logs = core.getCallHistoryForAddress(address);
                }
                List<CallLog> logsList = Arrays.asList(logs);
                mLogsList.setAdapter(
                        new HistoryLogAdapter(
                                getActivity(), R.layout.history_detail_cell, logsList));

                mContactAddress.setText(LinphoneUtils.getDisplayableAddress(address));
                mContact = ContactsManager.getInstance().findContactFromAddress(address);

                if (mDisplayName == null) {
                    mDisplayName = LinphoneUtils.getAddressDisplayName(address);
                }

                if (mContact != null) {
                    mContactName.setText(mContact.getFullName());
                    ContactAvatar.displayAvatar(mContact, mAvatarLayout);
                    mAddToContacts.setVisibility(View.GONE);
                    mGoToContact.setVisibility(View.VISIBLE);

                    if (!getResources().getBoolean(R.bool.disable_chat)
                            && mContact.hasPresenceModelForUriOrTelCapability(
                                    address.asStringUriOnly(), FriendCapability.LimeX3Dh)) {
                        mChatSecured.setVisibility(View.VISIBLE);
                    }
                } else {
                    mContactName.setText(mDisplayName);
                    ContactAvatar.displayAvatar(mDisplayName, mAvatarLayout);
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
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        Address participant = Factory.instance().createAddress(mSipUri);
        if (participant == null) {
            Log.e("[History Detail] Couldn't parse ", mSipUri);
            return;
        }
        ProxyConfig defaultProxyConfig = core.getDefaultProxyConfig();

        if (defaultProxyConfig != null) {
            ChatRoom room =
                    core.findOneToOneChatRoom(
                            defaultProxyConfig.getIdentityAddress(), participant, isSecured);
            if (room != null) {
                ((HistoryActivity) getActivity())
                        .showChatRoom(room.getLocalAddress(), room.getPeerAddress());
            } else {
                if (defaultProxyConfig.getConferenceFactoryUri() != null
                        && (isSecured
                                || !LinphonePreferences.instance().useBasicChatRoomFor1To1())) {
                    mWaitLayout.setVisibility(View.VISIBLE);

                    ChatRoomParams params = core.createDefaultChatRoomParams();
                    params.enableEncryption(isSecured);
                    params.enableGroup(false);
                    // We don't want a basic chat room,
                    // so if isSecured is false we have to set this manually
                    params.setBackend(ChatRoomBackend.FlexisipChat);

                    Address[] participants = new Address[1];
                    participants[0] = participant;

                    mChatRoom =
                            core.createChatRoom(
                                    params,
                                    getString(R.string.dummy_group_chat_subject),
                                    participants);
                    if (mChatRoom != null) {
                        mChatRoom.addListener(mChatRoomCreationListener);
                    } else {
                        Log.w("[History Detail Fragment] createChatRoom returned null...");
                        mWaitLayout.setVisibility(View.GONE);
                    }
                } else {
                    room = core.getChatRoom(participant);
                    if (room != null) {
                        ((HistoryActivity) getActivity())
                                .showChatRoom(room.getLocalAddress(), room.getPeerAddress());
                    }
                }
            }
        } else {
            if (isSecured) {
                Log.e(
                        "[History Detail Fragment] Can't create a secured chat room without proxy config");
                return;
            }

            ChatRoom room = core.getChatRoom(participant);
            if (room != null) {
                ((HistoryActivity) getActivity())
                        .showChatRoom(room.getLocalAddress(), room.getPeerAddress());
            }
        }
    }
}
