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
import android.widget.BaseExpandableListAdapter;
import java.util.ArrayList;
import java.util.List;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.contacts.views.ContactAvatar;
import org.linphone.core.Address;
import org.linphone.core.ChatRoomSecurityLevel;
import org.linphone.core.Participant;
import org.linphone.core.ParticipantDevice;
import org.linphone.utils.LinphoneUtils;

class DevicesAdapter extends BaseExpandableListAdapter {
    private final Context mContext;
    private List<Participant> mParticipants;

    public DevicesAdapter(Context context) {
        mContext = context;
        mParticipants = new ArrayList<>();
    }

    public void updateListItems(List<Participant> participants) {
        mParticipants = participants;
        notifyDataSetChanged();
    }

    @Override
    public View getGroupView(
            int groupPosition, boolean isExpanded, View view, ViewGroup viewGroup) {
        Participant participant = (Participant) getGroup(groupPosition);

        // Group position 0 is reserved for ME participant & devices
        DeviceGroupViewHolder holder = null;
        if (view != null) {
            Object possibleHolder = view.getTag();
            if (possibleHolder instanceof DeviceGroupViewHolder) {
                holder = (DeviceGroupViewHolder) possibleHolder;
            }
        } else {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            view = inflater.inflate(R.layout.chat_device_group, viewGroup, false);
        }
        if (holder == null) {
            holder = new DeviceGroupViewHolder(view);
            view.setTag(holder);
        }

        Address participantAddress = participant.getAddress();
        LinphoneContact contact =
                ContactsManager.getInstance().findContactFromAddress(participantAddress);
        if (contact != null) {
            ContactAvatar.displayAvatar(
                    contact, participant.getSecurityLevel(), holder.avatarLayout);
            holder.participantName.setText(contact.getFullName());
        } else {
            String displayName = LinphoneUtils.getAddressDisplayName(participantAddress);
            ContactAvatar.displayAvatar(
                    displayName, participant.getSecurityLevel(), holder.avatarLayout);
            holder.participantName.setText(displayName);
        }

        holder.sipUri.setText(participantAddress.asStringUriOnly());
        if (!mContext.getResources().getBoolean(R.bool.show_sip_uri_in_chat)) {
            holder.sipUri.setVisibility(View.GONE);
        }

        if (getChildrenCount(groupPosition) == 1) {
            holder.securityLevel.setVisibility(View.VISIBLE);
            holder.groupExpander.setVisibility(View.GONE);

            ParticipantDevice device = (ParticipantDevice) getChild(groupPosition, 0);
            ChatRoomSecurityLevel level = device.getSecurityLevel();
            switch (level) {
                case Safe:
                    holder.securityLevel.setImageResource(R.drawable.security_2_indicator);
                    break;
                case Encrypted:
                    holder.securityLevel.setImageResource(R.drawable.security_1_indicator);
                    break;
                case ClearText:
                case Unsafe:
                default:
                    holder.securityLevel.setImageResource(R.drawable.security_alert_indicator);
                    break;
            }
        } else {
            holder.securityLevel.setVisibility(View.GONE);
            holder.groupExpander.setVisibility(View.VISIBLE);
            holder.groupExpander.setImageResource(
                    isExpanded ? R.drawable.chevron_list_open : R.drawable.chevron_list_close);
        }

        return view;
    }

    @Override
    public View getChildView(
            int groupPosition, int childPosition, boolean b, View view, ViewGroup viewGroup) {
        ParticipantDevice device = (ParticipantDevice) getChild(groupPosition, childPosition);

        DeviceChildViewHolder holder = null;
        if (view != null) {
            Object possibleHolder = view.getTag();
            if (possibleHolder instanceof DeviceChildViewHolder) {
                holder = (DeviceChildViewHolder) possibleHolder;
            }
        } else {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            view = inflater.inflate(R.layout.chat_device_cell, viewGroup, false);
        }
        if (holder == null) {
            holder = new DeviceChildViewHolder(view);
            view.setTag(holder);
        }

        holder.deviceName.setText(device.getName());

        ChatRoomSecurityLevel level = device.getSecurityLevel();
        switch (level) {
            case Safe:
                holder.securityLevel.setImageResource(R.drawable.security_2_indicator);
                break;
            case Encrypted:
                holder.securityLevel.setImageResource(R.drawable.security_1_indicator);
                break;
            case ClearText:
            case Unsafe:
            default:
                holder.securityLevel.setImageResource(R.drawable.security_alert_indicator);
                break;
        }

        return view;
    }

    @Override
    public int getGroupCount() {
        return mParticipants.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (groupPosition >= mParticipants.size()) return 0;
        return mParticipants.get(groupPosition).getDevices().length;
    }

    @Override
    public Object getGroup(int groupPosition) {
        if (groupPosition >= mParticipants.size()) return null;
        return mParticipants.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (groupPosition >= mParticipants.size()) return null;
        ParticipantDevice[] devices = mParticipants.get(groupPosition).getDevices();
        if (devices.length == 0 || childPosition >= devices.length) return null;
        return devices[childPosition];
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }
}
