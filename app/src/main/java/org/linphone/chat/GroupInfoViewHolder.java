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

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.linphone.R;

public class GroupInfoViewHolder extends RecyclerView.ViewHolder {
    public final TextView name, sipUri;
    public final RelativeLayout avatarLayout;
    public final ImageView delete;
    public final LinearLayout isAdmin;
    public final LinearLayout isNotAdmin;

    public GroupInfoViewHolder(View view) {
        super(view);
        name = view.findViewById(R.id.name);
        sipUri = view.findViewById(R.id.sipUri);
        avatarLayout = view.findViewById(R.id.avatar_layout);
        delete = view.findViewById(R.id.delete);
        isAdmin = view.findViewById(R.id.isAdminLayout);
        isNotAdmin = view.findViewById(R.id.isNotAdminLayout);
    }
}
