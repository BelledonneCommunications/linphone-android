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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.linphone.LinphoneManager;
import org.linphone.core.ChatRoom;
import org.linphone.ui.SelectableAdapter;
import org.linphone.ui.SelectableHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatRoomsAdapter extends SelectableAdapter<ChatRoomViewHolder> {
    private Context mContext;
    public List<ChatRoom> mRooms;
    private int mItemResource;
    private ChatRoomViewHolder.ClickListener mClickListener;

    public ChatRoomsAdapter(Context context, int itemResource, List<ChatRoom> rooms, ChatRoomViewHolder.ClickListener clickListener, SelectableHelper helper) {
        super(helper);
        mClickListener = clickListener;
        mRooms = rooms;
        mContext = context;
        mItemResource = itemResource;
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
