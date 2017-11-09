/*
ChatRoomsAdapter.java
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
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatRoomsAdapter extends BaseAdapter {
	private class ChatRoomViewHolder {
		public TextView lastMessageView;
		public TextView date;
		public TextView displayName;
		public TextView unreadMessages;
		public CheckBox select;
		public ImageView contactPicture;

		public ChatRoomViewHolder(View view) {
			lastMessageView = view.findViewById(R.id.lastMessage);
			date = view.findViewById(R.id.date);
			displayName = view.findViewById(R.id.sipUri);
			unreadMessages = view.findViewById(R.id.unreadMessages);
			select = view.findViewById(R.id.delete_chatroom);
			contactPicture = view.findViewById(R.id.contact_picture);
		}
	}

	private Context mContext;
	ChatListFragment mFragment;
	private List<ChatRoom> mRooms;
	private LayoutInflater mLayoutInflater;
	private Bitmap mDefaultBitmap;

    public ChatRoomsAdapter(Context context, ChatListFragment fragment, LayoutInflater inflater) {
	    mContext = context;
	    mFragment = fragment;
        mLayoutInflater = inflater;
	    mRooms = new ArrayList<>();
	    mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
    }

    public void refresh() {
	    mRooms = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getChatRooms()));
	    notifyDataSetChanged();
    }

	/**
	 * List edition
	 */

	/**
	 * Adapter's methods
	 */

    @Override
    public int getCount() {
        return mRooms.size();
    }

    @Override
    public Object getItem(int position) {
        return mRooms.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
	    View view = null;
	    ChatRoomViewHolder holder = null;

	    if (convertView != null) {
		    view = convertView;
		    holder = (ChatRoomViewHolder) view.getTag();
	    } else {
		    view = mLayoutInflater.inflate(R.layout.chatlist_cell, viewGroup, false);
		    holder = new ChatRoomViewHolder(view);
		    view.setTag(holder);
	    }

	    ChatRoom chatRoom = mRooms.get(position);
	    Address remoteAddress = chatRoom.getPeerAddress();
	    Address contactAddress = remoteAddress;
	    if (chatRoom.getNbParticipants() == 1 && mContext.getString(R.string.dummy_group_chat_subject).equals(chatRoom.getSubject())) {
		    contactAddress = chatRoom.getParticipants()[0].getAddress();
	    }

	    LinphoneContact contact = null;
	    String message = "";
	    Long time;

	    int unreadMessagesCount = chatRoom.getUnreadMessagesCount();
	    ChatMessage[] history = chatRoom.getHistory(1);

	    if (history.length > 0) {
		    ChatMessage msg = history[0];
		    if (msg.getFileTransferInformation() != null || msg.getExternalBodyUrl() != null || msg.getAppdata() != null) {
			    holder.lastMessageView.setBackgroundResource(R.drawable.chat_file_message);
			    time = msg.getTime();
			    holder.date.setText(LinphoneUtils.timestampToHumanDate(mContext, time, R.string.messages_list_date_format));
			    holder.lastMessageView.setText("");
		    } else if (msg.getText() != null && msg.getText().length() > 0 ){
			    message = msg.getText();
			    holder.lastMessageView.setBackgroundResource(0);
			    time = msg.getTime();
			    holder.date.setText(LinphoneUtils.timestampToHumanDate(mContext, time, R.string.messages_list_date_format));
			    holder.lastMessageView.setText(message);
		    }
	    }

	    holder.displayName.setSelected(true); // For animation

	    if (!chatRoom.canHandleParticipants()) {
		    contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
		    if (contact != null) {
			    holder.displayName.setText(contact.getFullName());
			    LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
		    } else {
			    holder.displayName.setText(LinphoneUtils.getAddressDisplayName(contactAddress));
			    holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
		    }
	    } else if (chatRoom.getNbParticipants() == 1 && mContext.getString(R.string.dummy_group_chat_subject).equals(chatRoom.getSubject())) {
		    contact = ContactsManager.getInstance().findContactFromAddress(chatRoom.getParticipants()[0].getAddress());
		    if (contact != null) {
			    holder.displayName.setText(contact.getFullName());
			    LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
		    } else {
			    holder.displayName.setText(LinphoneUtils.getAddressDisplayName(chatRoom.getParticipants()[0].getAddress()));
			    holder.contactPicture.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
		    }
	    } else {
		    holder.displayName.setText(chatRoom.getSubject());
		    holder.contactPicture.setImageResource(R.drawable.chat_group_avatar);
	    }

	    if (unreadMessagesCount > 0) {
		    holder.unreadMessages.setVisibility(View.VISIBLE);
		    holder.unreadMessages.setText(String.valueOf(unreadMessagesCount));
		    if (unreadMessagesCount > 99) {
			    holder.unreadMessages.setTextSize(12);
		    }
		    holder.displayName.setTypeface(null, Typeface.BOLD);
	    } else {
		    holder.unreadMessages.setVisibility(View.GONE);
		    holder.displayName.setTypeface(null, Typeface.NORMAL);
	    }

	    /*if (isEditMode) {
		    holder.unreadMessages.setVisibility(View.GONE);
		    holder.select.setVisibility(View.VISIBLE);
		    holder.select.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			    @Override
			    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
				    chatList.setItemChecked(position, b);
				    if (getNbItemsChecked() == getCount()) {
					    deselectAll.setVisibility(View.VISIBLE);
					    selectAll.setVisibility(View.GONE);
					    enabledDeleteButton(true);
				    } else {
					    if (getNbItemsChecked() == 0) {
						    deselectAll.setVisibility(View.GONE);
						    selectAll.setVisibility(View.VISIBLE);
						    enabledDeleteButton(false);
					    } else {
						    deselectAll.setVisibility(View.GONE);
						    selectAll.setVisibility(View.VISIBLE);
						    enabledDeleteButton(true);
					    }
				    }
			    }
		    });
		    if (chatList.isItemChecked(position)) {
			    holder.select.setChecked(true);
		    } else {
			    holder.select.setChecked(false);
		    }
	    } else {
		    if (unreadMessagesCount > 0) {
			    holder.unreadMessages.setVisibility(View.VISIBLE);
		    }
	    }*/
	    return view;
    }
}
