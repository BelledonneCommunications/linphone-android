package org.linphone.views;

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
import java.io.IOException;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.ChatRoomSecurityLevel;
import org.linphone.core.tools.Log;

class ContactAvatarHolder {
    public final ImageView contactPicture;
    public final ImageView avatarMask;
    public final ImageView avatarBorder;
    public final ImageView securityLevel;
    public final TextView generatedAvatar;

    public ContactAvatarHolder(View v) {
        contactPicture = v.findViewById(R.id.contact_picture);
        avatarMask = v.findViewById(R.id.mask);
        securityLevel = v.findViewById(R.id.security_level);
        generatedAvatar = v.findViewById(R.id.generated_avatar);
        avatarBorder = v.findViewById(R.id.border);
    }

    public void init() {
        contactPicture.setVisibility(View.VISIBLE);
        generatedAvatar.setVisibility(View.VISIBLE);
        securityLevel.setVisibility(View.GONE);
        avatarBorder.setVisibility(View.GONE);
    }
}

public class ContactAvatar {

    private static String generateAvatar(String displayName) {
        String[] names = displayName.split(" ");
        StringBuilder generatedAvatarText = new StringBuilder();
        for (String name : names) {
            if (name != null && name.length() > 0) {
                generatedAvatarText.append(name.charAt(0));
            }
        }
        return generatedAvatarText.toString().toUpperCase();
    }

    private static void setSecurityLevel(ChatRoomSecurityLevel level, View v) {
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        if (holder.securityLevel != null) {
            holder.securityLevel.setVisibility(View.VISIBLE);
            switch (level) {
                case Safe:
                    holder.securityLevel.setImageResource(R.drawable.security_2_indicator);
                    break;
                case Encrypted:
                    holder.securityLevel.setImageResource(R.drawable.security_1_indicator);
                    break;
                case ClearText:
                case Unsafe:
                default:
                    holder.securityLevel.setImageResource(R.drawable.security_alert_indicator);
                    break;
            }
        } else {
            holder.securityLevel.setVisibility(View.GONE);
        }
    }

    private static void showHasLimeX3dhCapability(View v) {
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        if (holder.securityLevel != null) {
            holder.securityLevel.setVisibility(View.VISIBLE);
            holder.securityLevel.setImageResource(R.drawable.security_toogle_icon_green);
        } else {
            holder.securityLevel.setVisibility(View.GONE);
        }
    }

    public static void displayAvatar(
            String displayName, View v, boolean showBorder, int maskResource) {
        if (displayName == null || v == null) return;

        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        boolean generated_avatars =
                v.getContext().getResources().getBoolean(R.bool.generate_text_avatar);
        if (displayName.startsWith("+") || !generated_avatars) {
            // If display name is a phone number, use default avatar because generated one will be
            // +...
            holder.generatedAvatar.setVisibility(View.GONE);
        } else {
            String generatedAvatar = generateAvatar(displayName);
            if (generatedAvatar != null && generatedAvatar.length() > 0) {
                holder.generatedAvatar.setText(generatedAvatar);
                holder.generatedAvatar.setVisibility(View.VISIBLE);
            } else {
                holder.generatedAvatar.setVisibility(View.GONE);
            }
        }
        holder.securityLevel.setVisibility(View.GONE);

        if (maskResource != 0) {
            holder.avatarMask.setImageResource(maskResource);
        }
        if (showBorder) {
            holder.avatarBorder.setVisibility(View.VISIBLE);
        }
    }

    public static void displayAvatar(String displayName, View v, boolean showBorder) {
        displayAvatar(displayName, v, showBorder, 0);
    }

    public static void displayAvatar(String displayName, View v) {
        displayAvatar(displayName, v, false, 0);
    }

    public static void displayAvatar(
            String displayName, ChatRoomSecurityLevel securityLevel, View v) {
        displayAvatar(displayName, v);
        setSecurityLevel(securityLevel, v);
    }

    public static void displayAvatar(
            LinphoneContact contact, View v, boolean showBorder, int maskResource) {
        if (contact == null || v == null) return;

        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        boolean generated_avatars =
                v.getContext().getResources().getBoolean(R.bool.generate_text_avatar);

        // Kepp the generated avatar ready in case of failure while loading picture
        holder.generatedAvatar.setText(
                generateAvatar(
                        contact.getFullName() == null
                                ? contact.getFirstName() + " " + contact.getLastName()
                                : contact.getFullName()));

        holder.generatedAvatar.setVisibility(View.GONE);
        holder.contactPicture.setVisibility(View.VISIBLE);
        holder.securityLevel.setVisibility(View.GONE);

        Bitmap bm = null;
        try {
            if (contact.getThumbnailUri() != null) {
                bm =
                        MediaStore.Images.Media.getBitmap(
                                LinphoneService.instance().getContentResolver(),
                                contact.getThumbnailUri());
            }
        } catch (IOException e) {
            Log.e(e);
        }
        if (bm != null) {
            holder.contactPicture.setImageBitmap(bm);
            holder.contactPicture.setVisibility(View.VISIBLE);
            holder.generatedAvatar.setVisibility(View.GONE);
        } else if (generated_avatars) {
            holder.generatedAvatar.setText(
                    generateAvatar(
                            contact.getFullName() == null
                                    ? contact.getFirstName() + " " + contact.getLastName()
                                    : contact.getFullName()));
            holder.generatedAvatar.setVisibility(View.VISIBLE);
        }

        if (maskResource != 0) {
            holder.avatarMask.setImageResource(maskResource);
        }
        if (showBorder) {
            holder.avatarBorder.setVisibility(View.VISIBLE);
        }
    }

    public static void displayAvatar(LinphoneContact contact, View v, boolean showBorder) {
        displayAvatar(contact, v, showBorder, 0);
    }

    public static void displayAvatar(LinphoneContact contact, View v) {
        displayAvatar(contact, v, false, 0);
    }

    public static void displayAvatar(
            LinphoneContact contact, boolean hasLimeX3dhCapability, View v) {
        displayAvatar(contact, v);
        if (hasLimeX3dhCapability) {
            showHasLimeX3dhCapability(v);
        }
    }

    public static void displayAvatar(
            LinphoneContact contact, ChatRoomSecurityLevel securityLevel, View v) {
        displayAvatar(contact, v);
        setSecurityLevel(securityLevel, v);
    }

    public static void displayGroupChatAvatar(View v) {
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.contactPicture.setImageResource(R.drawable.chat_group_avatar);
        holder.generatedAvatar.setVisibility(View.GONE);
        holder.securityLevel.setVisibility(View.GONE);
        holder.avatarBorder.setVisibility(View.GONE);
    }

    public static void displayGroupChatAvatar(ChatRoomSecurityLevel level, View v) {
        displayGroupChatAvatar(v);
        setSecurityLevel(level, v);
    }
}
