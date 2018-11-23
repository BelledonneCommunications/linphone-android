/*
ChatMessageViewHolder.java
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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.Content;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    public Context mContext;
    public ChatMessage message;

    public LinearLayout eventLayout;
    public TextView eventMessage;

    public LinearLayout securityEventLayout;
    public TextView securityEventMessage;

    public View rightAnchor;
    public RelativeLayout bubbleLayout;
    public RelativeLayout background;
    public RelativeLayout avatarLayout;

    public ProgressBar sendInProgress;
    public TextView timeText;
    public ImageView outgoingImdn;
    public TextView messageText;

    public RecyclerView pictures;

    public CheckBox delete;
    private ClickListener mListener;

    public ChatMessageViewHolder(Context context, View view, ClickListener listener) {
        this(view);
        mContext = context;
        mListener = listener;
        view.setOnClickListener(this);
    }

    public ChatMessageViewHolder(View view) {
        super(view);
        eventLayout = view.findViewById(R.id.event);
        eventMessage = view.findViewById(R.id.event_text);

        securityEventLayout = view.findViewById(R.id.event);
        securityEventMessage = view.findViewById(R.id.event_text);

        rightAnchor = view.findViewById(R.id.rightAnchor);
        bubbleLayout = view.findViewById(R.id.bubble);
        background = view.findViewById(R.id.background);
        avatarLayout = view.findViewById(R.id.avatar_layout);

        sendInProgress = view.findViewById(R.id.send_in_progress);
        timeText = view.findViewById(R.id.time);
        outgoingImdn = view.findViewById(R.id.imdn);
        messageText = view.findViewById(R.id.message);

        pictures = view.findViewById(R.id.pictures);

        delete = view.findViewById(R.id.delete_message);
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);
    }

    public void bindMessage(ChatMessage message, LinphoneContact contact) {
        eventLayout.setVisibility(View.GONE);
        securityEventLayout.setVisibility(View.GONE);
        rightAnchor.setVisibility(View.VISIBLE);
        bubbleLayout.setVisibility(View.VISIBLE);
        messageText.setVisibility(View.GONE);
        timeText.setVisibility(View.VISIBLE);
        outgoingImdn.setVisibility(View.GONE);
        avatarLayout.setVisibility(View.GONE);
        pictures.setVisibility(View.GONE);
        sendInProgress.setVisibility(View.GONE);

        ChatMessage.State status = message.getState();
        Address remoteSender = message.getFromAddress();
        String displayName;
        String time = LinphoneUtils.timestampToHumanDate(mContext, message.getTime(), R.string.messages_date_format);

        if (message.isOutgoing()) {
            outgoingImdn.setVisibility(View.INVISIBLE); // For anchoring purposes

            if (status == ChatMessage.State.DeliveredToUser) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_received);
            } else if (status == ChatMessage.State.Displayed) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_read);
            } else if (status == ChatMessage.State.NotDelivered) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            } else if (status == ChatMessage.State.FileTransferError) {
                outgoingImdn.setVisibility(View.VISIBLE);
                outgoingImdn.setImageResource(R.drawable.imdn_error);
            } else if (status == ChatMessage.State.InProgress) {
                sendInProgress.setVisibility(View.VISIBLE);
            }

            timeText.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_outgoing_full);
        } else {
            rightAnchor.setVisibility(View.GONE);
            avatarLayout.setVisibility(View.VISIBLE);
            background.setBackgroundResource(R.drawable.chat_bubble_incoming_full);
        }

        if (contact == null) {
            contact = ContactsManager.getInstance().findContactFromAddress(remoteSender);
        }
        if (contact != null) {
            if (contact.getFullName() != null) {
                displayName = contact.getFullName();
            } else {
                displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            }
            ContactAvatar.displayAvatar(contact, avatarLayout);
        } else {
            displayName = LinphoneUtils.getAddressDisplayName(remoteSender);
            ContactAvatar.displayAvatar(displayName, avatarLayout);
        }

        if (message.isOutgoing()) {
            timeText.setText(time);
        } else {
            timeText.setText(time + " - " + displayName);
        }

        if (message.hasTextContent()) {
            String msg = message.getTextContent();
            Spanned text = LinphoneUtils.getTextWithHttpLinks(msg);
            messageText.setText(text);
            messageText.setMovementMethod(LinkMovementMethod.getInstance());
            messageText.setVisibility(View.VISIBLE);
        }

        List<Content> fileContents = new ArrayList<>();
        for (Content c : message.getContents()) {
            if (c.isFile() || c.isFileTransfer()) {
                fileContents.add(c);
            }
        }

        /*if (fileContents.size() > 0) {
            pictures.setVisibility(View.VISIBLE);
            mAdapter = new ChatBubbleFilesAdapter(mContext, message, fileContents);
            pictures.setAdapter(mAdapter);
            pictures.setHasFixedSize(true);
            mLayoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.HORIZONTAL);
            pictures.setLayoutManager(mLayoutManager);
        }*/
    }
}