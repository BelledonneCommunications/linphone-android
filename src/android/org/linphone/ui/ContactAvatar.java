package org.linphone.ui;

/*
ContactAvatar.java
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

import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.contacts.LinphoneContact;
import org.linphone.mediastream.Log;

import java.io.IOException;


class ContactAvatarHolder {
    public ImageView contactPicture, avatarMask, securityLevel;
    public TextView generatedAvatar;

    public ContactAvatarHolder(View v) {
        contactPicture = v.findViewById(R.id.contact_picture);
        avatarMask = v.findViewById(R.id.mask);
        securityLevel = v.findViewById(R.id.security_level);
        generatedAvatar = v.findViewById(R.id.generated_avatar);
    }

    public void init() {
        contactPicture.setVisibility(View.VISIBLE);
        generatedAvatar.setVisibility(View.VISIBLE);
        securityLevel.setVisibility(View.GONE);
    }
}

public class ContactAvatar {

    private static String generateAvatar(String displayName) {
        String[] names = displayName.split(" ");
        String generatedAvatarText = "";
        for (String name : names) {
            generatedAvatarText += name.charAt(0);
        }
        return generatedAvatarText.toUpperCase();
    }

    public static void setAvatarMask(View v, int resourceId) {
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.avatarMask.setImageResource(resourceId);
    }

    public static void displayAvatar(String displayName, View v) {
        if (displayName == null || v == null) return;

        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        if (displayName.startsWith("+")) {
            // If display name is a phone number, use default avatar because generated one will be +...
            holder.generatedAvatar.setVisibility(View.GONE);
            return;
        }

        holder.generatedAvatar.setText(generateAvatar(displayName));
        holder.generatedAvatar.setVisibility(View.VISIBLE);
    }

    public static void displayAvatar(LinphoneContact contact, View v) {
        if (contact == null || v == null) return;

        Bitmap bm = null;
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        if (contact.getThumbnailUri() != null && contact.getThumbnailUri().getScheme().startsWith("http")) {
            bm = LinphoneUtils.downloadBitmap(contact.getThumbnailUri());
        } else {
            if (contact.getThumbnailUri() != null) {
                try {
                    bm = MediaStore.Images.Media.getBitmap(LinphoneService.instance().getContentResolver(), contact.getThumbnailUri());
                } catch (IOException e) {
                    Log.e(e);
                }
            }
        }

        if (bm != null) {
            holder.contactPicture.setImageBitmap(bm);
            holder.contactPicture.setVisibility(View.VISIBLE);
            holder.generatedAvatar.setVisibility(View.GONE);
        } else {
            holder.generatedAvatar.setText(generateAvatar(contact.getFullName()));
            holder.generatedAvatar.setVisibility(View.VISIBLE);
        }

        if (holder.securityLevel != null) {
            //TODO when security level will be available
            /*if (contact.hasSecurity()) {
                holder.securityLevel.setVisibility(View.VISIBLE);
                switch(contact.getSecurityLevel()) {
                    case 0:
                        holder.securityLevel.setImageResource(R.drawable.security_alert_indicator);
                        break;
                    case 1:
                        holder.securityLevel.setImageResource(R.drawable.security_1_indicator.png);
                        break;
                    case 2:
                        holder.securityLevel.setImageResource(R.drawable.security_2_indicator);
                        break;
                }
            } else {
                holder.securityLevel.setVisibility(View.GONE);
            }*/
        }
    }
}
