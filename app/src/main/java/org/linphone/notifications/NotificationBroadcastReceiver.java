package org.linphone.notifications;

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

import android.app.Notification;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatMessageListenerStub;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final int notifId = intent.getIntExtra(Compatibility.INTENT_NOTIF_ID, 0);
        final String localyIdentity = intent.getStringExtra(Compatibility.INTENT_LOCAL_IDENTITY);

        if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)
                || intent.getAction().equals(Compatibility.INTENT_MARK_AS_READ_ACTION)) {
            String remoteSipAddr =
                    LinphoneService.instance()
                            .getNotificationManager()
                            .getSipUriForNotificationId(notifId);

            Core core = LinphoneManager.getLc();
            if (core == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't get Core instance");
                onError(context, notifId);
                return;
            }

            Address remoteAddr = core.interpretUrl(remoteSipAddr);
            if (remoteAddr == null) {
                Log.e(
                        "[Notification Broadcast Receiver] Couldn't interpret remote address "
                                + remoteSipAddr);
                onError(context, notifId);
                return;
            }

            Address localAddr = core.interpretUrl(localyIdentity);
            if (localAddr == null) {
                Log.e(
                        "[Notification Broadcast Receiver] Couldn't interpret local address "
                                + localyIdentity);
                onError(context, notifId);
                return;
            }

            ChatRoom room = core.getChatRoom(remoteAddr, localAddr);
            if (room == null) {
                Log.e(
                        "[Notification Broadcast Receiver] Couldn't find chat room for remote address "
                                + remoteSipAddr
                                + " and local address "
                                + localyIdentity);
                onError(context, notifId);
                return;
            }

            room.markAsRead();
            if (LinphoneActivity.isInstanciated()) {
                LinphoneActivity.instance()
                        .displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
            }

            if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)) {
                final String reply = getMessageText(intent).toString();
                if (reply == null) {
                    Log.e("[Notification Broadcast Receiver] Couldn't get reply text");
                    onError(context, notifId);
                    return;
                }

                ChatMessage msg = room.createMessage(reply);
                msg.send();
                msg.addListener(
                        new ChatMessageListenerStub() {
                            @Override
                            public void onMsgStateChanged(
                                    ChatMessage msg, ChatMessage.State state) {
                                if (state == ChatMessage.State.Delivered) {
                                    Notification replied =
                                            Compatibility.createRepliedNotification(context, reply);
                                    LinphoneService.instance()
                                            .getNotificationManager()
                                            .sendNotification(notifId, replied);
                                } else if (state == ChatMessage.State.NotDelivered) {
                                    Log.e(
                                            "[Notification Broadcast Receiver] Couldn't send reply, message is not delivered");
                                    onError(context, notifId);
                                }
                            }
                        });
            } else {
                LinphoneService.instance().getNotificationManager().dismissNotification(notifId);
            }
        } else if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)
                || intent.getAction().equals(Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION)) {
            String remoteAddr =
                    LinphoneService.instance()
                            .getNotificationManager()
                            .getSipUriForCallNotificationId(notifId);

            Core core = LinphoneManager.getLc();
            if (core == null) {
                Log.e("[Notification Broadcast Receiver] Couldn't get Core instance");
                return;
            }
            Call call = core.findCallFromUri(remoteAddr);
            if (call == null) {
                Log.e(
                        "[Notification Broadcast Receiver] Couldn't find call from remote address "
                                + remoteAddr);
                return;
            }

            if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)) {
                call.accept();
            } else {
                call.terminate();
            }
        }
    }

    private void onError(Context context, int notifId) {
        Notification replyError =
                Compatibility.createRepliedNotification(context, context.getString(R.string.error));
        LinphoneService.instance().getNotificationManager().sendNotification(notifId, replyError);
    }

    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(Compatibility.KEY_TEXT_REPLY);
        }
        return null;
    }
}
