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
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.linphone.LinphoneManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneShortcutManager;
import org.linphone.utils.LinphoneUtils;

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
        ArrayList<ChatRoom> notEmptyOneToOneRooms =
                LinphoneUtils.removeEmptyOneToOneChatRooms(rooms);
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

        int i = 0;
        int maxShortcuts =
                min(notEmptyOneToOneRooms.size(), shortcutManager.getMaxShortcutCountPerActivity());
        ArrayList<LinphoneContact> contacts = new ArrayList<>();
        for (ChatRoom room : notEmptyOneToOneRooms) {
            // Android can only have around 4-5 shortcuts at a time
            if (i >= maxShortcuts) break;

            Address participantAddress =
                    room.hasCapability(ChatRoomCapabilities.Basic.toInt())
                            ? room.getPeerAddress()
                            : room.getParticipants()[0].getAddress();
            LinphoneContact contact =
                    ContactsManager.getInstance().findContactFromAddress(participantAddress);

            if (contact != null && !contacts.contains(contact)) {
                String peerAddress = room.getPeerAddress().asStringUriOnly();
                ShortcutInfo shortcut = manager.createChatRoomShortcutInfo(contact, peerAddress);
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

        shortcutManager.setDynamicShortcuts(shortcuts);
    }
}
