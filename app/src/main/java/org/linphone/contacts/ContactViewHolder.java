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
package org.linphone.contacts;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;

public class ContactViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener {
    public final CheckBox delete;
    public final ImageView linphoneFriend;
    public final TextView name;
    public final LinearLayout separator;
    public final TextView separatorText;
    public final RelativeLayout avatarLayout;
    public final TextView organization;
    private final ClickListener mListener;

    public ContactViewHolder(View view, ClickListener listener) {
        super(view);

        delete = view.findViewById(R.id.delete);
        linphoneFriend = view.findViewById(R.id.friendLinphone);
        name = view.findViewById(R.id.name);
        separator = view.findViewById(R.id.separator);
        separatorText = view.findViewById(R.id.separator_text);
        avatarLayout = view.findViewById(R.id.avatar_layout);
        organization = view.findViewById(R.id.contactOrganization);
        // friendStatus = view.findViewById(R.id.friendStatus);
        mListener = listener;
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
    }

    @Override
    public void onClick(View view) {
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

    public interface ClickListener {
        void onItemClicked(int position);

        boolean onItemLongClicked(int position);
    }
}
