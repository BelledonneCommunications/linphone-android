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
package org.linphone.compatibility;

import static java.lang.Math.min;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneShortcutManager;

@TargetApi(25)
class ApiTwentyFivePlus {

    public static void removeChatShortcuts(Context context) {
        ShortcutManager shortcutManager =
                (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        shortcutManager.removeAllDynamicShortcuts();
    }

    public static void createChatShortcuts(Context context) {
        if (!LinphonePreferences.instance().shortcutsCreationEnabled()) return;

        LinphoneShortcutManager manager = new LinphoneShortcutManager(context);
        ShortcutManager shortcutManager =
                (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();

        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();

        int i = 0;
        int maxShortcuts = min(rooms.length, shortcutManager.getMaxShortcutCountPerActivity());
        ArrayList<LinphoneContact> contacts = new ArrayList<>();
        for (ChatRoom room : rooms) {
            // Android can only have around 4-5 shortcuts at a time
            if (i >= maxShortcuts) break;

            Address participantAddress =
                    room.hasCapability(ChatRoomCapabilities.Basic.toInt())
                            ? room.getPeerAddress()
                            : room.getParticipants()[0].getAddress();
            LinphoneContact contact =
                    ContactsManager.getInstance().findContactFromAddress(participantAddress);

            if (contact != null && !contacts.contains(contact)) {
                if (context.getResources().getBoolean(R.bool.shortcut_to_contact)) {
                    ShortcutInfo shortcut = manager.createContactShortcutInfo(contact);
                    if (shortcut != null) {
                        Log.i(
                                "[Shortcut] Creating launcher shortcut "
                                        + shortcut.getShortLabel()
                                        + " for contact "
                                        + shortcut.getShortLabel());
                        shortcuts.add(shortcut);
                        contacts.add(contact);
                        i += 1;
                    }
                } else if (context.getResources().getBoolean(R.bool.shortcut_to_chatroom)) {
                    String peerAddress = room.getPeerAddress().asStringUriOnly();
                    ShortcutInfo shortcut =
                            manager.createChatRoomShortcutInfo(contact, peerAddress);
                    if (shortcut != null) {
                        Log.i(
                                "[Shortcut] Creating launcher shortcut "
                                        + shortcut.getShortLabel()
                                        + " for room "
                                        + shortcut.getId());
                        shortcuts.add(shortcut);
                        contacts.add(contact);
                        i += 1;
                    }
                }
            }
        }

        shortcutManager.setDynamicShortcuts(shortcuts);
        manager.destroy();
    }
}
