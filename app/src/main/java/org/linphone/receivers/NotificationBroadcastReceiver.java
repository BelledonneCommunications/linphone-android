package org.linphone.receivers;

/*
NotificationBroadcastReceiver.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String reply = getMessageText(intent).toString();
        if (reply != null) {
            Notification replied = Compatibility.createRepliedNotification(context, reply);
            if (replied != null) {
                int notifId = intent.getIntExtra(Compatibility.INTENT_NOTIF_ID, 0);
                String remoteSipAddr = LinphoneService.instance().getSipUriForNotificationId(notifId);

                Core core = LinphoneManager.getLc();
                if (core == null) return;
                ProxyConfig proxyConfig = core.getDefaultProxyConfig();
                if (proxyConfig == null) return;
                Address localAddr = proxyConfig.getIdentityAddress();
                Address remoteAddr = core.interpretUrl(remoteSipAddr);
                if (localAddr == null || remoteAddr == null) return;
                ChatRoom room = core.findChatRoom(remoteAddr, localAddr);
                if (room == null) return;

                room.markAsRead();
                ChatMessage msg = room.createMessage(reply);
                msg.send();

                LinphoneService.instance().sendNotification(replied, notifId);
            }
        }
    }

    @TargetApi(20)
    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(Compatibility.KEY_TEXT_REPLY);
        }
        return null;
    }
}
