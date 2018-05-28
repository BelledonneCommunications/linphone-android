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
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

//import org.linphone.Chat;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.ui.SelectableAdapter;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatRoomsAdapter extends RecyclerView.Adapter<ChatRoomsAdapter.ChatRoomViewHolder> {

	public class ChatRoomViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		public TextView lastMessageSenderView;
		public TextView lastMessageView;
		public TextView date;
		public TextView displayName;
		public TextView unreadMessages;
		public CheckBox delete;
		public ImageView contactPicture;
		public Context mContext;
		public ChatRoom mRoom;


		public ChatRoomViewHolder(Context context,View itemView, ClickListener listener) {
			super(itemView);
			this.mContext= context;
			this.lastMessageSenderView = itemView.findViewById(R.id.lastMessageSender);
			this.lastMessageView = itemView.findViewById(R.id.lastMessage);
			this.date = itemView.findViewById(R.id.date);
			this.displayName = itemView.findViewById(R.id.sipUri);
			this.unreadMessages = itemView.findViewById(R.id.unreadMessages);
			this.delete = itemView.findViewById(R.id.delete_chatroom);
			this.contactPicture = itemView.findViewById(R.id.contact_picture);
			//this.selectedOverlay = itemView.findViewById(R.id.selected_overlay);
			this.listener = listener;

			itemView.setOnClickListener(this);
		}
		public void bindChatRoom(ChatRoom room) {

			// 4. Bind the data to the ViewHolder
			this.mRoom = room;
			this.lastMessageSenderView.setText(getSender(mRoom));
			this.lastMessageView.setText(mRoom.getLastMessageInHistory() != null ? mRoom.getLastMessageInHistory().getTextContent(): "");
			this.date.setText(mRoom.getLastMessageInHistory()!=null ? LinphoneUtils.timestampToHumanDate(this.mContext, mRoom.getLastUpdateTime(), R.string.messages_list_date_format) : "");
			this.displayName.setText(getContact(mRoom));
			this.unreadMessages.setText(String.valueOf(LinphoneManager.getInstance().getUnreadCountForChatRoom(mRoom)));
//			this.delete.setChecked(!this.delete.isChecked());
//			this.delete.setChecked(!this.delete.isChecked());
//			this.delete.setVisibility(this.editionMode == true ? View.VISIBLE : View.INVISIBLE);
//			this.unreadMessages.setVisibility(this.editionMode == false ? View.VISIBLE : View.INVISIBLE);

			getAvatar(mRoom);

		}

		@Override
		public void onClick(View v) {

			// 5. Handle the onClick event for the ViewHolder
			if (this.mRoom != null) {
				LinphoneActivity.instance().goToChat(mRoom.getPeerAddress().asString());
			}
		}

		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {

				return listener.onItemLongClicked(getAdapterPosition());
			}
			return false;
		}


		public String getSender(ChatRoom mRoom){
			if (mRoom.getLastMessageInHistory() != null) {
				LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getLastMessageInHistory().getFromAddress());
				if (contact != null) {
					return (contact.getFullName() + mContext.getString(R.string.separator));
				} else {
					return (LinphoneUtils.getAddressDisplayName(mRoom.getLastMessageInHistory().getFromAddress())  + ":");
				}
			}else{
				return "" ;
			}
		}

		public String getContact(ChatRoom mRoom) {
			LinphoneContact contact;
//			contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress());
			contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getLastMessageInHistory().getFromAddress());
			if (contact != null) {

				return (contact.getFullName());

			} else {
				return (LinphoneUtils.getAddressDisplayName(mRoom.getLastMessageInHistory().getFromAddress()));
			}
		}
////		Address remoteAddress = chatRoom.getPeerAddress();
////		Address contactAddress = remoteAddress;
//			if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
//			LinphoneContact contact;
//			if (chatRoom.getParticipants().length > 0) {
//				contact = ContactsManager.getInstance().findContactFromAddress(chatRoom.getParticipants()[0].getAddress());
//				if (contact != null) {
//					holder.displayName.setText(contact.getFullName());
//					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
//				} else {
//					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(chatRoom.getParticipants()[0].getAddress()));
//				}
//			} else {
//				contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
//				if (contact != null) {
//					holder.displayName.setText(contact.getFullName());
//					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
//				} else {
//					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(contactAddress));
//				}
//			}
//		} else {
//			holder.displayName.setText(chatRoom.getSubject());




		public void getAvatar(ChatRoom mRoom) {
			mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress());
			if (contact != null) {
				LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), this.contactPicture, ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress()).getThumbnailUri());
			} else {
				this.contactPicture.setImageBitmap(mDefaultBitmap);
			}
		}





	}

	private Context mContext;
	private List<ChatRoom> mRooms;
	private LayoutInflater mLayoutInflater;
	private Bitmap mDefaultBitmap, mDefaultGroupBitmap;
	private ChatRoomListenerStub mListener;
	private int itemResource;
	private ChatRoomViewHolder.ClickListener clickListener;
	private boolean editionMode;

