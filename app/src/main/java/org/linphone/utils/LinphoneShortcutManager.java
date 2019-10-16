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
package org.linphone.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.util.ArraySet;
import java.util.Set;
import org.linphone.R;
import org.linphone.chat.ChatActivity;
import org.linphone.contacts.ContactsActivity;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.tools.Log;

@TargetApi(25)
public class LinphoneShortcutManager {
    private Context mContext;
    private Set<String> mCategories;

    public LinphoneShortcutManager(Context context) {
        mContext = context;
        mCategories = new ArraySet<>();
        mCategories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION);
    }

    public void destroy() {
        mContext = null;
    }

    public ShortcutInfo createChatRoomShortcutInfo(
            LinphoneContact contact, String chatRoomAddress) {
        if (contact == null) return null;

        Bitmap bm = null;
        if (contact.getThumbnailUri() != null) {
            bm = ImageUtils.getRoundBitmapFromUri(mContext, contact.getThumbnailUri());
        }
        Icon icon =
                bm == null
                        ? Icon.createWithResource(mContext, R.drawable.avatar)
                        : Icon.createWithBitmap(bm);

        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(mContext, ChatActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("RemoteSipUri", chatRoomAddress);

            return new ShortcutInfo.Builder(mContext, chatRoomAddress)
                    .setShortLabel(contact.getFullName())
                    .setIcon(icon)
                    .setCategories(mCategories)
                    .setIntent(intent)
                    .build();
        } catch (Exception e) {
            Log.e("[Shortcuts Manager] ShortcutInfo.Builder exception: " + e);
        }

        return null;
    }

    public ShortcutInfo createContactShortcutInfo(LinphoneContact contact) {
        if (contact == null) return null;

        Bitmap bm = null;
        if (contact.getThumbnailUri() != null) {
            bm = ImageUtils.getRoundBitmapFromUri(mContext, contact.getThumbnailUri());
        }
        Icon icon =
                bm == null
                        ? Icon.createWithResource(mContext, R.drawable.avatar)
                        : Icon.createWithBitmap(bm);

        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(mContext, ContactsActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("ContactId", contact.getContactId());

            return new ShortcutInfo.Builder(mContext, contact.getContactId())
                    .setShortLabel(contact.getFullName())
                    .setIcon(icon)
                    .setCategories(mCategories)
                    .setIntent(intent)
                    .build();
        } catch (Exception e) {
            Log.e("[Shortcuts Manager] ShortcutInfo.Builder exception: " + e);
        }

        return null;
    }
}
