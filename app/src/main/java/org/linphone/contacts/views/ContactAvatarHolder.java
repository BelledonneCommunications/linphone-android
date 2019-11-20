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
package org.linphone.contacts.views;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.linphone.R;

class ContactAvatarHolder {
    public final ImageView contactPicture;
    public final ImageView avatarBorder;
    public final ImageView securityLevel;
    public final TextView generatedAvatar;
    public final ImageView generatedAvatarBackground;

    public ContactAvatarHolder(View v) {
        contactPicture = v.findViewById(R.id.contact_picture);
        securityLevel = v.findViewById(R.id.security_level);
        generatedAvatar = v.findViewById(R.id.generated_avatar);
        generatedAvatarBackground = v.findViewById(R.id.generated_avatar_background);
        avatarBorder = v.findViewById(R.id.border);
    }

    public void init() {
        contactPicture.setVisibility(View.VISIBLE);
        generatedAvatar.setVisibility(View.VISIBLE);
        generatedAvatarBackground.setVisibility(View.VISIBLE);
        securityLevel.setVisibility(View.GONE);
        avatarBorder.setVisibility(View.GONE);
    }
}