//	public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> mRooms) {
	public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> mRooms, ChatRoomViewHolder.ClickListener clickListener) {

	public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> mRooms) {
		super();
		this.editionMode = false;
		this.clickListener = clickListener;
		this.mRooms = mRooms;
		this.mContext = context;
		this.itemResource = itemResource;
		mContext = context;
		//mLayoutInflater = inflater;
		mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
		mDefaultGroupBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.chat_group_avatar);

//		mListener = new ChatRoomListenerStub() {
//			@Override
//			public void onSubjectChanged(ChatRoom cr, EventLog eventLog) {
//				ChatRoomViewHolder holder = (ChatRoomViewHolder) cr.getUserData();
//				holder.displayName.setText(cr.getSubject());
//			}
//		};

	}

	@Override
	public ChatRoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

		// 3. Inflate the view and return the new ViewHolder
		View view = LayoutInflater.from(parent.getContext())
				.inflate(this.itemResource, parent, false);
		return new ChatRoomViewHolder(this.mContext, view);
	}

	@Override
	public void onBindViewHolder(ChatRoomViewHolder holder, int position) {

		// 5. Use position to access the correct Bakery object
		ChatRoom room = this.mRooms.get(position);

		//Colors the item when selected
		holder.delete.setVisibility(this.editionMode == true ? View.VISIBLE : View.INVISIBLE);
		holder.unreadMessages.setVisibility(this.editionMode == false ? View.VISIBLE : View.INVISIBLE);

		holder.delete.setChecked(isSelected(position) ? true : false);
//		holder.unreadMessages.setVisibility(View.VISIBLE);
		// 6. Bind the bakery object to the holder
		holder.bindChatRoom(room);
	}
	public void setEditionMode(ActionMode actionMode) {
		if ( actionMode != null) {
			this.editionMode=true;
			this.notifyDataSetChanged();
		} else {
			this.editionMode=false;
			this.notifyDataSetChanged();
		}

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


	public void removeItem(int position) {

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
//		mRooms.get(position).addListener(mChatRoomListener);
		lc.deleteChatRoom(mRooms.get(position));
		mRooms.remove(position);
		notifyItemRemoved(position);
	}

	public void removeItems(List<Integer> positions) {
		// Reverse-sort the list
		Collections.sort(positions, new Comparator<Integer>() {
			@Override
			public int compare(Integer lhs, Integer rhs) {
				return rhs - lhs;
			}
		});

		// Split the list in ranges
		while (!positions.isEmpty()) {
			if (positions.size() == 1) {
				removeItem(positions.get(0));
				positions.remove(0);
			} else {
				int count = 1;
				while (positions.size() > count && positions.get(count).equals(positions.get(count - 1) - 1)) {
					++count;
				}

				if (count == 1) {
					removeItem(positions.get(0));
				} else {
					removeRange(positions.get(count - 1), count);
				}

				for (int i = 0; i < count; ++i) {
					positions.remove(0);
				}
			}
		}
	}

	private void removeRange(int positionStart, int itemCount) {
		for (int i = 0; i < itemCount; ++i) {
			Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			lc.deleteChatRoom(mRooms.get(positionStart));
			mRooms.remove(positionStart);
		}
		notifyItemRangeRemoved(positionStart, itemCount);
	}




	/**
	 * Adapter's methods
	 */

//	@Override
//	public int getCount() {
//		return mRooms.size();
//	}

	@Override
	public int getItemCount() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();

		return this.mRooms.size();
	}


	//@Override
	public Object getItem(int position) {
		return mRooms.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}


