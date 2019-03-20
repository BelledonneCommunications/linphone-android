/*
ImdnOldFragment.java
Copyright (C) 2010-2018  Belledonne Communications, Grenoble, France

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.ParticipantImdnState;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class ImdnFragment extends Fragment {
    private LayoutInflater mInflater;
    private LinearLayout mRead,
            mReadHeader,
            mDelivered,
            mDeliveredHeader,
            mSent,
            mSentHeader,
            mUndelivered,
            mUndeliveredHeader;
    private ImageView mBackButton;
    private ChatMessageViewHolder mBubble;
    private ViewGroup mContainer;

    private String mLocalSipuri, mRoomUri, mMessageId;
    private Address mLocalSipAddr, mRoomAddr;
    private ChatRoom mRoom;
    private ChatMessage mMessage;
    private ChatMessageListenerStub mListener;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mLocalSipuri = getArguments().getString("LocalSipUri");
            mLocalSipAddr = LinphoneManager.getLc().createAddress(mLocalSipuri);
            mRoomUri = getArguments().getString("RemoteSipUri");
            mRoomAddr = LinphoneManager.getLc().createAddress(mRoomUri);
            mMessageId = getArguments().getString("MessageId");
        }

        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        mRoom = core.getChatRoom(mRoomAddr, mLocalSipAddr);

        mInflater = inflater;
        mContainer = container;
        View view = mInflater.inflate(R.layout.chat_imdn, container, false);

        mBackButton = view.findViewById(R.id.back);
        mBackButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (LinphoneActivity.instance().isTablet()) {
                            LinphoneActivity.instance().goToChat(mLocalSipuri, mRoomUri, null);
                        } else {
                            LinphoneActivity.instance().onBackPressed();
                        }
                    }
                });

        mRead = view.findViewById(R.id.read_layout);
        mDelivered = view.findViewById(R.id.delivered_layout);
        mSent = view.findViewById(R.id.sent_layout);
        mUndelivered = view.findViewById(R.id.undelivered_layout);
        mReadHeader = view.findViewById(R.id.read_layout_header);
        mDeliveredHeader = view.findViewById(R.id.delivered_layout_header);
        mSentHeader = view.findViewById(R.id.sent_layout_header);
        mUndeliveredHeader = view.findViewById(R.id.undelivered_layout_header);

        mBubble = new ChatMessageViewHolder(getActivity(), view.findViewById(R.id.bubble), null);

        mMessage = mRoom.findMessage(mMessageId);
        mListener =
                new ChatMessageListenerStub() {
                    @Override
                    public void onParticipantImdnStateChanged(
                            ChatMessage msg, ParticipantImdnState state) {
                        refreshInfo();
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.MESSAGE_IMDN);
        }

        refreshInfo();
        if (mMessage != null) {
            mMessage.addListener(mListener);
        }
    }

    @Override
    public void onPause() {
        if (mMessage != null) {
            mMessage.removeListener(mListener);
        }
        super.onPause();
    }

    private void refreshInfo() {
        if (mMessage == null) {
            // TODO: error
            return;
        }
        Address remoteSender = mMessage.getFromAddress();
        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAddress(remoteSender);

        mBubble.delete.setVisibility(View.GONE);
        mBubble.eventLayout.setVisibility(View.GONE);
        mBubble.securityEventLayout.setVisibility(View.GONE);
        mBubble.rightAnchor.setVisibility(View.GONE);
        mBubble.bubbleLayout.setVisibility(View.GONE);
        mBubble.bindMessage(mMessage, contact);

        mRead.removeAllViews();
        mDelivered.removeAllViews();
        mSent.removeAllViews();
        mUndelivered.removeAllViews();

        ParticipantImdnState[] participants =
                mMessage.getParticipantsByImdnState(ChatMessage.State.Displayed);
        mReadHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        boolean first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact =
                    ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName =
                    participantContact != null
                            ? participantContact.getFullName()
                            : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time))
                    .setText(
                            LinphoneUtils.timestampToHumanDate(
                                    getActivity(),
                                    participant.getStateChangeTime(),
                                    R.string.messages_date_format));
            TextView name = v.findViewById(R.id.name);
            name.setText(participantDisplayName);
            if (participantContact != null) {
                ContactAvatar.displayAvatar(participantContact, v.findViewById(R.id.avatar_layout));
            } else {
                ContactAvatar.displayAvatar(
                        participantDisplayName, v.findViewById(R.id.avatar_layout));
            }

            final TextView sipUri = v.findViewById(R.id.sipUri);
            sipUri.setText(address.asStringUriOnly());
            if (!LinphoneActivity.instance()
                    .getResources()
                    .getBoolean(R.bool.show_sip_uri_in_chat)) {
                sipUri.setVisibility(View.GONE);
                name.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sipUri.setVisibility(
                                        sipUri.getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                            }
                        });
            }

            mRead.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.DeliveredToUser);
        mDeliveredHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact =
                    ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName =
                    participantContact != null
                            ? participantContact.getFullName()
                            : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time))
                    .setText(
                            LinphoneUtils.timestampToHumanDate(
                                    getActivity(),
                                    participant.getStateChangeTime(),
                                    R.string.messages_date_format));
            TextView name = v.findViewById(R.id.name);
            name.setText(participantDisplayName);
            if (participantContact != null) {
                ContactAvatar.displayAvatar(participantContact, v.findViewById(R.id.avatar_layout));
            } else {
                ContactAvatar.displayAvatar(
                        participantDisplayName, v.findViewById(R.id.avatar_layout));
            }

            final TextView sipUri = v.findViewById(R.id.sipUri);
            sipUri.setText(address.asStringUriOnly());
            if (!LinphoneActivity.instance()
                    .getResources()
                    .getBoolean(R.bool.show_sip_uri_in_chat)) {
                sipUri.setVisibility(View.GONE);
                name.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sipUri.setVisibility(
                                        sipUri.getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                            }
                        });
            }

            mDelivered.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.Delivered);
        mSentHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact =
                    ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName =
                    participantContact != null
                            ? participantContact.getFullName()
                            : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time))
                    .setText(
                            LinphoneUtils.timestampToHumanDate(
                                    getActivity(),
                                    participant.getStateChangeTime(),
                                    R.string.messages_date_format));
            TextView name = v.findViewById(R.id.name);
            name.setText(participantDisplayName);
            if (participantContact != null) {
                ContactAvatar.displayAvatar(participantContact, v.findViewById(R.id.avatar_layout));
            } else {
                ContactAvatar.displayAvatar(
                        participantDisplayName, v.findViewById(R.id.avatar_layout));
            }

            final TextView sipUri = v.findViewById(R.id.sipUri);
            sipUri.setText(address.asStringUriOnly());
            if (!LinphoneActivity.instance()
                    .getResources()
                    .getBoolean(R.bool.show_sip_uri_in_chat)) {
                sipUri.setVisibility(View.GONE);
                name.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sipUri.setVisibility(
                                        sipUri.getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                            }
                        });
            }

            mSent.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.NotDelivered);
        mUndeliveredHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact =
                    ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName =
                    participantContact != null
                            ? participantContact.getFullName()
                            : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            TextView name = v.findViewById(R.id.name);
            name.setText(participantDisplayName);
            if (participantContact != null) {
                ContactAvatar.displayAvatar(participantContact, v.findViewById(R.id.avatar_layout));
            } else {
                ContactAvatar.displayAvatar(
                        participantDisplayName, v.findViewById(R.id.avatar_layout));
            }

            final TextView sipUri = v.findViewById(R.id.sipUri);
            sipUri.setText(address.asStringUriOnly());
            if (!LinphoneActivity.instance()
                    .getResources()
                    .getBoolean(R.bool.show_sip_uri_in_chat)) {
                sipUri.setVisibility(View.GONE);
                name.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sipUri.setVisibility(
                                        sipUri.getVisibility() == View.VISIBLE
                                                ? View.GONE
                                                : View.VISIBLE);
                            }
                        });
            }

            mUndelivered.addView(v);
            first = false;
        }
    }
}
