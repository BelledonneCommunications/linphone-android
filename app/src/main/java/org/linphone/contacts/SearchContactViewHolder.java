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
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;

public class SearchContactViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {
    public final TextView name;
    public final TextView address;
    public final ImageView linphoneContact;
    public final ImageView isSelect;
    public final RelativeLayout avatarLayout;
    public final View disabled;

    private final ClickListener mListener;

    public SearchContactViewHolder(View view, ClickListener listener) {
        super(view);

        name = view.findViewById(R.id.contact_name);
        address = view.findViewById(R.id.contact_address);
        linphoneContact = view.findViewById(R.id.contact_linphone);
        isSelect = view.findViewById(R.id.contact_is_select);
        avatarLayout = view.findViewById(R.id.avatar_layout);
        disabled = view.findViewById(R.id.disabled);

        mListener = listener;
        view.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (mListener != null) {
            if (disabled.getVisibility() == View.GONE) {
                mListener.onItemClicked(getAdapterPosition());
            }
        }
    }

    public interface ClickListener {
        void onItemClicked(int position);
    }
}
