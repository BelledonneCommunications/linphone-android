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
import org.linphone.R;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomCapabilities;
import org.linphone.utils.LinphoneShortcutManager;

@TargetApi(25)
class ApiTwentyFivePlus {

    public static void createChatShortcuts(Context context) {
        if (!context.getResources().getBoolean(R.bool.create_most_recent_chat_rooms_shortcuts))
            return;

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

        LinphoneShortcutManager manager = new LinphoneShortcutManager(context);
        int maxShortcuts =
                min(notEmptyOneToOneRooms.size(), shortcutManager.getMaxShortcutCountPerActivity());
        for (int i = 0; i < maxShortcuts; i++) {
            // Android can only have around 4-5 shortcuts at a time
            ChatRoom room = notEmptyOneToOneRooms.get(i);
            ShortcutInfo shortcut = manager.createChatRoomShortcutInfo(room);
            if (shortcut != null) {
                shortcuts.add(shortcut);
            }
        }

        shortcutManager.setDynamicShortcuts(shortcuts);
    }

    public static void updateShortcuts(Context context) {
        if (!context.getResources().getBoolean(R.bool.create_most_recent_chat_rooms_shortcuts))
            return;

        ShortcutManager shortcutManager =
                (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        LinphoneShortcutManager manager = new LinphoneShortcutManager(context);

        for (ShortcutInfo shortcutInfo : shortcutManager.getDynamicShortcuts()) {
            ShortcutInfo shortcut = manager.updateShortcutInfo(shortcutInfo);
            if (shortcut != null) {
                shortcuts.add(shortcut);
            }
        }

        shortcutManager.updateShortcuts(shortcuts);
    }
}
