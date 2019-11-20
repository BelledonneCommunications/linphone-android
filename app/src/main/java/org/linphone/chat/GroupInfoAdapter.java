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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneContext;
import org.linphone.R;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.LinphoneContact;
import org.linphone.contacts.views.ContactAvatar;
import org.linphone.core.ChatRoom;
import org.linphone.core.Participant;

class GroupInfoAdapter extends RecyclerView.Adapter<GroupInfoViewHolder> {
    private List<ContactAddress> mItems;
    private View.OnClickListener mDeleteListener;
    private boolean mHideAdminFeatures;
    private ChatRoom mChatRoom;

    public GroupInfoAdapter(
            List<ContactAddress> items, boolean hideAdminFeatures, boolean isCreation) {
        mItems = items;
        mHideAdminFeatures = hideAdminFeatures || isCreation;
    }

    @NonNull
    @Override
    public GroupInfoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.chat_infos_cell, parent, false);
        return new GroupInfoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final GroupInfoViewHolder holder, int position) {
        final ContactAddress ca = (ContactAddress) getItem(position);
        LinphoneContact c = ca.getContact();

        holder.name.setText(
                (c != null && c.getFullName() != null)
                        ? c.getFullName()
                        : (ca.getDisplayName() != null) ? ca.getDisplayName() : ca.getUsername());

        if (c != null) {
            ContactAvatar.displayAvatar(c, holder.avatarLayout);
        } else {
            ContactAvatar.displayAvatar(holder.name.getText().toString(), holder.avatarLayout);
        }

        holder.sipUri.setText(ca.getAddressAsDisplayableString());

        if (!LinphoneContext.instance()
                .getApplicationContext()
                .getResources()
                .getBoolean(R.bool.show_sip_uri_in_chat)) {
            holder.sipUri.setVisibility(View.GONE);
            holder.name.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            holder.sipUri.setVisibility(
                                    holder.sipUri.getVisibility() == View.VISIBLE
                                            ? View.GONE
                                            : View.VISIBLE);
                        }
                    });
        }

        holder.delete.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mDeleteListener != null) {
                            mDeleteListener.onClick(view);
                        }
                    }
                });
        holder.delete.setTag(ca);

        holder.isAdmin.setVisibility(ca.isAdmin() ? View.VISIBLE : View.GONE);
        holder.isNotAdmin.setVisibility(ca.isAdmin() ? View.GONE : View.VISIBLE);

        holder.isAdmin.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        holder.isNotAdmin.setVisibility(View.VISIBLE);
                        holder.isAdmin.setVisibility(View.GONE);
                        ca.setAdmin(false);
                    }
                });

        holder.isNotAdmin.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        holder.isNotAdmin.setVisibility(View.GONE);
                        holder.isAdmin.setVisibility(View.VISIBLE);
                        ca.setAdmin(true);
                    }
                });

        holder.delete.setVisibility(View.VISIBLE);
        if (mHideAdminFeatures) {
            holder.delete.setVisibility(View.INVISIBLE);
            holder.isAdmin.setOnClickListener(
                    null); // Do not allow not admin to remove it's rights but display admins
            holder.isNotAdmin.setVisibility(
                    View.GONE); // Hide not admin button for not admin participants
        } else if (mChatRoom != null) {
            boolean found = false;
            for (Participant p : mChatRoom.getParticipants()) {
                if (p.getAddress().weakEqual(ca.getAddress())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                holder.isNotAdmin.setVisibility(
                        View.GONE); // Hide not admin button for participant not yet added so
                // even if user click it it won't have any effect
            }
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public void setChatRoom(ChatRoom room) {
        mChatRoom = room;
    }

    private Object getItem(int i) {
        return mItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public void setOnDeleteClickListener(View.OnClickListener onClickListener) {
        mDeleteListener = onClickListener;
    }

    public void updateDataSet(ArrayList<ContactAddress> mParticipants) {
        mItems = mParticipants;
        notifyDataSetChanged();
    }

    public void setAdminFeaturesVisible(boolean visible) {
        mHideAdminFeatures = !visible;
        notifyDataSetChanged();
    }
}
