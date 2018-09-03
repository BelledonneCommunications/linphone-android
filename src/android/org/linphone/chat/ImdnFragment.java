/*
ImdnFragment.java
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
import android.support.annotation.Nullable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.ParticipantImdnState;

public class ImdnFragment extends Fragment {
    private LayoutInflater mInflater;
    private LinearLayout mRead, mReadHeader, mDelivered, mDeliveredHeader, mSent, mSentHeader, mUndelivered, mUndeliveredHeader;
    private ImageView mBackButton;
    private ChatBubbleViewHolder mBubble;
    private ViewGroup mContainer;

    private String mRoomUri, mMessageId;
    private Address mRoomAddr;
    private ChatRoom mRoom;
    private ChatMessage mMessage;
    private ChatMessageListenerStub mListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mRoomUri = getArguments().getString("SipUri");
            mRoomAddr = LinphoneManager.getLc().createAddress(mRoomUri);
            mMessageId = getArguments().getString("MessageId");
        }
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        Address proxyConfigContact = core.getDefaultProxyConfig().getContact();
        if (proxyConfigContact != null) {
            mRoom = core.findOneToOneChatRoom(proxyConfigContact, mRoomAddr);
        }
        if (mRoom == null) {
            mRoom = core.getChatRoomFromUri(mRoomAddr.asStringUriOnly());
        }

        mInflater = inflater;
        mContainer = container;
        View view = mInflater.inflate(R.layout.chat_imdn, container, false);

        mBackButton = view.findViewById(R.id.back);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (LinphoneActivity.instance().isTablet()) {
                    LinphoneActivity.instance().goToChat(mRoomUri, null);
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

        mBubble = new ChatBubbleViewHolder(view.findViewById(R.id.bubble));
        mBubble.eventLayout.setVisibility(View.GONE);
        mBubble.bubbleLayout.setVisibility(View.VISIBLE);
        mBubble.delete.setVisibility(View.GONE);
        mBubble.messageText.setVisibility(View.GONE);
        mBubble.messageImage.setVisibility(View.GONE);
        mBubble.fileTransferLayout.setVisibility(View.GONE);
        mBubble.fileName.setVisibility(View.GONE);
        mBubble.openFileButton.setVisibility(View.GONE);
        mBubble.messageStatus.setVisibility(View.INVISIBLE);
        mBubble.messageSendingInProgress.setVisibility(View.GONE);
        mBubble.imdmLayout.setVisibility(View.INVISIBLE);
        mBubble.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());

        mMessage = mRoom.findMessage(mMessageId);
        mListener = new ChatMessageListenerStub() {
            @Override
            public void onParticipantImdnStateChanged(ChatMessage msg, ParticipantImdnState state) {
                refreshInfo();
            }
        };
        mMessage.setListener(mListener);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        layoutParams.setMargins(100, 10, 10, 10);
        if (mMessage.isOutgoing()) {
            mBubble.background.setBackgroundResource(R.drawable.resizable_chat_bubble_outgoing);
            Compatibility.setTextAppearance(mBubble.contactName, getActivity(), R.style.font3);
            Compatibility.setTextAppearance(mBubble.fileTransferAction, getActivity(), R.style.font15);
            mBubble.fileTransferAction.setBackgroundResource(R.drawable.resizable_confirm_delete_button);
            mBubble.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask_outgoing);
        } else {
            mBubble.background.setBackgroundResource(R.drawable.resizable_chat_bubble_incoming);
            Compatibility.setTextAppearance(mBubble.contactName, getActivity(), R.style.font9);
            Compatibility.setTextAppearance(mBubble.fileTransferAction, getActivity(), R.style.font8);
            mBubble.fileTransferAction.setBackgroundResource(R.drawable.resizable_assistant_button);
            mBubble.contactPictureMask.setImageResource(R.drawable.avatar_chat_mask);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshInfo();
    }

    private void refreshInfo() {
        Address remoteSender = mMessage.getFromAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(remoteSender);
        String displayName;

        if (contact != null) {
            if (contact.getFullName() != null) {
                displayName = contact.getFullName();
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            }

            mBubble.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            if (contact.hasPhoto()) {
                LinphoneUtils.setThumbnailPictureFromUri(getActivity(), mBubble.contactPicture, contact.getThumbnailUri());
            }
        } else {
            displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            mBubble.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
        }
        mBubble.contactName.setText(LinphoneUtils.timestampToHumanDate(getActivity(), mMessage.getTime(), R.string.messages_date_format) + " - " + displayName);

        if (mMessage.hasTextContent()) {
            String msg = mMessage.getTextContent();
            Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
            mBubble.messageText.setText(text);
            mBubble.messageText.setMovementMethod(LinkMovementMethod.getInstance());
            mBubble.messageText.setVisibility(View.VISIBLE);
        }

        String appData = mMessage.getAppdata();
        if (appData != null) { // Something to display
            mBubble.fileName.setVisibility(View.VISIBLE);
            mBubble.fileName.setText(LinphoneUtils.getNameFromFilePath(appData));
            // We purposely chose not to display the image
        }

        mRead.removeAllViews();
        mDelivered.removeAllViews();
        mSent.removeAllViews();
        mUndelivered.removeAllViews();

        ParticipantImdnState[] participants = mMessage.getParticipantsByImdnState(ChatMessage.State.Displayed);
        mReadHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        boolean first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact = ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName = participantContact != null ? participantContact.getFullName() : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time)).setText(LinphoneUtils.timestampToHumanDate(getActivity(), participant.getStateChangeTime(), R.string.messages_date_format));
            ((TextView) v.findViewById(R.id.name)).setText(participantDisplayName);
            if (participantContact != null && participantContact.hasPhoto()) {
                LinphoneUtils.setThumbnailPictureFromUri(getActivity(), ((ImageView) v.findViewById(R.id.contact_picture)), participantContact.getThumbnailUri());
            } else {
                ((ImageView) v.findViewById(R.id.contact_picture)).setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }

            mRead.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.DeliveredToUser);
        mDeliveredHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact = ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName = participantContact != null ? participantContact.getFullName() : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time)).setText(LinphoneUtils.timestampToHumanDate(getActivity(), participant.getStateChangeTime(), R.string.messages_date_format));
            ((TextView) v.findViewById(R.id.name)).setText(participantDisplayName);
            if (participantContact != null && participantContact.hasPhoto()) {
                LinphoneUtils.setThumbnailPictureFromUri(getActivity(), ((ImageView) v.findViewById(R.id.contact_picture)), participantContact.getThumbnailUri());
            } else {
                ((ImageView) v.findViewById(R.id.contact_picture)).setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }

            mDelivered.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.Delivered);
        mSentHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact = ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName = participantContact != null ? participantContact.getFullName() : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.time)).setText(LinphoneUtils.timestampToHumanDate(getActivity(), participant.getStateChangeTime(), R.string.messages_date_format));
            ((TextView) v.findViewById(R.id.name)).setText(participantDisplayName);
            if (participantContact != null && participantContact.hasPhoto()) {
                LinphoneUtils.setThumbnailPictureFromUri(getActivity(), ((ImageView) v.findViewById(R.id.contact_picture)), participantContact.getThumbnailUri());
            } else {
                ((ImageView) v.findViewById(R.id.contact_picture)).setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }

            mSent.addView(v);
            first = false;
        }

        participants = mMessage.getParticipantsByImdnState(ChatMessage.State.NotDelivered);
        mUndeliveredHeader.setVisibility(participants.length == 0 ? View.GONE : View.VISIBLE);
        first = true;
        for (ParticipantImdnState participant : participants) {
            Address address = participant.getParticipant().getAddress();

            LinphoneContact participantContact = ContactsManager.getInstance().findContactFromAddress(address);
            String participantDisplayName = participantContact != null ? participantContact.getFullName() : LinphoneUtils.getAddressDisplayName(address);

            View v = mInflater.inflate(R.layout.chat_imdn_cell, mContainer, false);
            v.findViewById(R.id.separator).setVisibility(first ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.name)).setText(participantDisplayName);
            if (participantContact != null && participantContact.hasPhoto()) {
                LinphoneUtils.setThumbnailPictureFromUri(getActivity(), ((ImageView) v.findViewById(R.id.contact_picture)), participantContact.getThumbnailUri());
            } else {
                ((ImageView) v.findViewById(R.id.contact_picture)).setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }

            mUndelivered.addView(v);
            first = false;
        }
    }
}
