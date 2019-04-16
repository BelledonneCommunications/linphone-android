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
import android.view.View;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.Content;
import org.linphone.core.Participant;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.ContactAvatar;

public class ChatRoomViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener {
    public final TextView lastMessageView;
    public final TextView date;
    public final TextView displayName;
    public final TextView unreadMessages;
    public final CheckBox delete;
    public final RelativeLayout avatarLayout;

    private final Context mContext;
    private final ClickListener mListener;

    public ChatRoomViewHolder(Context context, View itemView, ClickListener listener) {
        super(itemView);

        mContext = context;
        lastMessageView = itemView.findViewById(R.id.lastMessage);
        date = itemView.findViewById(R.id.date);
        displayName = itemView.findViewById(R.id.sipUri);
        unreadMessages = itemView.findViewById(R.id.unreadMessages);
        delete = itemView.findViewById(R.id.delete_chatroom);
        avatarLayout = itemView.findViewById(R.id.avatar_layout);
        mListener = listener;

        itemView.setOnClickListener(this);
        itemView.setOnLongClickListener(this);
    }

    public void bindChatRoom(ChatRoom room) {
        ChatMessage lastMessage = room.getLastMessageInHistory();

        if (lastMessage != null) {
            StringBuilder messageContent = new StringBuilder();
            for (Content c : lastMessage.getContents()) {
                if (c.isFile() || c.isFileTransfer()) {
                    messageContent.append(c.getName()).append(" ");
                } else if (c.isText()) {
                    messageContent.insert(0, c.getStringBuffer() + " ");
                }
            }
            lastMessageView.setText(getSender(room) + messageContent);
            date.setText(
                    LinphoneUtils.timestampToHumanDate(
                            mContext,
                            room.getLastUpdateTime(),
                            R.string.messages_list_date_format));
        } else {
            date.setText("");
            lastMessageView.setText("");
        }

        displayName.setText(getContact(room));
        unreadMessages.setText(String.valueOf(room.getUnreadMessagesCount()));
        getAvatar(room);
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
            LinphoneContact contact =
                    ContactsManager.getInstance()
                            .findContactFromAddress(
                                    mRoom.getLastMessageInHistory().getFromAddress());
            if (contact != null) {
                return (contact.getFullName() + mContext.getString(R.string.separator));
            }
            return (LinphoneUtils.getAddressDisplayName(
                            mRoom.getLastMessageInHistory().getFromAddress())
                    + mContext.getString(R.string.separator));
        }
        return null;
    }

    public String getContact(ChatRoom mRoom) {
        Address contactAddress = mRoom.getPeerAddress();
        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
                && mRoom.getParticipants().length > 0) {
            contactAddress = mRoom.getParticipants()[0].getAddress();
        }

        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            LinphoneContact contact;
            contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
            if (contact != null) {
                return contact.getFullName();
            }
            return LinphoneUtils.getAddressDisplayName(contactAddress);
        }
        return mRoom.getSubject();
    }

    public void getAvatar(ChatRoom mRoom) {
        if (mRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            LinphoneContact contact = null;
            if (mRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())) {
                contact =
                        ContactsManager.getInstance()
                                .findContactFromAddress(mRoom.getPeerAddress());
            } else {
                Participant[] participants = mRoom.getParticipants();
                if (participants != null && participants.length > 0) {
                    contact =
                            ContactsManager.getInstance()
                                    .findContactFromAddress(participants[0].getAddress());
                }
            }
            if (contact != null) {
                if (mRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
                    ContactAvatar.displayAvatar(contact, mRoom.getSecurityLevel(), avatarLayout);
                } else {
                    ContactAvatar.displayAvatar(contact, avatarLayout);
                }
            } else {
                Address remoteAddr = null;
                if (mRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
                    Participant[] participants = mRoom.getParticipants();
                    if (participants.length > 0) {
                        remoteAddr = participants[0].getAddress();
                    } else {
                        // TODO: error
                    }
                } else {
                    remoteAddr = mRoom.getPeerAddress();
                }

                String username = LinphoneUtils.getAddressDisplayName(remoteAddr);
                if (mRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
                    ContactAvatar.displayAvatar(username, mRoom.getSecurityLevel(), avatarLayout);
                } else {
                    ContactAvatar.displayAvatar(username, avatarLayout);
                }
            }
        } else {
            if (mRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
                ContactAvatar.displayGroupChatAvatar(mRoom.getSecurityLevel(), avatarLayout);
            } else {
                ContactAvatar.displayGroupChatAvatar(avatarLayout);
            }
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);

        boolean onItemLongClicked(int position);
    }
}
