package org.linphone.chat;

/*
DeviceGroupViewHolder.java
Copyright (C) 2010-2018  Belledonne Communications, Grenoble, France

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

import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import org.linphone.R;

class DeviceGroupViewHolder {
    public final RelativeLayout avatarLayout;
    public final TextView participantName, sipUri;
    public final ImageView groupExpander, securityLevel;

    public DeviceGroupViewHolder(View v) {
        avatarLayout = v.findViewById(R.id.avatar_layout);
        participantName = v.findViewById(R.id.name);
        sipUri = v.findViewById(R.id.sipUri);
        groupExpander = v.findViewById(R.id.dropdown);
        securityLevel = v.findViewById(R.id.device_security_level);
    }
}
