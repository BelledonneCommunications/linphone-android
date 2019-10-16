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

import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import org.linphone.R;

public class CallStatsViewHolder {

    public final RelativeLayout avatarLayout;
    public final TextView participantName, sipUri;

    public CallStatsViewHolder(View v) {
        avatarLayout = v.findViewById(R.id.avatar_layout);
        participantName = v.findViewById(R.id.name);
        sipUri = v.findViewById(R.id.sipUri);
    }
}