//	@Override
//	public View getView(final int position, View convertView, ViewGroup viewGroup) {
//		View view;
//		ChatRoomViewHolder holder;
//
//		if (convertView != null) {
//			view = convertView;
//			holder = (ChatRoomViewHolder) view.getTag();
//		} else {
//			view = mLayoutInflater.inflate(R.layout.chatlist_cell, viewGroup, false);
//			holder = new ChatRoomViewHolder(view);
//			view.setTag(holder);
//		}
//
//		ChatRoom chatRoom = mRooms.get(position);
//		Address remoteAddress = chatRoom.getPeerAddress();
//		Address contactAddress = remoteAddress;
//
//		if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) && chatRoom.getParticipants().length > 0) {
//			contactAddress = chatRoom.getParticipants()[0].getAddress();
//		}
//
//		if (chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt()) && chatRoom.getState() == ChatRoom.State.Created) { // Only set for state Created otherwise it will conflict with removal listener
//			chatRoom.addListener(mListener);
//			chatRoom.setUserData(holder);
//		}
//
//		int unreadMessagesCount = LinphoneManager.getInstance().getUnreadCountForChatRoom(chatRoom);
//		ChatMessage lastMessage = chatRoom.getLastMessageInHistory();
//		holder.lastMessageView.setText("");
//		holder.lastMessageSenderView.setText("");
//		holder.date.setText(LinphoneUtils.timestampToHumanDate(mContext, chatRoom.getLastUpdateTime(), R.string.messages_list_date_format));
//
//		if (lastMessage != null) {
//			if (lastMessage.getFileTransferInformation() != null || lastMessage.getExternalBodyUrl() != null || lastMessage.getAppdata() != null) {
//				holder.lastMessageView.setBackgroundResource(R.drawable.chat_file_message);
//			} else if (lastMessage.getTextContent() != null && lastMessage.getTextContent().length() > 0) {
//				holder.lastMessageView.setBackgroundResource(0);
//				holder.lastMessageView.setText(lastMessage.getTextContent());
//			}
//
//			Address lastMessageSenderAddress = lastMessage.getFromAddress();
//			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(lastMessageSenderAddress);
//			if (contact != null) {
//				holder.lastMessageSenderView.setText(contact.getFullName() + mContext.getString(R.string.separator));
//			} else {
//				holder.lastMessageSenderView.setText(LinphoneUtils.getAddressDisplayName(lastMessageSenderAddress) +  mContext.getString(R.string.separator));
//			}
//		}
//
//		holder.displayName.setSelected(true); // For animation
//		holder.contactPicture.setImageBitmap(mDefaultBitmap);
//
//		if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
//			LinphoneContact contact;
//			if (chatRoom.getParticipants().length > 0) {
//				contact = ContactsManager.getInstance().findContactFromAddress(chatRoom.getParticipants()[0].getAddress());
//				if (contact != null) {
//					holder.displayName.setText(contact.getFullName());
//					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
//				} else {
//					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(chatRoom.getParticipants()[0].getAddress()));
//				}
//			} else {
//				contact = ContactsManager.getInstance().findContactFromAddress(contactAddress);
//				if (contact != null) {
//					holder.displayName.setText(contact.getFullName());
//					LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), holder.contactPicture, contact.getThumbnailUri());
//				} else {
//					holder.displayName.setText(LinphoneUtils.getAddressDisplayName(contactAddress));
//				}
//			}
//		} else {
//			holder.displayName.setText(chatRoom.getSubject());
//			holder.contactPicture.setImageBitmap(mDefaultGroupBitmap);
//		}
//
//		if (unreadMessagesCount > 0) {
//			holder.unreadMessages.setVisibility(View.VISIBLE);
//			holder.unreadMessages.setText(String.valueOf(unreadMessagesCount));
//			if (unreadMessagesCount > 99) {
//				holder.unreadMessages.setTextSize(12);
//			}
//			holder.unreadMessages.setVisibility(View.VISIBLE);
//			holder.displayName.setTypeface(null, Typeface.BOLD);
//		} else {
//			holder.unreadMessages.setVisibility(View.GONE);
//			holder.displayName.setTypeface(null, Typeface.NORMAL);
//		}
//
//		if (isEditionEnabled()) {
//			view.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					ChatRoomViewHolder holder = (ChatRoomViewHolder)v.getTag();
//					holder.delete.setChecked(!holder.delete.isChecked());
//				}
//			});
//			holder.unreadMessages.setVisibility(View.GONE);
//			holder.delete.setOnCheckedChangeListener(null);
//			holder.delete.setVisibility(View.VISIBLE);
//			holder.delete.setChecked(getSelectedItemsPosition().contains(position));
//			holder.delete.setTag(position);
//			holder.delete.setOnCheckedChangeListener(getDeleteListener());
//		} else {
//			view.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					ChatRoom chatRoom = mRooms.get(position);
//					LinphoneActivity.instance().goToChat(chatRoom.getPeerAddress().asString());
//				}
//			});
//			holder.delete.setVisibility(isEditionEnabled() ? View.VISIBLE : View.GONE);
//		}
//		return view;
//	}
}
