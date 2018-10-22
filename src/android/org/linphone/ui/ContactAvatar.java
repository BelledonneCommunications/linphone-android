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
import org.linphone.contacts.LinphoneContact;
import org.linphone.mediastream.Log;

import java.io.IOException;

public class ContactAvatar {

    private static String generateAvatar(String displayName) {
        String[] names = displayName.split(" ");
        String generatedAvatarText = "";
        for (String name : names) {
            generatedAvatarText += name.charAt(0);
        }
        return generatedAvatarText.toUpperCase();
    }


    public static void displayAvatar(String displayName, TextView generatedAvatarView) {
        generatedAvatarView.setText(generateAvatar(displayName));
        generatedAvatarView.setVisibility(View.VISIBLE);
    }

    public static void displayAvatar(LinphoneContact contact, ImageView contactPictureView, TextView generatedAvatarView) {
        Bitmap bm = null;

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
            contactPictureView.setImageBitmap(bm);
            contactPictureView.setVisibility(View.VISIBLE);
            generatedAvatarView.setVisibility(View.GONE);
        } else {
            generatedAvatarView.setText(generateAvatar(contact.getFullName()));
            generatedAvatarView.setVisibility(View.VISIBLE);
        }
    }
}
