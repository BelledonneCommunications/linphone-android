/*
GroupChatFragment.java
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.linphone.R;
import org.linphone.core.ChatMessage;

import java.util.ArrayList;

public class ChatEventsAdapter extends BaseAdapter {
    private ArrayList<ChatMessage> mHistory;
    private LayoutInflater mLayoutInflater;

    public ChatEventsAdapter(LayoutInflater inflater) {
        mLayoutInflater = inflater;
    }

    @Override
    public int getCount() {
        return mHistory.size();
    }

    @Override
    public Object getItem(int i) {
        return mHistory.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ChatBubbleViewHolder holder;
        if (view != null) {
            holder = (ChatBubbleViewHolder) view.getTag();
        } else {
            view = mLayoutInflater.inflate(R.layout.chat_bubble, null);
            holder = new ChatBubbleViewHolder(view);
            view.setTag(holder);
        }

        ChatMessage msg = (ChatMessage)getItem(i);
        holder.messageId = msg.getMessageId();

        //TODO

        return view;
    }
}
