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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.ChatRoom;
import org.linphone.core.Participant;

import java.util.ArrayList;
import java.util.List;

public class GroupInfoAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private List<ContactAddress> mItems;
    private View.OnClickListener mDeleteListener;
    private boolean mHideAdminFeatures;
    private ChatRoom mChatRoom;

    public GroupInfoAdapter(LayoutInflater inflater, List<ContactAddress> items, boolean hideAdminFeatures, boolean isCreation) {
        mInflater = inflater;
        mItems = items;
        mHideAdminFeatures = hideAdminFeatures || isCreation;
    }

    public void setChatRoom(ChatRoom room) {
    	mChatRoom = room;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int i) {
        return mItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = mInflater.inflate(R.layout.chat_infos_cell, null);
        }

        final ContactAddress ca = (ContactAddress)getItem(i);
        LinphoneContact c = ca.getContact();

        TextView name = view.findViewById(R.id.name);
        ImageView avatar = view.findViewById(R.id.contact_picture);
        ImageView delete = view.findViewById(R.id.delete);
        ImageView secure = view.findViewById(R.id.secure);
        final LinearLayout isAdmin = view.findViewById(R.id.isAdminLayout);
        final LinearLayout isNotAdmin = view.findViewById(R.id.isNotAdminLayout);

        name.setText((c.getFullName() != null) ? c.getFullName() :
		        (ca.getDisplayName() != null) ? ca.getDisplayName() : ca.getUsername());
        if (c.hasPhoto()) {
            LinphoneUtils.setThumbnailPictureFromUri(LinphoneActivity.instance(), avatar, c.getThumbnailUri());
        }

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
	            if (mDeleteListener != null) {
	                mDeleteListener.onClick(view);
	            }
            }
        });
        delete.setTag(ca);

        final String sipUri = ca.getAddress().asStringUriOnly();
        final String nameString = name.getText().toString();

	    secure.setVisibility(View.VISIBLE);
	    secure.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View view) {
			    if (LinphoneActivity.isInstanciated()) {
				    LinphoneActivity.instance().setAddresGoToDialerAndCall(sipUri, nameString, null);
			    }
		    }
	    });

        isAdmin.setVisibility(ca.isAdmin() ? View.VISIBLE : View.GONE);
        isNotAdmin.setVisibility(ca.isAdmin() ? View.GONE : View.VISIBLE);

        isAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
	            isNotAdmin.setVisibility(View.VISIBLE);
	            isAdmin.setVisibility(View.GONE);
	            ca.setAdmin(false);
            }
        });

        isNotAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
	            isNotAdmin.setVisibility(View.GONE);
	            isAdmin.setVisibility(View.VISIBLE);
	            ca.setAdmin(true);
            }
        });

	    delete.setVisibility(View.VISIBLE);
        if (mHideAdminFeatures) {
            delete.setVisibility(View.INVISIBLE);
            isAdmin.setOnClickListener(null); // Do not allow not admin to remove it's rights but display admins
            isNotAdmin.setVisibility(View.GONE); // Hide not admin button for not admin participants
        } else if (mChatRoom != null) {
	        boolean found = false;
	        for (Participant p : mChatRoom.getParticipants()) {
		        if (p.getAddress().weakEqual(ca.getAddress())) {
			        found = true;
			        break;
		        }
	        }
	        if (!found) {
		        isNotAdmin.setVisibility(View.GONE); // Hide not admin button for participant not yet added so even if user click it it won't have any effect
	        }
        }

        return view;
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
	    notifyDataSetInvalidated();
    }
}
