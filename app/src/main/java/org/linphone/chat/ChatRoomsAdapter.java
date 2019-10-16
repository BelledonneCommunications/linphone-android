/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.DiffUtil;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.core.ChatRoom;
import org.linphone.utils.SelectableAdapter;
import org.linphone.utils.SelectableHelper;

public class ChatRoomsAdapter extends SelectableAdapter<ChatRoomViewHolder> {
    private final Context mContext;
    private List<ChatRoom> mRooms;
    private final int mItemResource;
    private final ChatRoomViewHolder.ClickListener mClickListener;

    public ChatRoomsAdapter(
            Context context,
            int itemResource,
            List<ChatRoom> rooms,
            ChatRoomViewHolder.ClickListener clickListener,
            SelectableHelper helper) {
        super(helper);
        mClickListener = clickListener;
        mRooms = rooms;
        mContext = context;
        mItemResource = itemResource;
    }

    @Override
    public ChatRoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(mItemResource, parent, false);
        return new ChatRoomViewHolder(mContext, view, mClickListener);
    }

    @Override
    public void onBindViewHolder(ChatRoomViewHolder holder, int position) {
        ChatRoom room = mRooms.get(position);
        holder.delete.setVisibility(isEditionEnabled() ? View.VISIBLE : View.INVISIBLE);
        holder.unreadMessages.setVisibility(
                isEditionEnabled()
                        ? View.INVISIBLE
                        : (room.getUnreadMessagesCount() > 0 ? View.VISIBLE : View.INVISIBLE));
        holder.delete.setChecked(isSelected(position));
        room.setUserData(holder);
        holder.bindChatRoom(room);
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(boolean force) {
        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();
        List<ChatRoom> roomsList = Arrays.asList(rooms);

        if (!force) {
            DiffUtil.DiffResult diffResult =
                    DiffUtil.calculateDiff(new ChatRoomDiffCallback(roomsList, mRooms));
            diffResult.dispatchUpdatesTo(this);
            mRooms = roomsList;
        } else {
            mRooms = roomsList;
            notifyDataSetChanged();
        }
    }

    /** Adapter's methods */
    @Override
    public int getItemCount() {
        return mRooms.size();
    }

    @Override
    public Object getItem(int position) {
        if (position < mRooms.size()) return mRooms.get(position);
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    class ChatRoomDiffCallback extends DiffUtil.Callback {
        List<ChatRoom> oldChatRooms;
        List<ChatRoom> newChatRooms;

        public ChatRoomDiffCallback(List<ChatRoom> newRooms, List<ChatRoom> oldRooms) {
            oldChatRooms = oldRooms;
            newChatRooms = newRooms;
        }

        @Override
        public int getOldListSize() {
            return oldChatRooms.size();
        }

        @Override
        public int getNewListSize() {
            return newChatRooms.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldChatRooms.get(oldItemPosition) == (newChatRooms.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return newChatRooms.get(newItemPosition).getUnreadMessagesCount() == 0;
        }
    }
}
