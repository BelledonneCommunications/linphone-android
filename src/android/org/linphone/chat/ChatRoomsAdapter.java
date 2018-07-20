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
import android.support.v7.widget.RecyclerView;
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
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.EventLog;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class ChatRoomsAdapter extends SelectableAdapter<ChatRoomsAdapter.ChatRoomViewHolder> {

	public static class ChatRoomViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{

		public TextView lastMessageSenderView;
		public TextView lastMessageView;
		public TextView date;
		public TextView displayName;
		public TextView unreadMessages;
		public CheckBox delete;
		public ImageView contactPicture;
		public Context mContext;
		public ChatRoom mRoom;
		public ClickListener listener;

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
			this.listener = listener;

			itemView.setOnClickListener(this);
			itemView.setOnLongClickListener(this);
		}
		public void bindChatRoom(ChatRoom room) {

			//Bind the data to the ViewHolder
			this.mRoom = room;
			this.lastMessageSenderView.setText(getSender(mRoom));
			this.lastMessageView.setText(mRoom.getLastMessageInHistory() != null ? mRoom.getLastMessageInHistory().getTextContent(): "");
			this.date.setText(mRoom.getLastMessageInHistory()!=null ? LinphoneUtils.timestampToHumanDate(this.mContext, mRoom.getLastUpdateTime(), R.string.messages_list_date_format) : "");
			this.displayName.setText(getContact(mRoom));
			this.unreadMessages.setText(String.valueOf(LinphoneManager.getInstance().getUnreadCountForChatRoom(mRoom)));
			getAvatar(mRoom);
		}

		//Handle the onClick/onLongClick event for the ViewHolder
//		@Override
		public void onClick(View v) {
			if (listener != null) {
				listener.onItemClicked(getAdapterPosition());
			}
		}

//		@Override
		public boolean onLongClick(View v) {
			if (listener != null) {
				return listener.onItemLongClicked(getAdapterPosition());
			}
			return false;
		}

		//Functions to get messages datas

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
			contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getLastMessageInHistory().getFromAddress());
			if (contact != null) {

				return (contact.getFullName());

			} else {
				return (LinphoneUtils.getAddressDisplayName(mRoom.getLastMessageInHistory().getFromAddress()));
			}
		}

		public void getAvatar(ChatRoom mRoom) {
			mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
			LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress());
			if (contact != null) {
				LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), this.contactPicture, ContactsManager.getInstance().findContactFromAddress(mRoom.getPeerAddress()).getThumbnailUri());
			} else {
				this.contactPicture.setImageBitmap(mDefaultBitmap);
			}
		}



		public interface ClickListener {
			void onItemClicked(int position);
			boolean onItemLongClicked(int position);
		}





	}						//Holder ending

	//Adapter beginning

	private Context mContext;
	public List<ChatRoom> mRooms;
	private static Bitmap mDefaultBitmap;
	//private Bitmap mDefaultGroupBitmap;
	private ChatRoomListenerStub mListener;
	private int itemResource;
	private ChatRoomViewHolder.ClickListener clickListener;

	public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> mRooms, ChatRoomViewHolder.ClickListener clickListener, SelectableHelper helper) {

		super(helper);
		this.clickListener = clickListener;
		this.mRooms = mRooms;
		this.mContext = context;
		this.itemResource = itemResource;
		mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
		//mDefaultGroupBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.chat_group_avatar);
	}




	@Override
	public ChatRoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

		// Inflate the view and return the new ViewHolder
		View view = LayoutInflater.from(parent.getContext())
				.inflate(this.itemResource, parent, false);

		return new ChatRoomViewHolder(this.mContext, view, clickListener);

	}

	@Override
	public void onBindViewHolder(ChatRoomViewHolder holder, int position) {
		//Bind datas to the ViewHolder
		ChatRoom room = this.mRooms.get(position);
		//Shows checkboxes when ActionMode enabled
		holder.delete.setVisibility(this.isEditionEnabled() == true ? View.VISIBLE : View.INVISIBLE);
		holder.unreadMessages.setVisibility(this.isEditionEnabled() == false ? View.VISIBLE : View.INVISIBLE);
		//Set checkbox checked if item selected
		holder.delete.setChecked(isSelected(position) ? true : false);
		//Bind the chatroom object to the holder
		holder.bindChatRoom(room);
	}

//	Let know the adapter if ActionMode is enabled
//	public void setEditionMode(ActionMode actionMode) {
//		if ( isEditionEnabled() == true) {
//			this.editionMode=true;
//			this.notifyDataSetChanged();	//Needed to update the whole list checkboxes
//		} else {
//			this.editionMode=false;
//			this.notifyDataSetChanged();
//		}
//
//	}

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
		notifyDataSetChanged();
	}


	public void removeItem(int position) {

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		cleanRoom(position);
		lc.deleteChatRoom(mRooms.get(position));
		mRooms.remove(position);
		notifyItemRemoved(position);
	}
//
//	public void removeItems(List<Integer> positions) {
//		// Reverse-sort the list
//		Collections.sort(positions, new Comparator<Integer>() {
//			@Override
//			public int compare(Integer lhs, Integer rhs) {
//				return rhs - lhs;
//			}
//		});
//
//		// Split the list in ranges
//		while (!positions.isEmpty()) {
//			if (positions.size() == 1) {
//				removeItem(positions.get(0));
//				positions.remove(0);
//
//			} else {
//				int count = 1;
//				while (positions.size() > count && positions.get(count).equals(positions.get(count - 1) - 1)) {
//					++count;
//				}
//
//				if (count == 1) {
//					removeItem(positions.get(0));
//				} else {
//					removeRange(positions.get(count - 1), count);
//				}
//
//				for (int i = 0; i < count; ++i) {
//					positions.remove(0);
//				}
//			}
//		}
//	}
//
//	private void removeRange(int positionStart, int itemCount) {
//		for (int i = 0; i < itemCount; ++i) {
//			Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
//			cleanRoom(positionStart);
//			lc.deleteChatRoom(mRooms.get(positionStart));
//			mRooms.remove(positionStart);
//		}
//		notifyItemRangeRemoved(positionStart, itemCount);
//	}
//
//	//Delete downloaded file from incoming message that will be deleted
	private void cleanRoom (int position) {

		for (EventLog eventLog : mRooms.get(position).getHistoryEvents(0)) {
			if (eventLog.getType() == EventLog.Type.ConferenceChatMessage) {
				ChatMessage message = eventLog.getChatMessage();
				if (message.getAppdata() != null && !message.isOutgoing()) {
					File file = new File(message.getAppdata());
					if (file.exists()) {
						file.delete();
					}
				}
			}
		}
	}



	/**
	 * Adapter's methods
	 */


	@Override
	public int getItemCount() {
		return this.mRooms.size();
	}

	@Override
	public Object getItem(int position) {
		return mRooms.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;

	}
}
