/*
ChatRoomViewHolder.java
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.Content;

public class ChatRoomViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
    private Bitmap mDefaultBitmap;
    private Bitmap mDefaultGroupBitmap;

    public TextView lastMessageSenderView;
    public TextView lastMessageView;
    public TextView date;
    public TextView displayName;
    public TextView unreadMessages;
    public CheckBox delete;
    public ImageView contactPicture;
    public ImageView lastMessageFileTransfer;
    public Context mContext;
    public ChatRoom mRoom;
    private ClickListener mListener;

    public ChatRoomViewHolder(Context context, View itemView, ClickListener listener) {
        super(itemView);

        mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
        mDefaultGroupBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.chat_group_avatar);

        mContext = context;
        lastMessageSenderView = itemView.findViewById(R.id.lastMessageSender);
        lastMessageView = itemView.findViewById(R.id.lastMessage);
        date = itemView.findViewById(R.id.date);
        displayName = itemView.findViewById(R.id.sipUri);
        unreadMessages = itemView.findViewById(R.id.unreadMessages);
        delete = itemView.findViewById(R.id.delete_chatroom);
        contactPicture = itemView.findViewById(R.id.contact_picture);
        lastMessageFileTransfer = itemView.findViewById(R.id.lastMessageFileTransfer);
        mListener = listener;

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
    }

    public void bindChatRoom(ChatRoom room) {
        mRoom = room;
        ChatMessage lastMessage = mRoom.getLastMessageInHistory();
        lastMessageView.setVisibility(View.GONE);
        lastMessageFileTransfer.setVisibility(View.GONE);

        if (lastMessage != null) {
            String text = lastMessage.getTextContent();
            if (text != null && text.length() > 0) {
                lastMessageView.setVisibility(View.VISIBLE);
                lastMessageView.setText(text);
            }
            date.setText(LinphoneUtils.timestampToHumanDate(mContext, mRoom.getLastUpdateTime(), R.string.messages_list_date_format));
            for (Content c : lastMessage.getContents()) {
                if (c.isFile() || c.isFileTransfer()) {
                    lastMessageFileTransfer.setVisibility(View.VISIBLE);
                }
            }
        }

        lastMessageSenderView.setText(getSender(mRoom));
        displayName.setText(getContact(mRoom));
        unreadMessages.setText(String.valueOf(LinphoneManager.getInstance().getUnreadCountForChatRoom(mRoom)));
        getAvatar(mRoom);
    }

    public void onClick(View v) {
        if (mListener != null) {
            mListener.onItemClicked(getAdapterPosition());
        }
    }

    public boolean onLongClick(View v) {
        if (mListener != null) {
            return mListener.onItemLongClicked(getAdapterPosition());
        }
        return false;
    }

    public String getSender(ChatRoom mRoom) {
        if (mRoom.getLastMessageInHistory() != null) {
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getLastMessageInHistory().getFromAddress());
            if (contact != null) {
                return (contact.getFullName() + mContext.getString(R.string.separator));
            }
            return (LinphoneUtils.getAddressDisplayName(mRoom.getLastMessageInHistory().getFromAddress()) + mContext.getString(R.string.separator));
        }
        return null;
    }

    public String getContact(ChatRoom mRoom) {
        Address contactAddress = mRoom.getPeerAddress();
        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && mRoom.getParticipants().length > 0) {
            contactAddress = mRoom.getParticipants()[0].getAddress();
        }

        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            LinphoneContact contact;
            if (mRoom.getParticipants().length > 0) {
                contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getParticipants()[0].getAddress());
                if (contact != null) {
                    return (contact.getFullName());
                }
                return (LinphoneUtils.getAddressDisplayName(mRoom.getParticipants()[0].getAddress()));
            } else {
                contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
                if (contact != null) {
                    return (contact.getFullName());
                }
                return (LinphoneUtils.getAddressDisplayName(contactAddress));
            }
        }
        return (mRoom.getSubject());
    }

    public void getAvatar(ChatRoom mRoom) {
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress());
        if (contact != null) {
            LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), contactPicture, ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress()).getThumbnailUri());
        } else {
            if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()))
                contactPicture.setImageBitmap(mDefaultBitmap);
            else
                contactPicture.setImageBitmap(mDefaultGroupBitmap);
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);

        boolean onItemLongClicked(int position);
    }
}