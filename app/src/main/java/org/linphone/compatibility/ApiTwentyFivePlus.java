package org.linphone.compatibility;

/*
ApiTwentyFivePlus.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import static java.lang.Math.min;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.provider.MediaStore;
import android.util.ArraySet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.chat.ChatActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.Factory;
import org.linphone.core.tools.Log;
import org.linphone.utils.LinphoneUtils;

@TargetApi(25)
class ApiTwentyFivePlus {

    public static void createChatShortcuts(Context context) {
        ShortcutManager shortcutManager =
                (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();

        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();
        ArrayList<ChatRoom> notEmptyOneToOneRooms = new ArrayList<>();
        for (ChatRoom room : rooms) {
            if (room.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
                    && room.getHistorySize() > 0) {
                notEmptyOneToOneRooms.add(room);
            }
        }
        Collections.sort(
                notEmptyOneToOneRooms,
                new Comparator<ChatRoom>() {
                    public int compare(ChatRoom cr1, ChatRoom cr2) {
                        long timeDiff = cr1.getLastUpdateTime() - cr2.getLastUpdateTime();
                        if (timeDiff > 0) return -1;
                        else if (timeDiff == 0) return 0;
                        return 1;
                    }
                });

        Set<String> categories = new ArraySet<>();
        categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION);

        for (int i = 0;
                i
                        < min(
                                notEmptyOneToOneRooms.size(),
                                shortcutManager.getMaxShortcutCountPerActivity());
                i++) {
            // Android can only have 4 shortcuts at a time max
            ChatRoom room = notEmptyOneToOneRooms.get(i);
            Address peerAddress =
                    room.hasCapability(ChatRoomCapabilities.Basic.toInt())
                            ? room.getPeerAddress()
                            : room.getParticipants()[0].getAddress();
            LinphoneContact contact =
                    ContactsManager.getInstance().findContactFromAddress(peerAddress);
            String address = peerAddress.asStringUriOnly();

            Bitmap bm = null;
            try {
                if (contact != null && contact.getThumbnailUri() != null) {
                    bm =
                            MediaStore.Images.Media.getBitmap(
                                    LinphoneService.instance().getContentResolver(),
                                    contact.getThumbnailUri());
                }
            } catch (IOException e) {
                Log.e("[Shortcuts Manager] " + e);
            }
            Icon icon =
                    bm == null
                            ? Icon.createWithResource(context, R.drawable.avatar)
                            : Icon.createWithBitmap(bm);

            String name =
                    contact == null
                            ? LinphoneUtils.getAddressDisplayName(peerAddress)
                            : contact.getFullName();

            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClass(context, ChatActivity.class);
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("RemoteSipUri", room.getPeerAddress().asStringUriOnly());

                ShortcutInfo shortcut =
                        new ShortcutInfo.Builder(context, address)
                                .setShortLabel(name)
                                .setIcon(icon)
                                .setCategories(categories)
                                .setIntent(intent)
                                .build();

                shortcuts.add(shortcut);
            } catch (Exception e) {
                Log.e("[Shortcuts Manager] " + e);
            }
        }

        shortcutManager.setDynamicShortcuts(shortcuts);
    }

    public static void updateShortcuts(Context context) {
        ShortcutManager shortcutManager =
                (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();

        Set<String> categories = new ArraySet<>();
        categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION);

        for (ShortcutInfo shortcutInfo : shortcutManager.getDynamicShortcuts()) {
            String address = shortcutInfo.getId();
            Address peerAddress = Factory.instance().createAddress(address);
            LinphoneContact contact =
                    ContactsManager.getInstance().findContactFromAddress(peerAddress);

            if (contact != null) {
                Bitmap bm = null;
                try {
                    if (contact != null && contact.getThumbnailUri() != null) {
                        bm =
                                MediaStore.Images.Media.getBitmap(
                                        LinphoneService.instance().getContentResolver(),
                                        contact.getThumbnailUri());
                    }
                } catch (IOException e) {
                    Log.e("[Shortcuts Manager] " + e);
                }
                Icon icon =
                        bm == null
                                ? Icon.createWithResource(context, R.drawable.avatar)
                                : Icon.createWithBitmap(bm);

                String name =
                        contact == null
                                ? LinphoneUtils.getAddressDisplayName(peerAddress)
                                : contact.getFullName();
                ShortcutInfo shortcut =
                        new ShortcutInfo.Builder(context, address)
                                .setShortLabel(name)
                                .setIcon(icon)
                                .setCategories(categories)
                                .setIntent(shortcutInfo.getIntent())
                                .build();

                shortcuts.add(shortcut);
            }
        }

        shortcutManager.updateShortcuts(shortcuts);
    }
}
