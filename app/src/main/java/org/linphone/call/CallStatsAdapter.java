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
package org.linphone.call;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.contacts.views.ContactAvatar;
import org.linphone.core.Call;
import org.linphone.utils.LinphoneUtils;

public class CallStatsAdapter extends BaseExpandableListAdapter {
    private final Context mContext;
    private List<Call> mCalls;

    public CallStatsAdapter(Context context) {
        mContext = context;
        mCalls = new ArrayList<>();
    }

    public void updateListItems(List<Call> listCall) {
        if (listCall != null) {
            mCalls = listCall;
            notifyDataSetChanged();
        }
    }

    @Override
    public View getChildView(
            int groupPosition,
            int childPosition,
            boolean isLastChild,
            View view,
            ViewGroup viewGroup) {

        CallStatsChildViewHolder holder;

        if (view != null) {
            Object possibleHolder = view.getTag();
            if (possibleHolder instanceof CallStatsChildViewHolder) {
                holder = (CallStatsChildViewHolder) possibleHolder;
                view.setTag(holder);
            }
        } else {
            // opening the statistics view
            LayoutInflater inflater = LayoutInflater.from(mContext);
            view = inflater.inflate(R.layout.call_stats_child, viewGroup, false);
        }

        // filling the view
        holder = new CallStatsChildViewHolder(view, mContext);
        view.setTag(holder);
        holder.setCall(mCalls.get(groupPosition));

        return view;
    }

    @Override
    public View getGroupView(
            int groupPosition, boolean isExpanded, View view, ViewGroup viewGroup) {

        CallStatsViewHolder holder = null;
        if (view != null) {
            Object possibleHolder = view.getTag();
            if (possibleHolder instanceof CallStatsViewHolder) {
                holder = (CallStatsViewHolder) possibleHolder;
            }
        } else {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            view = inflater.inflate(R.layout.call_stats_group, viewGroup, false);
        }
        if (holder == null) {
            holder = new CallStatsViewHolder(view);
            view.setTag(holder);
        }

        // Recovering the current call
        Call call = (Call) getGroup(groupPosition);
        // Search for the associated contact
        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAddress(call.getRemoteAddress());
        if (contact != null) {
            // Setting up the avatar
            ContactAvatar.displayAvatar(contact, holder.avatarLayout);
            // addition of the participant's name
            holder.participantName.setText(contact.getFullName());
        } else {
            String displayName = LinphoneUtils.getAddressDisplayName(call.getRemoteAddress());
            ContactAvatar.displayAvatar(displayName, holder.avatarLayout);
            holder.participantName.setText(displayName);
        }

        // add sip address on group view
        holder.sipUri.setText(call.getRemoteAddress().asString());

        return view;
    }

    @Override
    public int getGroupCount() {
        return mCalls.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        if (mCalls.isEmpty() && groupPosition < mCalls.size()) {
            return null;
        }
        return mCalls.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (mCalls.isEmpty() && groupPosition < mCalls.size()) {
            return null;
        }
        return mCalls.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
}
