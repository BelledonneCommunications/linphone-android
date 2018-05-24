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
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.EventLog;
import org.linphone.ui.ListSelectionAdapter;
import org.linphone.ui.ListSelectionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatRoomsAdapter extends ListSelectionAdapter {

	private class ChatRoomViewHolder {
		public TextView lastMessageSenderView;
		public TextView lastMessageView;
		public TextView date;
		public TextView displayName;
		public TextView unreadMessages;
		public CheckBox delete;
		public ImageView contactPicture;

		public ChatRoomViewHolder(View view) {
			lastMessageSenderView = view.findViewById(R.id.lastMessageSender);
			lastMessageView = view.findViewById(R.id.lastMessage);
			date = view.findViewById(R.id.date);
			displayName = view.findViewById(R.id.sipUri);
			unreadMessages = view.findViewById(R.id.unreadMessages);
			delete = view.findViewById(R.id.delete_chatroom);
			contactPicture = view.findViewById(R.id.contact_picture);
		}
	}

	private Context mContext;
	private List<ChatRoom> mRooms;
	private LayoutInflater mLayoutInflater;
	private Bitmap mDefaultBitmap, mDefaultGroupBitmap;
	private ChatRoomListenerStub mListener;

	public ChatRoomsAdapter(Context context, ListSelectionHelper helper, LayoutInflater inflater) {
		super(helper);
		mContext = context;
		mLayoutInflater = inflater;
		mRooms = new ArrayList<>();
		mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
		mDefaultGroupBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.chat_group_avatar);

		mListener = new ChatRoomListenerStub() {
			@Override
			public void onSubjectChanged(ChatRoom cr, EventLog eventLog) {
				ChatRoomViewHolder holder = (ChatRoomViewHolder) cr.getUserData();
				holder.displayName.setText(cr.getSubject());
			}
		};
	}

	public void refresh() {
		mRooms = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getChatRooms()));
		Collections.sort(mRooms, new Comparator<ChatRoom>() {
			public int compare(ChatRoom cr1, ChatRoom cr2) {
				long timeDiff = cr1.getLastUpdateTime() - cr2.getLastUpdateTime();
				if (timeDiff > 0) return -1;
				else if (timeDiff == 0) return 0;
				return 1;
			}
		});
		notifyDataSetChanged();
	}

	public void clear() {
		for (ChatRoom room : mRooms) {
			room.removeListener(mListener);
		}
		mRooms.clear();
	}

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
	public View getView(final int position, View convertView, ViewGroup viewGroup) {
		View view;
		ChatRoomViewHolder holder;

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

		if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && chatRoom.getParticipants().length > 0) {
			contactAddress = chatRoom.getParticipants()[0].getAddress();
		}

		if (chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt()) && chatRoom.getState() == ChatRoom.State.Created) { // Only set for state Created otherwise it will conflict with removal listener
			chatRoom.addListener(mListener);
			chatRoom.setUserData(holder);
		}

		int unreadMessagesCount = LinphoneManager.getInstance().getUnreadCountForChatRoom(chatRoom);
		ChatMessage lastMessage = chatRoom.getLastMessageInHistory();
		holder.lastMessageView.setText("");
		holder.lastMessageSenderView.setText("");
		holder.date.setText(LinphoneUtils.timestampToHumanDate(mContext, chatRoom.getLastUpdateTime(), R.string.messages_list_date_format));

		if (lastMessage != null) {
			if (lastMessage.getFileTransferInformation() != null || lastMessage.getExternalBodyUrl() != null || lastMessage.getAppdata() != null) {
				holder.lastMessageView.setBackgroundResource(R.drawable.chat_file_message);
			} else if (lastMessage.getTextContent() != null && lastMessage.getTextContent().length() > 0) {
				holder.lastMessageView.setBackgroundResource(0);
				holder.lastMessageView.setText(lastMessage.getTextContent());
			}

			Address lastMessageSenderAddress = lastMessage.getFromAddress();
			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(lastMessageSenderAddress);
			if (contact != null) {
				holder.lastMessageSenderView.setText(contact.getFullName() + mContext.getString(R.string.separator));
			} else {
				holder.lastMessageSenderView.setText(LinphoneUtils.getAddressDisplayName(lastMessageSenderAddress) +  mContext.getString(R.string.separator));
			}
		}

		holder.displayName.setSelected(true); // For animation
		holder.contactPicture.setImageBitmap(mDefaultBitmap);

		if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
			LinphoneContact contact;
			if (chatRoom.getParticipants().length > 0) {
				contact = ContactsManager.getInstance().findContactFromAddress(chatRoom.getParticipants()[0].getAddress());
				if (contact != null) {
					holder.displayName.setText(contact.getFullName());
					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
				} else {
					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(chatRoom.getParticipants()[0].getAddress()));
				}
			} else {
				contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
				if (contact != null) {
					holder.displayName.setText(contact.getFullName());
					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
				} else {
					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(contactAddress));
				}
			}
		} else {
			holder.displayName.setText(chatRoom.getSubject());
			holder.contactPicture.setImageBitmap(mDefaultGroupBitmap);
		}

		if (unreadMessagesCount > 0) {
			holder.unreadMessages.setVisibility(View.VISIBLE);
			holder.unreadMessages.setText(String.valueOf(unreadMessagesCount));
			if (unreadMessagesCount > 99) {
				holder.unreadMessages.setTextSize(12);
			}
			holder.unreadMessages.setVisibility(View.VISIBLE);
			holder.displayName.setTypeface(null, Typeface.BOLD);
		} else {
			holder.unreadMessages.setVisibility(View.GONE);
			holder.displayName.setTypeface(null, Typeface.NORMAL);
		}

		if (isEditionEnabled()) {
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ChatRoomViewHolder holder = (ChatRoomViewHolder)v.getTag();
					holder.delete.setChecked(!holder.delete.isChecked());
				}
			});
			holder.unreadMessages.setVisibility(View.GONE);
			holder.delete.setOnCheckedChangeListener(null);
			holder.delete.setVisibility(View.VISIBLE);
			holder.delete.setChecked(getSelectedItemsPosition().contains(position));
			holder.delete.setTag(position);
			holder.delete.setOnCheckedChangeListener(getDeleteListener());
		} else {
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ChatRoom chatRoom = mRooms.get(position);
					LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asString(), null);
				}
			});
			holder.delete.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
		}
		return view;
	}
}
