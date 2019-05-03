package org.linphone.chat;

/*
ChatRoomsAdapter.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ChatRoom;
import org.linphone.utils.LinphoneUtils;
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
        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();
        if (mContext.getResources().getBoolean(R.bool.hide_empty_one_to_one_chat_rooms)) {
            mRooms = LinphoneUtils.removeEmptyOneToOneChatRooms(rooms);
        } else {
            mRooms = Arrays.asList(rooms);
        }

        Collections.sort(
                mRooms,
                new Comparator<ChatRoom>() {
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
        // Do not notify data set changed, we don't want the list to empty when fragment is paused
    }

    /** Adapter's methods */
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
