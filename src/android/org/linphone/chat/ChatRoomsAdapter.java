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
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

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
		private ClickListener mListener;

		public ChatRoomViewHolder(Context context,View itemView, ClickListener listener) {
			super(itemView);
			mContext = context;
			lastMessageSenderView = itemView.findViewById(R.id.lastMessageSender);
			lastMessageView = itemView.findViewById(R.id.lastMessage);
			date = itemView.findViewById(R.id.date);
			displayName = itemView.findViewById(R.id.sipUri);
			unreadMessages = itemView.findViewById(R.id.unreadMessages);
			delete = itemView.findViewById(R.id.delete_chatroom);
			contactPicture = itemView.findViewById(R.id.contact_picture);
			mListener = listener;

			itemView.setOnClickListener(this);
			itemView.setOnLongClickListener(this);
		}

		public void bindChatRoom(ChatRoom room) {
			mRoom = room;
			lastMessageSenderView.setText(getSender(mRoom));
			lastMessageView.setText(mRoom.getLastMessageInHistory() != null ? mRoom.getLastMessageInHistory().getTextContent(): "");
			date.setText(mRoom.getLastMessageInHistory() != null ? LinphoneUtils.timestampToHumanDate(mContext, mRoom.getLastUpdateTime(), R.string.messages_list_date_format) : "");
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

		public String getSender(ChatRoom mRoom){
			if (mRoom.getLastMessageInHistory() != null) {
				LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(mRoom.getLastMessageInHistory().getFromAddress());
				if (contact != null) {
					return (contact.getFullName() + mContext.getString(R.string.separator));
				}
				return (LinphoneUtils.getAddressDisplayName(mRoom.getLastMessageInHistory().getFromAddress())  + mContext.getString(R.string.separator));
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

	private Context mContext;
	public List<ChatRoom> mRooms;
	private static Bitmap mDefaultBitmap;
	private static Bitmap mDefaultGroupBitmap;
	private int mItemResource;
	private ChatRoomViewHolder.ClickListener mClickListener;

	public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> rooms, ChatRoomViewHolder.ClickListener clickListener, SelectableHelper helper) {
		super(helper);
		mClickListener = clickListener;
		mRooms = rooms;
		mContext = context;
		mItemResource = itemResource;
		mDefaultBitmap = ContactsManager.getInstance().getDefaultAvatarBitmap();
		mDefaultGroupBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.chat_group_avatar);
	}

	@Override
	public ChatRoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(mItemResource, parent, false);
		return new ChatRoomViewHolder(mContext, view, mClickListener);
	}

	@Override
	public void onBindViewHolder(ChatRoomViewHolder holder, int position) {
		ChatRoom room = mRooms.get(position);
		holder.delete.setVisibility(isEditionEnabled() ? View.VISIBLE : View.INVISIBLE);
		holder.unreadMessages.setVisibility(isEditionEnabled() ? View.INVISIBLE : (room.getUnreadMessagesCount() > 0 ? View.VISIBLE : View.INVISIBLE));
		holder.delete.setChecked(isSelected(position));
		holder.bindChatRoom(room);
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
		mRooms.clear();
		notifyDataSetChanged();
	}

	/**
	 * Adapter's methods
	 */

	@Override
	public int getItemCount() {
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
}
