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
package org.linphone.notifications;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final int notifId = intent.getIntExtra(Compatibility.INTENT_NOTIF_ID, 0);
        final String localyIdentity = intent.getStringExtra(Compatibility.INTENT_LOCAL_IDENTITY);

        if (!LinphoneContext.isReady()) {
            Log.e("[Notification Broadcast Receiver] Context not ready, aborting...");
            return;
        }

        if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)
                || intent.getAction().equals(Compatibility.INTENT_MARK_AS_READ_ACTION)) {
            String remoteSipAddr =
                    LinphoneContext.instance()
                            .getNotificationManager()
                            .getSipUriForNotificationId(notifId);

            Core core = LinphoneManager.getCore();
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

            if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)) {
                final String reply = getMessageText(intent).toString();
                if (reply == null) {
                    Log.e("[Notification Broadcast Receiver] Couldn't get reply text");
                    onError(context, notifId);
                    return;
                }

                ChatMessage msg = room.createMessage(reply);
                msg.setUserData(notifId);
                msg.addListener(
                        LinphoneContext.instance().getNotificationManager().getMessageListener());
                msg.send();
                Log.i("[Notification Broadcast Receiver] Reply sent for notif id " + notifId);
            } else {
                LinphoneContext.instance().getNotificationManager().dismissNotification(notifId);
            }
        } else if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)
                || intent.getAction().equals(Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION)) {
            String remoteAddr =
                    LinphoneContext.instance()
                            .getNotificationManager()
                            .getSipUriForCallNotificationId(notifId);

            Core core = LinphoneManager.getCore();
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
                LinphoneManager.getCallManager().acceptCall(call);
            } else {
                call.terminate();
            }
        }
    }

    private void onError(Context context, int notifId) {
        Notification replyError =
                Compatibility.createRepliedNotification(context, context.getString(R.string.error));
        LinphoneContext.instance().getNotificationManager().sendNotification(notifId, replyError);
    }

    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(Compatibility.KEY_TEXT_REPLY);
        }
        return null;
    }
}
