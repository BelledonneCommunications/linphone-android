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

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import org.linphone.R;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.ChatRoomSecurityLevel;

class ContactAvatarHolder implements RequestListener<Drawable> {
    public final ImageView contactPicture;
    public final ImageView avatarMask;
    public final ImageView securityLevel;
    public final TextView generatedAvatar;

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

    @Override
    public boolean onLoadFailed(
            @Nullable GlideException e,
            Object model,
            Target<Drawable> target,
            boolean isFirstResource) {
        contactPicture.setVisibility(View.GONE);
        generatedAvatar.setVisibility(View.VISIBLE);
        return false;
    }

    @Override
    public boolean onResourceReady(
            Drawable resource,
            Object model,
            Target<Drawable> target,
            DataSource dataSource,
            boolean isFirstResource) {
        return false;
    }
}

public class ContactAvatar {

    private static String generateAvatar(String displayName) {
        String[] names = displayName.split(" ");
        StringBuilder generatedAvatarText = new StringBuilder();
        for (String name : names) {
            generatedAvatarText.append(name.charAt(0));
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

    public static void setAvatarMask(View v, int resourceId) {
        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.avatarMask.setImageResource(resourceId);
    }

    public static void displayAvatar(String displayName, View v) {
        if (displayName == null || v == null) return;

        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        if (displayName.startsWith("+")) {
            // If display name is a phone number, use default avatar because generated one will be
            // +...
            holder.generatedAvatar.setVisibility(View.GONE);
        } else {
            holder.generatedAvatar.setText(generateAvatar(displayName));
            holder.generatedAvatar.setVisibility(View.VISIBLE);
        }
        holder.securityLevel.setVisibility(View.GONE);
    }

    public static void displayAvatar(
            String displayName, ChatRoomSecurityLevel securityLevel, View v) {
        displayAvatar(displayName, v);
        setSecurityLevel(securityLevel, v);
    }

    public static void displayAvatar(LinphoneContact contact, View v) {
        if (contact == null || v == null) return;

        ContactAvatarHolder holder = new ContactAvatarHolder(v);
        holder.init();

        // Kepp the generated avatar ready in case of failure while loading picture
        holder.generatedAvatar.setText(
                generateAvatar(
                        contact.getFullName() == null
                                ? contact.getFirstName() + " " + contact.getLastName()
                                : contact.getFullName()));
        holder.generatedAvatar.setVisibility(View.GONE);

        holder.contactPicture.setVisibility(View.VISIBLE);
        Glide.with(v)
                .load(contact.getPhotoUri())
                .error(Glide.with(v).load(contact.getThumbnailUri()).listener(holder))
                .into(holder.contactPicture);
        holder.securityLevel.setVisibility(View.GONE);
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
        Glide.with(v).load(R.drawable.chat_group_avatar).into(holder.contactPicture);
        holder.generatedAvatar.setVisibility(View.GONE);
        holder.securityLevel.setVisibility(View.GONE);
    }

    public static void displayGroupChatAvatar(ChatRoomSecurityLevel level, View v) {
        displayGroupChatAvatar(v);
        setSecurityLevel(level, v);
    }
}
